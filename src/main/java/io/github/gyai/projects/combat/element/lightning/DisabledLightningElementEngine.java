package io.github.gyai.projects.combat.element.lightning;

import java.util.Objects;

/** Explicit disabled implementation until lightning design is approved. */
public final class DisabledLightningElementEngine implements LightningElementEngine {
    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Result evaluate(Input input) {
        Objects.requireNonNull(input, "input");
        return new Result(false, "LIGHTNING_SYSTEM_DISABLED");
    }
}
