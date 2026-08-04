package io.github.gyai.projects.combat.damage;

public enum StarterSwordRouteDecision {
    NEW_AUTHORITATIVE(true),
    LEGACY_DISABLED(false),
    LEGACY_UNSUPPORTED_ITEM(false),
    LEGACY_UNSUPPORTED_KIND(false),
    LEGACY_UNSUPPORTED_TYPE(false),
    LEGACY_UNSUPPORTED_MODE(false),
    LEGACY_METADATA(false),
    LEGACY_CRITICAL(false),
    LEGACY_SHIELD(false),
    LEGACY_SPECIAL_STATE(false),
    LEGACY_ROUTE_FAILURE(false),
    LEGACY_INVALID_SNAPSHOT(false),
    LEGACY_CALCULATION_FAILURE(false),
    LEGACY_INVALID_RESULT(false);

    private final boolean authoritative;

    StarterSwordRouteDecision(boolean authoritative) {
        this.authoritative = authoritative;
    }

    public boolean authoritative() {
        return authoritative;
    }
}
