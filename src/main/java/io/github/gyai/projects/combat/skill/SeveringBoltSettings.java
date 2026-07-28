package io.github.gyai.projects.combat.skill;

import org.bukkit.configuration.ConfigurationSection;

public record SeveringBoltSettings(double telegraphDelay, double lightningHeight,
                                   double isolatedMultiplier, double controlledMultiplier,
                                   double missingHealthScaling, double warningRingDensity,
                                   double lightningParticleDensity, boolean useLightningEffect) {
    public static SeveringBoltSettings load(ConfigurationSection config) {
        String path = "skills.painter.severing-bolt.";
        return new SeveringBoltSettings(config.getDouble(path + "telegraph-delay", .7),
                config.getDouble(path + "lightning-height", 14),
                config.getDouble(path + "isolated-multiplier", 1.5),
                config.getDouble(path + "controlled-multiplier", 1.5),
                config.getDouble(path + "missing-health-scaling", .5),
                config.getDouble(path + "warning-ring-density", 1.5),
                config.getDouble(path + "lightning-particle-density", 2),
                config.getBoolean(path + "use-lightning-effect", true));
    }
}
