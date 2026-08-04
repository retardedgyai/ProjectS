package io.github.gyai.projects.network.beta;

public record ElementDisplaySnapshot(
        int targetNetworkId,
        double fireFractionalGauge,
        int fireStacks,
        double coldGauge,
        ColdStage coldStage,
        boolean frozen,
        long refreezeImmunityMillis
) {
    public enum ColdStage { NONE, CHILLED, DEEP_CHILL, FROZEN }

    public ElementDisplaySnapshot {
        BetaDisplayValidation.finite(fireFractionalGauge, "fireFractionalGauge");
        BetaDisplayValidation.finite(coldGauge, "coldGauge");
        if (targetNetworkId < 0 || fireFractionalGauge < 0 || fireStacks < 0
                || coldGauge < 0 || coldStage == null || refreezeImmunityMillis < 0) {
            throw new IllegalArgumentException("Invalid element display values");
        }
    }
}
