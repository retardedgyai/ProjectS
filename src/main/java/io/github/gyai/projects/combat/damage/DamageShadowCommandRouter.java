package io.github.gyai.projects.combat.damage;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Pure backward-compatible subject routing for damage-shadow commands. */
public final class DamageShadowCommandRouter {
    private static final Set<String> STARTER_SUBJECTS = Set.of(
            "starter-sword", "starter_sword");
    private static final Set<String> SPIN_SUBJECTS = Set.of(
            "spin-slash", "spin_slash");
    private static final Set<String> ACTIONS = Set.of(
            "status", "enable", "disable", "reset", "summary", "export");
    private static final String USAGE =
            "使用法: /projects damage-shadow "
                    + "[starter-sword|spin-slash] "
                    + "<status|enable|disable|reset|summary|export>";

    private final DamageShadowCommandService starterSword;
    private final DamageShadowCommandService spinSlash;

    public DamageShadowCommandRouter(
            DamageShadowCommandService starterSword,
            DamageShadowCommandService spinSlash
    ) {
        this.starterSword = Objects.requireNonNull(
                starterSword, "starterSword");
        this.spinSlash = spinSlash;
    }

    public DamageShadowCommandService.Response execute(String... arguments) {
        if (arguments == null || arguments.length == 0) {
            return starterSword.execute("status");
        }
        String first = normalize(arguments[0]);
        if (ACTIONS.contains(first)) {
            // Preserve the original action-first form, including its former
            // behavior of ignoring trailing tokens.
            return starterSword.execute(arguments[0]);
        }
        if (STARTER_SUBJECTS.contains(first)) {
            return starterSword.execute(
                    arguments.length >= 2 ? arguments[1] : "status");
        }
        if (SPIN_SUBJECTS.contains(first)) {
            return spinSlash == null
                    ? unknown()
                    : spinSlash.execute(
                            arguments.length >= 2 ? arguments[1] : "status");
        }
        if (arguments.length == 1) {
            return starterSword.execute(arguments[0]);
        }
        return unknown();
    }

    private static DamageShadowCommandService.Response unknown() {
        return new DamageShadowCommandService.Response(false, List.of(USAGE));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
