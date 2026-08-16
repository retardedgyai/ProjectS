package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.monster.definition.v2.MobDefinitionV2;
import io.github.gyai.projects.monster.editor.v2.MobEditorV2Service;

import java.nio.file.Path;
import java.util.UUID;

/** Admission and path boundary around the existing Mob Editor v2 foundation. */
public final class StagingMobEditorRuntime implements AutoCloseable {
    public static final Path RELATIVE_STAGING_ROOT = Path.of("plugins", "ProjectS",
            "beta-staging", "mobs");

    private final MobEditorV2Service service;
    private final BetaActivationPolicy policy;
    private final Path repositoryRoot;
    private boolean closed;

    public StagingMobEditorRuntime(MobEditorV2Service service,
                                   BetaActivationPolicy policy,
                                   Path repositoryRoot) {
        this.service = java.util.Objects.requireNonNull(service);
        this.policy = java.util.Objects.requireNonNull(policy);
        this.repositoryRoot = java.util.Objects.requireNonNull(repositoryRoot)
                .toAbsolutePath().normalize();
        Path suffix = RELATIVE_STAGING_ROOT.normalize();
        if (!this.repositoryRoot.endsWith(suffix)) {
            throw new IllegalArgumentException("Mob repository must be beta-staging/mobs");
        }
    }

    public MobEditorV2Service.ListResult list(Context context, int page) {
        if (!readAllowed(context)) return deniedList(page);
        return service.list(context.playerId(), page);
    }

    public MobEditorV2Service.OpenResult open(Context context, String mobId) {
        if (!readAllowed(context)) return new MobEditorV2Service.OpenResult(denied(), null, null);
        return service.open(context.playerId(), mobId);
    }

    public MobEditorV2Service.ValidationResult validate(Context context, UUID sessionId,
                                                        MobDefinitionV2 draft) {
        if (!readAllowed(context)) return new MobEditorV2Service.ValidationResult(denied(), null);
        return service.validate(context.playerId(), sessionId, draft);
    }

    public MobEditorV2Service.PreviewResult preview(Context context, UUID sessionId,
                                                    long revision, MobDefinitionV2 draft) {
        if (!readAllowed(context)) return new MobEditorV2Service.PreviewResult(denied(), null, null);
        return service.preview(context.playerId(), sessionId, revision, draft);
    }

    public MobEditorV2Service.SaveResult save(Context context, UUID sessionId, long revision) {
        if (!writeAllowed(context)) return new MobEditorV2Service.SaveResult(denied(), null, null);
        return service.save(context.playerId(), sessionId, revision);
    }

    public MobEditorV2Service.SaveResult rollback(Context context, UUID sessionId,
                                                   long revision, long selectedRevision) {
        if (!writeAllowed(context)) return new MobEditorV2Service.SaveResult(denied(), null, null);
        return service.rollback(context.playerId(), sessionId, revision, selectedRevision);
    }

    public MobEditorV2Service.TestSpawnResult testSpawn(Context context, UUID sessionId,
                                                        long revision, UUID requestId) {
        if (!writeAllowed(context)) return new MobEditorV2Service.TestSpawnResult(denied(), null);
        return service.requestTestSpawn(context.playerId(), sessionId, revision, requestId);
    }

    public Path repositoryRoot() { return repositoryRoot; }

    @Override public synchronized void close() {
        if (!closed) { closed = true; service.close(); }
    }

    private boolean readAllowed(Context context) {
        return !closed && context != null && StagingAdmission.read(policy,
                context.playerId(), context.worldName(), context.projectsDev(),
                context.compatibleClient()).allowed();
    }

    private boolean writeAllowed(Context context) {
        return !closed && context != null && StagingAdmission.stagingWrite(policy,
                context.playerId(), context.worldName(), context.projectsDev(),
                context.compatibleClient()).allowed();
    }

    private static MobEditorV2Service.Result denied() {
        return new MobEditorV2Service.Result(MobEditorV2Service.Status.PERMISSION_DENIED,
                "activation staging admission denied");
    }

    private static MobEditorV2Service.ListResult deniedList(int page) {
        return new MobEditorV2Service.ListResult(denied(), java.util.List.of(), Math.max(0, page));
    }

    public record Context(UUID playerId, String worldName,
                          boolean projectsDev, boolean compatibleClient) { }
}
