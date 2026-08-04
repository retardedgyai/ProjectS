package io.github.gyai.projects.combat.damage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.logging.Logger;

public final class DamageShadowCommandRoutingTest {
    private DamageShadowCommandRoutingTest() {
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("damage-shadow-routing-");
        try {
            DamageShadowValidationController starter = controller(
                    root.resolve("starter"),
                    new DamageShadowValidationExporter());
            DamageShadowValidationController spin = controller(
                    root.resolve("spin"),
                    new DamageShadowValidationExporter(
                            "spin-slash-shadow"));
            DamageShadowCommandService starterCommands =
                    new DamageShadowCommandService(starter);
            DamageShadowCommandService spinCommands =
                    new DamageShadowCommandService(
                            spin, "spin_slash", "spin-slash");
            DamageShadowCommandRouter router =
                    new DamageShadowCommandRouter(
                            starterCommands, spinCommands);

            assert router.execute("enable").success();
            assert starter.enabled();
            assert !spin.enabled();
            assert router.execute("status", "ignored-trailing-token")
                    .success();
            assert router.execute("disable").messages().getFirst().equals(
                    "starter_sword shadow検証を無効化しました。");

            assert router.execute("starter-sword", "enable").success();
            assert starter.enabled();
            assert !spin.enabled();
            assert router.execute("starter_sword", "status").success();

            assert router.execute("spin-slash", "enable").success();
            assert starter.enabled();
            assert spin.enabled();
            assert router.execute("spin_slash", "status").success();
            spin.recordComparison(
                    DamageShadowTestFixtures.context(
                            DamageShadowTargetType.NORMAL_MONSTER, 3),
                    spinComparison());
            assert router.execute("spin-slash", "summary").messages()
                    .stream().anyMatch(value -> value.contains(
                            "comparisons=1"));
            assert starter.snapshot().comparisonCount() == 0;
            assert router.execute("spin-slash", "export").success();
            assert Files.list(root.resolve("spin")).count() == 1;
            assert router.execute("spin-slash", "reset").success();
            assert spin.snapshot().comparisonCount() == 0;
            assert router.execute("spin-slash", "disable").messages()
                    .getFirst().equals(
                            "spin_slash shadow検証を無効化しました。");
            assert !spin.enabled();
            assert starter.enabled();

            assert router.execute().success();
            assert !router.execute("unknown").success();
            assert !router.execute("unknown-subject", "status").success();
            assert !router.execute("spin-slash", "unknown").success();
            assert router.execute("spin-slash", "unknown").messages()
                    .getFirst().contains("spin-slash");

            DamageShadowCommandService.Response legacyUsage =
                    starterCommands.execute("unknown");
            assert legacyUsage.messages().getFirst().equals(
                    "使用法: /projects damage-shadow "
                            + "<status|enable|disable|reset|summary|export>");
        } finally {
            deleteRecursively(root);
        }
    }

    private static DamageShadowComparison spinComparison() {
        DamageCalculationSnapshot snapshot =
                GenericDamageShadowComparatorTest.spinSnapshot(
                        DamageType.PHYSICAL, DamageKind.DIRECT_SKILL,
                        DamageMode.PVE,
                        SpinSlashDamageShadow.EXPECTATION.exactTags(),
                        ElementProfile.EMPTY, false, 0,
                        DamageKind.DIRECT_SKILL.lifeStealEfficiency(
                                true, DamageType.PHYSICAL));
        return DamageShadowComparator.compare(
                snapshot.calculate(), snapshot,
                SpinSlashDamageShadow.EXPECTATION);
    }

    private static DamageShadowValidationController controller(
            Path directory,
            DamageShadowValidationExporter exporter
    ) {
        return new DamageShadowValidationController(
                false, new DamageShadowValidationTracker(3),
                exporter, directory,
                Clock.fixed(
                        Instant.parse("2026-08-05T03:04:05Z"),
                        ZoneOffset.UTC),
                Logger.getAnonymousLogger());
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
