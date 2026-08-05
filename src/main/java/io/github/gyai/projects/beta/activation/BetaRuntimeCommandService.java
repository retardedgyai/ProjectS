package io.github.gyai.projects.beta.activation;

import io.github.gyai.projects.feature.FeatureKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BetaRuntimeCommandService {
    public static final int MAXIMUM_RESPONSE_LINES = 32;
    private final BetaRuntime runtime;
    private final BetaOperatorContributorRegistry contributors;

    public BetaRuntimeCommandService(BetaRuntime runtime) {
        this(runtime, BetaOperatorContributorRegistry.disabledDefaults(runtime));
    }

    public BetaRuntimeCommandService(
            BetaRuntime runtime,
            BetaOperatorContributorRegistry contributors
    ) {
        this.runtime = java.util.Objects.requireNonNull(runtime, "runtime");
        this.contributors = java.util.Objects.requireNonNull(contributors, "contributors");
    }

    public Response execute(List<String> arguments, boolean hasDevPermission) {
        return execute(arguments, new BetaOperatorContributorRegistry.Context(
                null, "", hasDevPermission, false));
    }

    public Response execute(
            List<String> arguments,
            BetaOperatorContributorRegistry.Context context
    ) {
        boolean hasDevPermission = context != null && context.projectsDev();
        if (!hasDevPermission) return new Response(false, List.of("permission denied"));
        List<String> values = arguments == null ? List.of() : List.copyOf(arguments);
        if (!values.isEmpty() && "staging".equalsIgnoreCase(values.get(0))) {
            BetaOperatorContributorRegistry.Result result =
                    contributors.execute(values, runtime.healthSnapshot(), context);
            return new Response(result.success(), result.messages());
        }
        return execute(values.isEmpty() ? "status" : values.get(0), true);
    }

    public Response execute(String action, boolean hasDevPermission) {
        if (!hasDevPermission) return new Response(false, List.of("permission denied"));
        String normalized = action == null ? "status" : action.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "status" -> status();
            case "modules" -> modules();
            case "policy" -> policy();
            case "health" -> health();
            default -> new Response(false, List.of(
                    "usage: /projects beta <status|modules|policy|health>",
                    "runtime mutation commands are unavailable; restart required"));
        };
    }

    private Response status() {
        BetaRuntimeHealthSnapshot health = runtime.healthSnapshot();
        return new Response(true, List.of(
                "Beta runtime status=" + health.status(),
                "startedModules=" + health.moduleStates().values().stream()
                        .filter(value -> value == BetaRuntimeModuleState.RUNNING).count(),
                "startCount=" + health.startCount() + " stopCount=" + health.stopCount(),
                "restartRequired=" + health.restartRequired()));
    }

    private Response policy() {
        BetaActivationPolicy policy = runtime.policy();
        return new Response(true, List.of(
                "audience=" + policy.audience(),
                "targetScope=" + policy.targetScope(),
                "mutationPolicy=" + policy.mutationPolicy(),
                "requireCompatibleClient=" + policy.requireCompatibleClient(),
                "allowlistedPlayers=" + policy.allowlistedPlayerUuids().size()
                        + " allowedWorlds=" + policy.allowedWorlds().size(),
                "restartRequired=" + policy.restartRequired()));
    }

    private Response modules() {
        BetaRuntimeHealthSnapshot health = runtime.healthSnapshot();
        ArrayList<String> lines = new ArrayList<>();
        for (BetaRuntimeModuleId id : BetaRuntimeModuleId.values()) {
            String line = id + "=" + health.moduleStates().get(id);
            if (health.blockedDependencies().containsKey(id)) {
                line += " blockedBy=" + health.blockedDependencies().get(id);
            }
            lines.add(line);
        }
        for (FeatureKey key : FeatureKey.values()) {
            lines.add("feature." + key.id() + "="
                    + runtime.featureFlags().isEnabled(key));
        }
        return bounded(true, lines);
    }

    private Response health() {
        BetaRuntimeHealthSnapshot health = runtime.healthSnapshot();
        ArrayList<String> lines = new ArrayList<>();
        lines.add("status=" + health.status() + " timestamp=" + health.timestamp());
        lines.add("lastFailure=" + (health.lastFailure().isBlank() ? "none" : health.lastFailure()));
        int start = Math.max(0, health.diagnostics().size() - 16);
        for (BetaRuntimeDiagnostic diagnostic : health.diagnostics().subList(
                start, health.diagnostics().size())) {
            lines.add(diagnostic.code() + " module="
                    + (diagnostic.moduleId() == null ? "runtime" : diagnostic.moduleId())
                    + " detail=" + diagnostic.detail());
        }
        lines.addAll(contributors.healthDetails());
        return bounded(true, lines);
    }

    private static Response bounded(boolean success, List<String> source) {
        ArrayList<String> lines = new ArrayList<>();
        for (String line : source) {
            if (lines.size() >= MAXIMUM_RESPONSE_LINES) break;
            lines.add(BetaRuntimeModuleResult.bounded(line));
        }
        return new Response(success, lines);
    }

    public record Response(boolean success, List<String> messages) {
        public Response {
            messages = List.copyOf(messages == null ? List.of() : messages);
            if (messages.size() > MAXIMUM_RESPONSE_LINES) {
                throw new IllegalArgumentException("Response is oversized");
            }
        }
    }
}
