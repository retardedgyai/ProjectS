package io.github.gyai.projects.combat.element.lightning;

public final class LightningElementEngineTest {
    private LightningElementEngineTest() {
    }

    public static void main(String[] args) {
        LightningElementEngine engine = new DisabledLightningElementEngine();
        assert !engine.enabled();
        var result = engine.evaluate(new LightningElementEngine.Input("fixture"));
        assert !result.applied();
        assert result.reason().equals("LIGHTNING_SYSTEM_DISABLED");
    }
}
