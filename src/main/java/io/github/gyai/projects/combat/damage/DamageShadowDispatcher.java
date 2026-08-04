package io.github.gyai.projects.combat.damage;

import java.util.List;
import java.util.Objects;

/** Selects observational routes without retrying an application once started. */
public final class DamageShadowDispatcher implements DamageRequestApplier {
    private final DamageRequestApplier legacyApplier;
    private final List<DamageShadowRoute> routes;

    public DamageShadowDispatcher(
            DamageRequestApplier legacyApplier,
            List<? extends DamageShadowRoute> routes
    ) {
        this.legacyApplier = Objects.requireNonNull(
                legacyApplier, "legacyApplier");
        this.routes = routes == null ? List.of() : List.copyOf(routes);
    }

    @Override
    public DamageApplicationResult apply(DamageRequest request) {
        for (DamageShadowRoute route : routes) {
            boolean supported;
            try {
                supported = route.supports(request);
            } catch (RuntimeException exception) {
                try {
                    route.recordDispatchFailure(request, exception);
                } catch (RuntimeException ignored) {
                    // Route diagnostics cannot replace legacy combat.
                }
                continue;
            }
            if (supported) {
                // Never catch here: retrying after an application began could
                // apply legacy damage twice.
                return route.apply(request);
            }
        }
        return legacyApplier.apply(request);
    }
}
