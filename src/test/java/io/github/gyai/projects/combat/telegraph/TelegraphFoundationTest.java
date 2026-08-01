package io.github.gyai.projects.combat.telegraph;

import io.github.gyai.projects.network.TelegraphPacket;
import io.github.gyai.projects.monster.boss.ChargeRuntimeGuard;

import java.util.UUID;

public final class TelegraphFoundationTest {
    private TelegraphFoundationTest() {
    }

    public static void main(String[] args) {
        assert TelegraphGeometry.contains(
                TelegraphInstance.Shape.CIRCLE,
                0, 10, 0,
                0, 1,
                5, 0, 0, 0,
                3,
                3, 13, 4);
        assert !TelegraphGeometry.contains(
                TelegraphInstance.Shape.CIRCLE,
                0, 10, 0,
                0, 1,
                5, 0, 0, 0,
                3,
                3, 13.01, 4);
        assert !TelegraphGeometry.contains(
                TelegraphInstance.Shape.CIRCLE,
                0, 10, 0,
                0, 1,
                5, 0, 0, 0,
                3,
                4, 10, 4);

        assert TelegraphGeometry.contains(
                TelegraphInstance.Shape.DONUT,
                0, 0, 0,
                0, 1,
                8, 5, 0, 0,
                3,
                6, 0, 0);
        assert !TelegraphGeometry.contains(
                TelegraphInstance.Shape.DONUT,
                0, 0, 0,
                0, 1,
                8, 5, 0, 0,
                3,
                5, 0, 0);
        assert !TelegraphGeometry.contains(
                TelegraphInstance.Shape.DONUT,
                0, 0, 0,
                0, 1,
                8, 5, 0, 0,
                3,
                9, 0, 0);

        assert TelegraphGeometry.contains(
                TelegraphInstance.Shape.LINE,
                0, 0, 0,
                1, 0,
                0, 0, 4, 10,
                3,
                5, 0, 1.99);
        assert !TelegraphGeometry.contains(
                TelegraphInstance.Shape.LINE,
                0, 0, 0,
                1, 0,
                0, 0, 4, 10,
                3,
                5, 0, 2.01);
        assert !TelegraphGeometry.contains(
                TelegraphInstance.Shape.LINE,
                0, 0, 0,
                1, 0,
                0, 0, 4, 10,
                3,
                -0.1, 0, 0);

        assert TelegraphTimeline.trackingLockTick(
                0, 100, 0.40) == 60;
        assert TelegraphTimeline.isImminent(
                100, 30, 0.30);
        assert !TelegraphTimeline.isImminent(
                100, 31, 0.30);

        TelegraphRequest request = request(
                TelegraphInstance.TrackingMode.TARGET,
                UUID.randomUUID());
        TelegraphInstance instance =
                new TelegraphInstance(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        12,
                        request);
        long initialRevision = instance.revision();
        assert instance.updateCenter(1, 0, 1);
        assert instance.revision()
                == initialRevision + 1;
        assert instance.lock();
        double lockedX = instance.centerX();
        assert !instance.updateCenter(3, 0, 3);
        assert instance.centerX() == lockedX;

        TelegraphInstance cancelled =
                new TelegraphInstance(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        13,
                        request(
                                TelegraphInstance
                                        .TrackingMode.FIXED,
                                null));
        assert cancelled.cancel(
                TelegraphInstance.CancellationReason
                        .HARD_CONTROL,
                15);
        assert !cancelled.detonate();

        TelegraphPacket packet = TelegraphPacket.from(
                TelegraphOperation.CREATE,
                1,
                10,
                instance);
        assert packet.encode().length
                <= TelegraphPacket.MAX_PACKET_BYTES;

        assert !TelegraphViewerPolicy.shouldSendFallback(
                true, false);
        assert !TelegraphViewerPolicy.shouldSendFallback(
                false, true);
        assert TelegraphViewerPolicy.shouldSendFallback(
                false, false);

        ChargeRuntimeGuard stuckGuard =
                new ChargeRuntimeGuard();
        assert stuckGuard.observe(0.01, 20)
                == ChargeRuntimeGuard.StopReason.NONE;
        assert stuckGuard.observe(0.02, 20)
                == ChargeRuntimeGuard.StopReason.NONE;
        assert stuckGuard.observe(0.0, 20)
                == ChargeRuntimeGuard.StopReason.STUCK;
        assert stuckGuard.finishOnce(
                ChargeRuntimeGuard.StopReason.COLLISION);
        assert !stuckGuard.finishOnce(
                ChargeRuntimeGuard.StopReason.COLLISION);
        assert stuckGuard.finishReason()
                == ChargeRuntimeGuard.StopReason.COLLISION;
        assert !stuckGuard.particlesAllowed();

        ChargeRuntimeGuard movingGuard =
                new ChargeRuntimeGuard();
        assert movingGuard.observe(0.5, 3)
                == ChargeRuntimeGuard.StopReason.NONE;
        assert movingGuard.observe(0.5, 3)
                == ChargeRuntimeGuard.StopReason.NONE;
        assert movingGuard.observe(0.5, 3)
                == ChargeRuntimeGuard.StopReason.TIMEOUT;

        expectIllegal(() -> new TelegraphRequest(
                "bad",
                UUID.randomUUID(),
                "minecraft:overworld",
                TelegraphInstance.Shape.CIRCLE,
                TelegraphInstance.VisualTheme.DAMAGE,
                TelegraphInstance.VisualStyle.STANDARD,
                Double.NaN, 0, 0,
                0, 1,
                5, 0, 0, 0,
                0, 1, 10, 12,
                TelegraphInstance.TrackingMode.FIXED,
                null,
                3));
        expectIllegal(() -> new TelegraphRequest(
                "bad",
                UUID.randomUUID(),
                "minecraft:overworld",
                TelegraphInstance.Shape.CIRCLE,
                TelegraphInstance.VisualTheme.DAMAGE,
                TelegraphInstance.VisualStyle.STANDARD,
                0, 0, 0,
                0, 1,
                129, 0, 0, 0,
                0, 1, 10, 12,
                TelegraphInstance.TrackingMode.FIXED,
                null,
                3));
        expectIllegal(() -> new TelegraphRequest(
                "bad",
                UUID.randomUUID(),
                "minecraft:overworld",
                TelegraphInstance.Shape.CIRCLE,
                TelegraphInstance.VisualTheme.DAMAGE,
                TelegraphInstance.VisualStyle.STANDARD,
                0, 0, 0,
                0, 1,
                5, 0, 0, 0,
                10, 10, 9, 12,
                TelegraphInstance.TrackingMode.FIXED,
                null,
                3));
    }

    private static TelegraphRequest request(
            TelegraphInstance.TrackingMode mode,
            UUID target
    ) {
        return new TelegraphRequest(
                "test",
                UUID.randomUUID(),
                "minecraft:overworld",
                TelegraphInstance.Shape.CIRCLE,
                TelegraphInstance.VisualTheme.DAMAGE,
                TelegraphInstance.VisualStyle.STANDARD,
                0, 0, 0,
                0, 1,
                5, 0, 0, 0,
                0, 6, 10, 14,
                mode,
                target,
                3);
    }

    private static void expectIllegal(
            Runnable runnable
    ) {
        try {
            runnable.run();
            throw new AssertionError(
                    "Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
