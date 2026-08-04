package io.github.gyai.projects.monster.editor.v2;

import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2Validator;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionValidation;
import io.github.gyai.projects.monster.repository.MobDefinitionV2Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Disabled-by-default, bounded editor foundation. It is not wired into ProjectSPlugin. */
public final class MobEditorV2Service implements AutoCloseable {
    private final MobDefinitionV2Repository repository;
    private final MobDefinitionV2Validator validator;
    private final PermissionDecisionPort permissions;
    private final TestSpawnPort testSpawns;
    private final BooleanSupplier enabled;
    private final MobEditorV2Policy policy;
    private final Clock clock;
    private final Map<UUID, LinkedHashMap<UUID, Session>> sessions = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashMap<UUID, TestSpawnHandle>> activeSpawns = new LinkedHashMap<>();
    private boolean closed;

    public MobEditorV2Service(
            MobDefinitionV2Repository repository,
            MobDefinitionV2Validator validator,
            PermissionDecisionPort permissions,
            TestSpawnPort testSpawns,
            BooleanSupplier enabled,
            MobEditorV2Policy policy,
            Clock clock
    ) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.validator = java.util.Objects.requireNonNull(validator, "validator");
        this.permissions = java.util.Objects.requireNonNull(permissions, "permissions");
        this.testSpawns = java.util.Objects.requireNonNull(testSpawns, "testSpawns");
        this.enabled = java.util.Objects.requireNonNull(enabled, "enabled");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public synchronized ListResult list(UUID playerId, int page) {
        Optional<Result> denied = admission(playerId, Action.LIST);
        if (denied.isPresent()) return new ListResult(denied.get(), List.of(), page);
        List<MobDefinitionV2Repository.ReadResult> values = repository.list().stream()
                .sorted(Comparator.comparing(MobDefinitionV2Repository.ReadResult::mobId))
                .skip((long) Math.max(0, page) * policy.listPageSize())
                .limit(policy.listPageSize()).toList();
        return new ListResult(Result.ok("listed"), values, Math.max(0, page));
    }

    public synchronized OpenResult open(UUID playerId, String mobId) {
        Optional<Result> denied = admission(playerId, Action.OPEN);
        if (denied.isPresent()) return new OpenResult(denied.get(), null, null);
        cleanupExpired();
        if (sessionCount() >= policy.maximumGlobalSessions()
                || sessions.getOrDefault(playerId, new LinkedHashMap<>()).size()
                >= policy.maximumSessionsPerPlayer()) {
            return new OpenResult(Result.failure(Status.LIMIT_REJECTED, "session limit"), null, null);
        }
        var read = repository.read(mobId);
        if (read.status() == MobDefinitionV2Repository.ReadStatus.V1) {
            return new OpenResult(Result.failure(Status.LEGACY_READ_ONLY,
                    "v1 remains read-only until explicit upgrade"), null, read);
        }
        if (read.status() != MobDefinitionV2Repository.ReadStatus.V2) {
            return new OpenResult(Result.failure(Status.NOT_FOUND, read.message()), null, read);
        }
        UUID sessionId = UUID.randomUUID();
        Session session = new Session(sessionId, playerId, read.definition(),
                read.revision(), 1, clock.instant());
        sessions.computeIfAbsent(playerId, key -> new LinkedHashMap<>()).put(sessionId, session);
        return new OpenResult(Result.ok("opened"), snapshot(session), read);
    }

    public synchronized ValidationResult validate(UUID playerId, UUID sessionId,
                                                  MobDefinitionV2 draft) {
        Optional<Result> denied = admission(playerId, Action.VALIDATE);
        if (denied.isPresent()) return new ValidationResult(denied.get(), null);
        Session session = active(playerId, sessionId);
        if (session == null) return new ValidationResult(expired(), null);
        return new ValidationResult(Result.ok("validated"), validator.validate(draft));
    }

    public synchronized PreviewResult preview(UUID playerId, UUID sessionId,
                                              long expectedSessionRevision,
                                              MobDefinitionV2 draft) {
        Optional<Result> denied = admission(playerId, Action.PREVIEW);
        if (denied.isPresent()) return new PreviewResult(denied.get(), null, null);
        Session session = active(playerId, sessionId);
        if (session == null) return new PreviewResult(expired(), null, null);
        if (session.sessionRevision != expectedSessionRevision) {
            return new PreviewResult(Result.failure(Status.CONFLICT, "session revision conflict"),
                    snapshot(session), null);
        }
        MobDefinitionValidation validation = validator.validate(draft);
        if (!validation.valid()) return new PreviewResult(
                Result.failure(Status.INVALID, "validation failed"), snapshot(session), validation);
        session.draft = draft;
        session.sessionRevision++;
        touch(session);
        return new PreviewResult(Result.ok("previewed"), snapshot(session), validation);
    }

    public synchronized SaveResult save(UUID playerId, UUID sessionId,
                                        long expectedSessionRevision) {
        Optional<Result> denied = admission(playerId, Action.SAVE);
        if (denied.isPresent()) return new SaveResult(denied.get(), null, null);
        Session session = active(playerId, sessionId);
        if (session == null) return new SaveResult(expired(), null, null);
        if (session.sessionRevision != expectedSessionRevision) {
            return new SaveResult(Result.failure(Status.CONFLICT, "session revision conflict"),
                    snapshot(session), null);
        }
        var saved = repository.save(session.draft, session.baseRevision);
        if (!saved.success()) {
            Status status = saved.conflict() ? Status.CONFLICT : Status.INVALID;
            return new SaveResult(Result.failure(status, saved.message()), snapshot(session), saved);
        }
        session.draft = saved.saved(); session.baseRevision = saved.saved().revision();
        session.sessionRevision++; touch(session);
        return new SaveResult(Result.ok("saved"), snapshot(session), saved);
    }

    public synchronized SaveResult rollback(UUID playerId, UUID sessionId,
                                            long expectedSessionRevision,
                                            long selectedRevision) {
        Optional<Result> denied = admission(playerId, Action.ROLLBACK);
        if (denied.isPresent()) return new SaveResult(denied.get(), null, null);
        Session session = active(playerId, sessionId);
        if (session == null) return new SaveResult(expired(), null, null);
        if (session.sessionRevision != expectedSessionRevision) {
            return new SaveResult(Result.failure(Status.CONFLICT, "session revision conflict"), snapshot(session), null);
        }
        var saved = repository.rollback(session.draft.mobId(), selectedRevision, session.baseRevision);
        if (!saved.success()) return new SaveResult(Result.failure(
                saved.conflict() ? Status.CONFLICT : Status.INVALID, saved.message()), snapshot(session), saved);
        session.draft = saved.saved(); session.baseRevision = saved.saved().revision();
        session.sessionRevision++; touch(session);
        return new SaveResult(Result.ok("rollback committed as new revision"), snapshot(session), saved);
    }

    public synchronized TestSpawnResult requestTestSpawn(
            UUID playerId, UUID sessionId, long expectedSessionRevision, UUID requestId
    ) {
        Optional<Result> denied = admission(playerId, Action.TEST_SPAWN);
        if (denied.isPresent()) return new TestSpawnResult(denied.get(), null);
        Session session = active(playerId, sessionId);
        if (session == null) return new TestSpawnResult(expired(), null);
        if (session.sessionRevision != expectedSessionRevision) {
            return new TestSpawnResult(Result.failure(Status.CONFLICT, "session revision conflict"), null);
        }
        if (spawnCount() >= policy.maximumGlobalTestSpawns()
                || activeSpawns.getOrDefault(playerId, new LinkedHashMap<>()).size()
                >= policy.maximumTestSpawnsPerPlayer()) {
            return new TestSpawnResult(Result.failure(Status.LIMIT_REJECTED, "test-spawn limit"), null);
        }
        TestSpawnHandle handle = testSpawns.spawn(new TestSpawnRequest(
                requestId, playerId, session.draft, session.draft.revision()));
        if (handle == null || !playerId.equals(handle.playerId())) {
            return new TestSpawnResult(Result.failure(Status.INVALID, "test-spawn adapter rejected"), null);
        }
        activeSpawns.computeIfAbsent(playerId, key -> new LinkedHashMap<>())
                .put(handle.handleId(), handle);
        session.testSpawnIds.add(handle.handleId());
        return new TestSpawnResult(Result.ok("test-spawn created"), handle);
    }

    public synchronized Result cleanupTestSpawns(UUID playerId) {
        LinkedHashMap<UUID, TestSpawnHandle> removed = activeSpawns.remove(playerId);
        if (removed != null) removed.values().forEach(testSpawns::cleanup);
        return Result.ok("test-spawns cleaned");
    }

    public synchronized Result closeSession(UUID playerId, UUID sessionId) {
        LinkedHashMap<UUID, Session> values = sessions.get(playerId);
        if (values != null) {
            Session removed = values.remove(sessionId);
            if (removed != null) cleanupSessionSpawns(removed);
            if (values.isEmpty()) sessions.remove(playerId);
        }
        return Result.ok("session closed");
    }

    public synchronized int sessionCount() { return sessions.values().stream().mapToInt(Map::size).sum(); }
    public synchronized int spawnCount() { return activeSpawns.values().stream().mapToInt(Map::size).sum(); }

    public synchronized void clear() {
        new ArrayList<>(activeSpawns.keySet()).forEach(this::cleanupTestSpawns);
        sessions.clear(); activeSpawns.clear();
    }

    @Override public synchronized void close() { if (!closed) { clear(); closed = true; } }

    private Optional<Result> admission(UUID playerId, Action action) {
        if (closed) return Optional.of(Result.failure(Status.CLOSED, "closed"));
        if (!enabled.getAsBoolean()) return Optional.of(Result.failure(Status.DISABLED, "MOB_EDITOR_V2=false"));
        if (playerId == null || !permissions.allowed(playerId, action)) {
            return Optional.of(Result.failure(Status.PERMISSION_DENIED, "permission denied"));
        }
        return Optional.empty();
    }

    private Session active(UUID playerId, UUID sessionId) {
        cleanupExpired();
        Session session = sessions.getOrDefault(playerId, new LinkedHashMap<>()).get(sessionId);
        if (session != null) touch(session); return session;
    }

    private void cleanupExpired() {
        Instant cutoff = clock.instant().minus(policy.sessionExpiry());
        sessions.values().forEach(values -> values.values().removeIf(session -> {
            if (!session.touchedAt.isBefore(cutoff)) return false;
            cleanupSessionSpawns(session);
            return true;
        }));
        sessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void cleanupSessionSpawns(Session session) {
        LinkedHashMap<UUID, TestSpawnHandle> values = activeSpawns.get(session.playerId);
        if (values == null) return;
        for (UUID handleId : session.testSpawnIds) {
            TestSpawnHandle handle = values.remove(handleId);
            if (handle != null) testSpawns.cleanup(handle);
        }
        session.testSpawnIds.clear();
        if (values.isEmpty()) activeSpawns.remove(session.playerId);
    }

    private void touch(Session session) { session.touchedAt = clock.instant(); }
    private static Result expired() { return Result.failure(Status.SESSION_EXPIRED, "session missing or expired"); }
    private static SessionSnapshot snapshot(Session session) { return new SessionSnapshot(session.sessionId, session.playerId, session.draft, session.baseRevision, session.sessionRevision, session.touchedAt); }

    private static final class Session {
        private final UUID sessionId; private final UUID playerId; private MobDefinitionV2 draft;
        private final java.util.Set<UUID> testSpawnIds = new java.util.LinkedHashSet<>();
        private long baseRevision; private long sessionRevision; private Instant touchedAt;
        private Session(UUID sessionId, UUID playerId, MobDefinitionV2 draft,
                        long baseRevision, long sessionRevision, Instant touchedAt) {
            this.sessionId = sessionId; this.playerId = playerId; this.draft = draft;
            this.baseRevision = baseRevision; this.sessionRevision = sessionRevision; this.touchedAt = touchedAt;
        }
    }

    public enum Action { LIST, OPEN, VALIDATE, PREVIEW, SAVE, ROLLBACK, TEST_SPAWN }
    public enum Status { OK, DISABLED, PERMISSION_DENIED, LIMIT_REJECTED, SESSION_EXPIRED, CONFLICT, INVALID, NOT_FOUND, LEGACY_READ_ONLY, CLOSED }
    public record Result(Status status, String message) {
        public boolean success() { return status == Status.OK; }
        static Result ok(String message) { return new Result(Status.OK, message); }
        static Result failure(Status status, String message) { return new Result(status, message); }
    }
    public record SessionSnapshot(UUID sessionId, UUID playerId, MobDefinitionV2 draft,
                                  long baseRevision, long sessionRevision, Instant touchedAt) { }
    public record ListResult(Result result, List<MobDefinitionV2Repository.ReadResult> definitions, int page) { public ListResult { definitions = List.copyOf(definitions); } }
    public record OpenResult(Result result, SessionSnapshot session, MobDefinitionV2Repository.ReadResult read) { }
    public record ValidationResult(Result result, MobDefinitionValidation validation) { }
    public record PreviewResult(Result result, SessionSnapshot session, MobDefinitionValidation validation) { }
    public record SaveResult(Result result, SessionSnapshot session, MobDefinitionV2Repository.SaveResult repositoryResult) { }
    public record TestSpawnResult(Result result, TestSpawnHandle handle) { }
    public record TestSpawnRequest(UUID requestId, UUID playerId, MobDefinitionV2 definition, long revision) { }
    public record TestSpawnHandle(UUID handleId, UUID playerId, String mobId, long revision) { }
    @FunctionalInterface public interface PermissionDecisionPort { boolean allowed(UUID playerId, Action action); }
    public interface TestSpawnPort { TestSpawnHandle spawn(TestSpawnRequest request); void cleanup(TestSpawnHandle handle); }
}
