package io.github.gyai.projects.beta.activation;

public enum BetaMutationPolicy {
    READ_ONLY,
    STAGING_WRITE,
    PRODUCTION_WRITE;

    public boolean allows(BetaMutationPolicy required) {
        return required != null && ordinal() >= required.ordinal();
    }
}
