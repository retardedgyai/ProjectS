package io.github.gyai.projects.combat.skill;

import org.bukkit.configuration.ConfigurationSection;

public record PainterSpellSettings(boolean enabled, int manaCost, double cooldown, double range, double radius,
                                   double baseDamage, double scaling, double duration, double tickInterval,
                                   int slowStrength, double ccDuration, double projectileSpeed, double particleDensity) {
    public static PainterSpellSettings load(ConfigurationSection root, PainterSpell spell) {
        String path = "skills.painter." + spell.configKey + ".";
        return new PainterSpellSettings(root.getBoolean(path + "enabled", true),
                root.getInt(path + "mana-cost", spell.defaultMana), root.getDouble(path + "cooldown", spell.defaultCooldown),
                root.getDouble(path + "range", spell.defaultRange), root.getDouble(path + "radius", spell.defaultRadius),
                root.getDouble(path + "base-damage", spell.defaultDamage), root.getDouble(path + "scaling", 0),
                root.getDouble(path + "duration", 5), root.getDouble(path + "tick-interval", 1),
                root.getInt(path + "slow-strength", 1), root.getDouble(path + "cc-duration", 1.5),
                root.getDouble(path + "projectile-speed", 1.5), root.getDouble(path + "particle-density", 1));
    }
}
