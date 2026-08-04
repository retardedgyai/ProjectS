package io.github.gyai.projects.monster.definition.v2;

import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.combat.damage.ElementProfile;
import io.github.gyai.projects.combat.element.ElementTargetCategory;
import io.github.gyai.projects.feature.FeatureFlagSnapshot;
import io.github.gyai.projects.feature.FeatureKey;
import io.github.gyai.projects.monster.content.MobDefinitionApplyResult;
import io.github.gyai.projects.monster.content.MobDefinitionRegistry;
import io.github.gyai.projects.monster.definition.v2.reference.MobReferenceResolvers;
import io.github.gyai.projects.monster.editor.MobDefinition;
import io.github.gyai.projects.monster.editor.MobDefinitionRepository;
import io.github.gyai.projects.monster.editor.MobDefinitionValidator;
import io.github.gyai.projects.monster.editor.MobEditorPermissions;
import io.github.gyai.projects.monster.editor.v2.MobEditorV2Policy;
import io.github.gyai.projects.monster.editor.v2.MobEditorV2Service;
import io.github.gyai.projects.monster.repository.MobDefinitionV2Codec;
import io.github.gyai.projects.monster.repository.MobDefinitionV2Repository;
import io.github.gyai.projects.network.MobEditorChannel;
import io.github.gyai.projects.network.MobEditorStatePacket;
import io.github.gyai.projects.schema.SchemaId;
import io.github.gyai.projects.schema.SchemaVersions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MobV2FoundationTest {
    private MobV2FoundationTest() { }

    public static void main(String[] args) throws Exception {
        schemaAndV1Compatibility();
        modelValidationAndCodec();
        repositorySafetyAndRevision();
        runtimeRegistry();
        editorFoundation();
        pureApiBoundary();
    }

    private static void schemaAndV1Compatibility() throws Exception {
        assert MobDefinition.SCHEMA_VERSION == 1;
        assert SchemaVersions.currentVersion(SchemaId.MOB_DEFINITION).orElseThrow() == 2;
        assert SchemaVersions.supportedReadVersions(SchemaId.MOB_DEFINITION).equals(Set.of(1, 2));
        assert MobEditorChannel.REQUEST_CHANNEL.equals("projects:mob_editor_req_v1");
        assert MobEditorStatePacket.CHANNEL.equals("projects:mob_editor_state_v1");
        assert MobEditorPermissions.OPEN.equals("projects.mobeditor.open");
        assert MobEditorPermissions.TEST.equals("projects.mobeditor.test");
        assert !FeatureFlagSnapshot.allDisabled().isEnabled(FeatureKey.MOB_EDITOR_V2);

        Path directory = Files.createTempDirectory("projects-mob-v1-invariance-");
        try {
            MobDefinitionValidator validator = new MobDefinitionValidator(
                    Set.of("ZOMBIE"), material -> true, item -> true, head -> true);
            MobDefinitionRepository v1 = new MobDefinitionRepository(
                    directory, validator, message -> { });
            assert v1.reload().success();
            MobDefinition definition = MobDefinition.create("legacy_fixture");
            var saved = v1.save(definition, 0);
            assert saved.success() && saved.definition().revision() == 1;
            Path file = directory.resolve("legacy_fixture.yml");
            byte[] before = Files.readAllBytes(file);
            assert v1.reload().success();
            byte[] afterV1Reload = Files.readAllBytes(file);
            assert java.util.Arrays.equals(before, afterV1Reload);

            Fixture fixture = fixture(directory, MobDefinitionV2Policy.SAFE_DEFAULTS, allResolved());
            var read = fixture.repository.read("legacy_fixture");
            assert read.status() == MobDefinitionV2Repository.ReadStatus.V1;
            assert read.schemaVersion() == 1 && read.revision() == 1;
            assert java.util.Arrays.equals(before, Files.readAllBytes(file));
            assert !new String(before, StandardCharsets.UTF_8).contains("payload-base64");
            var proposal = fixture.repository.proposeUpgrade(
                    "legacy_fixture", valid("legacy_fixture", 0));
            assert proposal != null;
            assert !fixture.repository.commitUpgrade(proposal, false).success();
            assert java.util.Arrays.equals(before, Files.readAllBytes(file));
            var upgraded = fixture.repository.commitUpgrade(proposal, true);
            assert upgraded.success() && upgraded.saved().revision() == 2;
            assert fixture.repository.read("legacy_fixture").status()
                    == MobDefinitionV2Repository.ReadStatus.V2;
            assert Files.exists(directory.resolve(".history/legacy_fixture/legacy-v1-backup.yml"));
            fixture.repository.close(); fixture.repository.close();
        } finally { deleteTree(directory); }
    }

    private static void modelValidationAndCodec() throws Exception {
        MobDefinitionV2Policy policy = MobDefinitionV2Policy.SAFE_DEFAULTS;
        MobDefinitionV2Validator validator = new MobDefinitionV2Validator(allResolved(), policy);
        MobDefinitionV2 value = valid("test_boss", 0);
        assert validator.validate(value).valid();
        assert !validator.validate(new MobDefinitionV2(2, "bad/id", value.revision(),
                value.display(), value.entityType(), value.category(), value.attributes(),
                value.attacks(), value.skills(), value.phases(), value.dropReferences(),
                value.spawnRules(), value.weaknesses(), value.fireCategory(),
                value.iceCategory(), value.rewardReferences(),
                value.participationPolicyReference(), value.extensions())).valid();
        assert value.attacks().getFirst().metadata().tags().equals(
                Set.of(AttackTag.SKILL, AttackTag.MELEE, AttackTag.PHYSICAL));
        assert value.attacks().getFirst().metadata().elements().equals(ElementProfile.EMPTY);
        assertThrows(() -> value.attacks().add(value.attacks().getFirst()));
        assertThrows(() -> value.attributes().put("bad", 1.0));

        MobDefinitionV2Codec codec = new MobDefinitionV2Codec(policy);
        byte[] first = codec.encode(value);
        byte[] second = codec.encode(value);
        assert java.util.Arrays.equals(first, second);
        assert codec.decode(first).equals(value);
        byte[] trailing = java.util.Arrays.copyOf(first, first.length + 1);
        trailing[trailing.length - 1] = 'x';
        assertThrowsIo(() -> codec.decode(trailing));

        MobDefinitionV2 duplicateAttack = copy(value, value.phases(), value.spawnRules(),
                value.weaknesses(), List.of(value.attacks().getFirst(), value.attacks().getFirst()),
                value.skills());
        assert !validator.validate(duplicateAttack).valid();
        MobDefinitionV2 cycle = copy(value, List.of(
                phase("one", true, Set.of("two")), phase("two", false, Set.of("one"))),
                value.spawnRules(), value.weaknesses(), value.attacks(), value.skills());
        assert !validator.validate(cycle).valid();
        MobDefinitionV2 unreachable = copy(value, List.of(
                phase("one", true, Set.of()), phase("two", false, Set.of())),
                value.spawnRules(), value.weaknesses(), value.attacks(), value.skills());
        assert !validator.validate(unreachable).valid();
        MobDefinitionV2 missingTarget = copy(value, List.of(phase("one", true, Set.of("missing"))),
                value.spawnRules(), value.weaknesses(), value.attacks(), value.skills());
        assert !validator.validate(missingTarget).valid();
        MobDefinitionV2 duplicateSpawn = copy(value, value.phases(),
                List.of(value.spawnRules().getFirst(), value.spawnRules().getFirst()),
                value.weaknesses(), value.attacks(), value.skills());
        assert !validator.validate(duplicateSpawn).valid();
        MobDefinitionV2 duplicateWeakness = copy(value, value.phases(), value.spawnRules(),
                List.of(new MobDefinitionV2.ElementWeakness(DamageElement.FIRE, 1.2),
                        new MobDefinitionV2.ElementWeakness(DamageElement.FIRE, 1.3)),
                value.attacks(), value.skills());
        assert !validator.validate(duplicateWeakness).valid();
        assertThrows(() -> new MobDefinitionV2.ElementWeakness(DamageElement.ICE, Double.NaN));
        assertThrows(() -> new MobDefinitionV2.AttackDefinition("bad", DamageType.PHYSICAL,
                MobDefinitionV2.AttackClassification.DIRECT, AttackMetadata.EMPTY, Double.POSITIVE_INFINITY));
        MobDefinitionV2 invalidOverride = withCategories(value,
                new MobDefinitionV2.ElementCategorySettings(
                        ElementTargetCategory.BOSS, Map.of("threshold", Double.NaN)),
                value.iceCategory());
        assert !validator.validate(invalidOverride).valid();

        MobReferenceResolvers missingSkill = new MobReferenceResolvers(
                (id, revision) -> false, id -> true, id -> true,
                id -> true, id -> true, id -> true);
        assert new MobDefinitionV2Validator(missingSkill, policy).validate(value).status()
                == MobDefinitionValidation.Status.UNRESOLVED_REFERENCE;
    }

    private static void repositorySafetyAndRevision() throws Exception {
        Path root = Files.createTempDirectory("projects-mob-v2-repo-");
        try {
            MobDefinitionV2Policy policy = new MobDefinitionV2Policy(32, 128, 64,
                    256, 1_048_576, 3);
            Fixture fixture = fixture(root, policy, allResolved());
            MobDefinitionV2 draft = valid("repository_boss", 0);
            var saved1 = fixture.repository.save(draft, 0);
            assert saved1.success() && saved1.saved().revision() == 1;
            assert fixture.repository.read(draft.mobId()).definition().equals(saved1.saved());
            assert fixture.repository.save(draft, 0).conflict();

            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<MobDefinitionV2Repository.SaveResult> a = new AtomicReference<>();
            AtomicReference<MobDefinitionV2Repository.SaveResult> b = new AtomicReference<>();
            Thread one = Thread.ofPlatform().start(() -> saveAfter(start, fixture.repository,
                    saved1.saved(), 1, a));
            Thread two = Thread.ofPlatform().start(() -> saveAfter(start, fixture.repository,
                    saved1.saved(), 1, b));
            start.countDown(); one.join(); two.join();
            assert a.get().success() ^ b.get().success();
            assert a.get().conflict() ^ b.get().conflict();
            long revision = 2;
            fixture.repository.markLastKnownGood(draft.mobId(), 1);
            for (int i = 0; i < 5; i++) {
                var next = fixture.repository.save(draft, revision++);
                assert next.success();
            }
            assert fixture.repository.history(draft.mobId()).size() <= 3;
            assert fixture.repository.history(draft.mobId()).contains(1L);
            long current = fixture.repository.read(draft.mobId()).revision();
            var rolledBack = fixture.repository.rollback(draft.mobId(), 1, current);
            assert rolledBack.success() && rolledBack.saved().revision() == current + 1;

            Path unknown = root.resolve("unknown.yml");
            Files.writeString(unknown, "schema-version: 99\nid: unknown\nrevision: 0\n");
            assert fixture.repository.read("unknown").status()
                    == MobDefinitionV2Repository.ReadStatus.UNKNOWN_VERSION;
            assert !Files.exists(unknown);
            Path invalidUtf8 = root.resolve("bad_utf.yml");
            Files.write(invalidUtf8, new byte[]{(byte) 0xc3, (byte) 0x28});
            assert fixture.repository.read("bad_utf").status()
                    == MobDefinitionV2Repository.ReadStatus.CORRUPT;
            Path oversized = root.resolve("oversized.yml");
            Files.write(oversized, new byte[(int) policy.maximumFileBytes() + 1]);
            assert fixture.repository.read("oversized").status()
                    == MobDefinitionV2Repository.ReadStatus.OVERSIZED;
            assert fixture.repository.read("../escape").status()
                    == MobDefinitionV2Repository.ReadStatus.UNSAFE_PATH;
            Path directory = root.resolve("directory.yml"); Files.createDirectory(directory);
            assert fixture.repository.read("directory").status()
                    == MobDefinitionV2Repository.ReadStatus.UNSAFE_PATH;
            try {
                Path symlink = root.resolve("link.yml");
                Files.createSymbolicLink(symlink, root.resolve("repository_boss.yml"));
                assert fixture.repository.read("link").status()
                        == MobDefinitionV2Repository.ReadStatus.UNSAFE_PATH;
            } catch (UnsupportedOperationException | IOException ignored) { }

            Path failureRoot = Files.createTempDirectory("projects-mob-v2-atomic-failure-");
            Fixture initialFailureFixture = fixture(
                    failureRoot, policy, allResolved());
            assert initialFailureFixture.repository.save(
                    valid("atomic_failure", 0), 0).success();
            initialFailureFixture.repository.close();
            byte[] beforeFailure = Files.readAllBytes(
                    failureRoot.resolve("atomic_failure.yml"));
            Fixture failureFixture = fixture(failureRoot, policy, allResolved(),
                    (target, contents) -> { throw new IOException("injected atomic failure"); });
            assert !failureFixture.repository.save(valid("atomic_failure", 0), 1).success();
            assert java.util.Arrays.equals(beforeFailure, Files.readAllBytes(
                    failureRoot.resolve("atomic_failure.yml")));
            failureFixture.repository.close(); deleteTree(failureRoot);
            fixture.repository.close(); fixture.repository.close();
        } finally { deleteTree(root); }
    }

    private static void runtimeRegistry() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);
        MobDefinitionV2Validator validator = new MobDefinitionV2Validator(
                allResolved(), MobDefinitionV2Policy.SAFE_DEFAULTS);
        MobDefinitionRegistry registry = new MobDefinitionRegistry(2, clock);
        MobDefinitionV2 first = valid("runtime_boss", 1);
        assert registry.apply(first, validator.validate(first)).status()
                == MobDefinitionApplyResult.Status.APPLIED;
        var pinned = registry.pinForSpawn(first.mobId()).orElseThrow();
        MobDefinitionV2 second = first.withRevision(2);
        assert registry.apply(second, validator.validate(second)).status()
                == MobDefinitionApplyResult.Status.APPLIED;
        assert pinned.revision() == 1;
        assert registry.current(first.mobId()).orElseThrow().revision() == 2;
        MobDefinitionValidation invalid = new MobDefinitionValidation(
                MobDefinitionValidation.Status.INVALID, List.of("bad reload"));
        assert registry.apply(second.withRevision(3), invalid).status()
                == MobDefinitionApplyResult.Status.INVALID_RETAINED;
        assert registry.current(first.mobId()).orElseThrow().revision() == 2;
        registry.close(); registry.close();
        assert registry.snapshot().isEmpty();
    }

    private static void editorFoundation() throws Exception {
        Path root = Files.createTempDirectory("projects-mob-v2-editor-");
        try {
            Fixture fixture = fixture(root, MobDefinitionV2Policy.SAFE_DEFAULTS, allResolved());
            assert fixture.repository.save(valid("editor_boss", 0), 0).success();
            AtomicBoolean enabled = new AtomicBoolean(false);
            AtomicBoolean allowed = new AtomicBoolean(true);
            AtomicInteger cleanup = new AtomicInteger();
            MobEditorV2Service.TestSpawnPort spawnPort = new MobEditorV2Service.TestSpawnPort() {
                @Override public MobEditorV2Service.TestSpawnHandle spawn(
                        MobEditorV2Service.TestSpawnRequest request) {
                    return new MobEditorV2Service.TestSpawnHandle(UUID.randomUUID(),
                            request.playerId(), request.definition().mobId(), request.revision());
                }
                @Override public void cleanup(MobEditorV2Service.TestSpawnHandle handle) {
                    cleanup.incrementAndGet();
                }
            };
            MutableClock clock = new MutableClock(Instant.parse("2026-08-05T00:00:00Z"));
            MobEditorV2Service service = new MobEditorV2Service(fixture.repository,
                    fixture.validator, (player, action) -> allowed.get(), spawnPort,
                    enabled::get, new MobEditorV2Policy(1, 1, 1, 1, 50,
                    Duration.ofSeconds(5)), clock);
            UUID player = UUID.randomUUID();
            assert service.open(player, "editor_boss").result().status()
                    == MobEditorV2Service.Status.DISABLED;
            enabled.set(true); allowed.set(false);
            assert service.open(player, "editor_boss").result().status()
                    == MobEditorV2Service.Status.PERMISSION_DENIED;
            allowed.set(true);
            var opened = service.open(player, "editor_boss");
            assert opened.result().success();
            assert service.open(player, "editor_boss").result().status()
                    == MobEditorV2Service.Status.LIMIT_REJECTED;
            assert service.list(player, 0).definitions().size() == 1;
            var preview = service.preview(player, opened.session().sessionId(), 1,
                    opened.session().draft());
            assert preview.result().success() && preview.session().sessionRevision() == 2;
            var saved = service.save(player, opened.session().sessionId(), 2);
            assert saved.result().success() && saved.session().baseRevision() == 2;
            var spawned = service.requestTestSpawn(player, opened.session().sessionId(), 3,
                    UUID.randomUUID());
            assert spawned.result().success() && service.spawnCount() == 1;
            assert service.requestTestSpawn(player, opened.session().sessionId(), 3,
                    UUID.randomUUID()).result().status() == MobEditorV2Service.Status.LIMIT_REJECTED;
            assert service.cleanupTestSpawns(player).success();
            assert cleanup.get() == 1 && service.spawnCount() == 0;
            assert service.requestTestSpawn(player, opened.session().sessionId(), 3,
                    UUID.randomUUID()).result().success();
            clock.advance(Duration.ofSeconds(6));
            assert service.validate(player, opened.session().sessionId(),
                    opened.session().draft()).result().status()
                    == MobEditorV2Service.Status.SESSION_EXPIRED;
            assert cleanup.get() == 2 && service.spawnCount() == 0;
            service.close(); service.close();
            assert service.sessionCount() == 0;
            fixture.repository.close();
        } finally { deleteTree(root); }
    }

    private static void pureApiBoundary() {
        List<Class<?>> types = List.of(MobDefinitionV2.class,
                MobDefinitionV2Validator.class, MobDefinitionV2Repository.class,
                MobDefinitionRegistry.class, MobEditorV2Service.class);
        for (Class<?> type : types) {
            for (Method method : type.getMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) continue;
                assertNoBukkit(method.getReturnType());
                for (Class<?> parameter : method.getParameterTypes()) assertNoBukkit(parameter);
            }
        }
    }

    private static MobDefinitionV2 valid(String id, long revision) {
        AttackMetadata attack = new AttackMetadata(
                Set.of(AttackTag.SKILL, AttackTag.MELEE, AttackTag.PHYSICAL),
                ElementProfile.EMPTY);
        return new MobDefinitionV2(2, id, revision,
                new MobDefinitionV2.DisplayMetadata("Test Boss", "ALWAYS",
                        Map.of("subtitle", "fixture")),
                "ZOMBIE", MobDefinitionV2.MobCategory.BOSS,
                Map.of("max-health", 100.0, "physical-defense", 10.0),
                List.of(new MobDefinitionV2.AttackDefinition("attack.basic",
                        DamageType.PHYSICAL, MobDefinitionV2.AttackClassification.DIRECT,
                        attack, 1.0)),
                List.of(new MobDefinitionV2.SkillReference("skill.fixture", 1,
                        "ON_TIMER", "cooldown.fixture", "target.nearest",
                        "attack.basic", 1.0, "condition.always")),
                List.of(phase("phase.entry", true, Set.of())),
                List.of("item.fixture"),
                List.of(new MobDefinitionV2.SpawnRule("spawn.fixture", "region.fixture",
                        "limit.fixture", "respawn.fixture", Map.of("weather", "any"),
                        "lifecycle.fixture")),
                List.of(new MobDefinitionV2.ElementWeakness(DamageElement.FIRE, 1.2)),
                new MobDefinitionV2.ElementCategorySettings(
                        ElementTargetCategory.BOSS, Map.of()),
                new MobDefinitionV2.ElementCategorySettings(
                        ElementTargetCategory.BOSS, Map.of()),
                List.of("reward.fixture"), "participation.fixture",
                Map.of("future-field", "preserved"));
    }

    private static MobDefinitionV2.PhaseDefinition phase(String id, boolean entry,
                                                          Set<String> targets) {
        return new MobDefinitionV2.PhaseDefinition(id, entry, "condition.enter",
                "condition.exit", Set.of("skill.fixture"), targets,
                List.of("cleanup.telegraph"), "invulnerability.none");
    }

    private static MobDefinitionV2 copy(MobDefinitionV2 source,
                                        List<MobDefinitionV2.PhaseDefinition> phases,
                                        List<MobDefinitionV2.SpawnRule> spawns,
                                        List<MobDefinitionV2.ElementWeakness> weaknesses,
                                        List<MobDefinitionV2.AttackDefinition> attacks,
                                        List<MobDefinitionV2.SkillReference> skills) {
        return new MobDefinitionV2(2, source.mobId(), source.revision(), source.display(),
                source.entityType(), source.category(), source.attributes(), attacks, skills,
                phases, source.dropReferences(), spawns, weaknesses, source.fireCategory(),
                source.iceCategory(), source.rewardReferences(),
                source.participationPolicyReference(), source.extensions());
    }

    private static MobDefinitionV2 withCategories(
            MobDefinitionV2 source,
            MobDefinitionV2.ElementCategorySettings fire,
            MobDefinitionV2.ElementCategorySettings ice
    ) {
        return new MobDefinitionV2(2, source.mobId(), source.revision(), source.display(),
                source.entityType(), source.category(), source.attributes(), source.attacks(),
                source.skills(), source.phases(), source.dropReferences(), source.spawnRules(),
                source.weaknesses(), fire, ice, source.rewardReferences(),
                source.participationPolicyReference(), source.extensions());
    }

    private static MobReferenceResolvers allResolved() {
        return new MobReferenceResolvers((id, revision) -> true, id -> true,
                id -> true, id -> true, id -> true, id -> true);
    }

    private static Fixture fixture(Path root, MobDefinitionV2Policy policy,
                                   MobReferenceResolvers resolvers) {
        MobDefinitionV2Validator validator = new MobDefinitionV2Validator(resolvers, policy);
        return new Fixture(validator, new MobDefinitionV2Repository(root,
                new MobDefinitionV2Codec(policy), validator, policy));
    }

    private static Fixture fixture(Path root, MobDefinitionV2Policy policy,
                                   MobReferenceResolvers resolvers,
                                   MobDefinitionV2Repository.AtomicWriter writer) {
        MobDefinitionV2Validator validator = new MobDefinitionV2Validator(resolvers, policy);
        return new Fixture(validator, new MobDefinitionV2Repository(root,
                new MobDefinitionV2Codec(policy), validator, policy, writer));
    }

    private static void saveAfter(CountDownLatch start, MobDefinitionV2Repository repository,
                                  MobDefinitionV2 draft, long expected,
                                  AtomicReference<MobDefinitionV2Repository.SaveResult> output) {
        try { start.await(); output.set(repository.save(draft, expected)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private static void assertNoBukkit(Class<?> type) {
        assert !type.getTypeName().startsWith("org.bukkit") : type;
        if (type.isArray()) assertNoBukkit(type.componentType());
    }

    private static void assertThrows(Action action) {
        try { action.run(); throw new AssertionError("exception expected"); }
        catch (RuntimeException expected) { }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static void assertThrowsIo(Action action) {
        try { action.run(); throw new AssertionError("IOException expected"); }
        catch (IOException expected) { }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
    private record Fixture(MobDefinitionV2Validator validator,
                           MobDefinitionV2Repository repository) { }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
