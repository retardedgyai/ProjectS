package io.github.gyai.projects.beta.activation;

import java.time.Instant;

public record BetaRuntimeDiagnostic(
        Instant timestamp,
        BetaRuntimeModuleId moduleId,
        BetaRuntimeDiagnosticCode code,
        String detail
) {
    public BetaRuntimeDiagnostic {
        if (timestamp == null || code == null) {
            throw new IllegalArgumentException("Diagnostic timestamp and code are required");
        }
        detail = BetaRuntimeModuleResult.bounded(detail);
    }
}
