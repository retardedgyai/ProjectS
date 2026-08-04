package io.github.gyai.projects.combat.damage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.logging.Logger;

public final class DamageShadowCommandTest {
    private DamageShadowCommandTest() {
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("damage-shadow-command-");
        try {
            DamageShadowValidationController controller = controller(directory);
            DamageShadowCommandService commands =
                    new DamageShadowCommandService(controller);

            assert commands.execute("status").success();
            assert !controller.enabled();
            assert commands.execute("enable").success();
            assert controller.enabled();
            controller.recordComparison(
                    DamageShadowTestFixtures.context(
                            DamageShadowTargetType.TRAINING_DUMMY, 0),
                    DamageShadowTestFixtures.comparison(false, 0, 0));
            assert controller.snapshot().comparisonCount() == 1;
            assert commands.execute("summary").messages().stream()
                    .anyMatch(value -> value.contains("comparisons=1"));

            assert commands.execute("reset").success();
            assert controller.enabled();
            assert controller.snapshot().comparisonCount() == 0;
            assert commands.execute("export").success();
            assert Files.list(directory).count() == 1;

            assert commands.execute("disable").success();
            assert !controller.enabled();
            assert !commands.execute("unknown").success();
        } finally {
            deleteRecursively(directory);
        }
    }

    private static DamageShadowValidationController controller(Path directory) {
        return new DamageShadowValidationController(
                false,
                new DamageShadowValidationTracker(3),
                new DamageShadowValidationExporter(),
                directory,
                Clock.fixed(
                        Instant.parse("2026-08-04T03:04:05Z"),
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
