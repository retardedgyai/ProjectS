package io.github.gyai.projects.network.beta;

public record ElementDisplaySnapshot(
        int targetNetworkId,
        long stateRevision,
        double fireFractionalGauge,
        int fireStacks,
        double fireThreshold,
        double fireFractionalProgress,
        boolean fireDecayActive,
        long fireDecayStartsInMillis,
        long detonationPulseRevision,
        long snapshotExpiresAtMillis,
        double coldGauge,
        ColdStage coldStage,
        boolean frozen,
        long refreezeImmunityMillis
) {
    public enum ColdStage { NONE, CHILLED, DEEP_CHILL, FROZEN }

    public ElementDisplaySnapshot {
        BetaDisplayValidation.finite(fireFractionalGauge, "fireFractionalGauge");
        BetaDisplayValidation.finite(coldGauge, "coldGauge");
        if (targetNetworkId < 0 || stateRevision < 0 || fireFractionalGauge < 0
                || fireStacks < 0 || fireStacks > 10 || !Double.isFinite(fireThreshold)
                || fireThreshold <= 0 || !Double.isFinite(fireFractionalProgress)
                || fireFractionalProgress < 0 || fireFractionalProgress > 1
                || fireDecayStartsInMillis < 0 || detonationPulseRevision < 0
                || snapshotExpiresAtMillis < 0
                || coldGauge < 0 || coldStage == null || refreezeImmunityMillis < 0) {
            throw new IllegalArgumentException("Invalid element display values");
        }
    }

    /** Protocol-v1 compatibility constructor for the original seven-field snapshot. */
    public ElementDisplaySnapshot(int targetNetworkId, double fireFractionalGauge,
                                  int fireStacks, double coldGauge, ColdStage coldStage,
                                  boolean frozen, long refreezeImmunityMillis) {
        this(targetNetworkId, 0, fireFractionalGauge, fireStacks, 100.0,
                Math.clamp(fireFractionalGauge / 100.0, 0.0, 1.0), false,
                0, 0, 0, coldGauge, coldStage, frozen, refreezeImmunityMillis);
    }
}
