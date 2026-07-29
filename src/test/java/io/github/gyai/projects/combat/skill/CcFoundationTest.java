package io.github.gyai.projects.combat.skill;

import io.github.gyai.projects.monster.MonsterRank;
import io.github.gyai.projects.network.MonsterUiMath;
import io.github.gyai.projects.network.MonsterUiPacket;
import io.github.gyai.projects.status.StatusEffectState;
import io.github.gyai.projects.status.StatusEffectType;

import java.util.List;
import java.util.UUID;

public final class CcFoundationTest {
    private CcFoundationTest() {
    }

    public static void main(String[] args) {
        UUID source = UUID.randomUUID();
        HardControlState root = HardControlState.apply(
                null, HardControlType.ROOT, source, 10, 40).state();
        HardControlState.Transition stun = HardControlState.apply(
                root, HardControlType.STUN, source, 12, 20);
        assert stun.result() == HardControlApplicationResult.REPLACED;
        assert stun.state().type() == HardControlType.STUN;

        HardControlState.Transition rejected = HardControlState.apply(
                stun.state(), HardControlType.ROOT, source, 13, 100);
        assert rejected.result()
                == HardControlApplicationResult.REJECTED_LOWER_PRIORITY;
        assert rejected.state().type() == HardControlType.STUN;

        HardControlState.Transition refreshed = HardControlState.apply(
                stun.state(), HardControlType.STUN, source, 15, 5);
        assert refreshed.result() == HardControlApplicationResult.REFRESHED;
        assert refreshed.state().endTick() == stun.state().endTick();

        HardControlState fear = HardControlState.apply(
                null, HardControlType.FEAR, source, 20, 40).state();
        HardControlState.Transition charm = HardControlState.apply(
                fear, HardControlType.CHARM, source, 21, 40);
        assert charm.result() == HardControlApplicationResult.REPLACED;
        assert charm.state().type() == HardControlType.CHARM;

        HardControlState expired = HardControlState.apply(
                null, HardControlType.ROOT, source, 0, 1).state();
        HardControlState later = HardControlState.apply(
                expired, HardControlType.FEAR, source, 2, 10).state();
        assert later.type() == HardControlType.FEAR;
        assert later.startTick() == 2;

        StatusEffectState slow = StatusEffectState.apply(
                null, StatusEffectType.SLOW, source,
                10, 20, 1.0).state();
        StatusEffectState refreshedSlow = StatusEffectState.apply(
                slow, StatusEffectType.SLOW, source,
                15, 40, 3.0).state();
        assert refreshedSlow.endTick() == 55;
        assert refreshedSlow.strength() == 3.0;
        assert refreshedSlow.type() == StatusEffectType.SLOW;
        HardControlState hardSlot = null;
        assert hardSlot == null;

        assert MonsterUiMath.threatBand(5, 10)
                == MonsterUiMath.ThreatBand.GRAY;
        assert MonsterUiMath.threatBand(6, 10)
                == MonsterUiMath.ThreatBand.WHITE;
        assert MonsterUiMath.threatBand(12, 10)
                == MonsterUiMath.ThreatBand.WHITE;
        assert MonsterUiMath.threatBand(13, 10)
                == MonsterUiMath.ThreatBand.YELLOW;
        assert MonsterUiMath.threatBand(15, 10)
                == MonsterUiMath.ThreatBand.YELLOW;
        assert MonsterUiMath.threatBand(16, 10)
                == MonsterUiMath.ThreatBand.RED;

        assertClose(100.0, MonsterUiMath.clampHealth(120.0, 100.0));
        assertClose(0.0, MonsterUiMath.clampHealth(-5.0, 100.0));
        assert "1,240 / 1,800".equals(
                MonsterUiMath.formatHealth(1240.0, 1800.0));
        assertClose(0.5, MonsterUiMath.remainingRatio(10, 20));
        assertClose(0.0, MonsterUiMath.remainingRatio(-1, 20));
        assertClose(1.0, MonsterUiMath.remainingRatio(30, 20));

        MonsterUiPacket.Entry valid = new MonsterUiPacket.Entry(
                1,
                UUID.randomUUID(),
                "test",
                "テスト",
                MonsterRank.NORMAL,
                1,
                MonsterUiMath.ThreatBand.WHITE,
                5.0,
                10.0,
                48.0,
                null,
                List.of());
        new MonsterUiPacket(
                MonsterUiPacket.Operation.UPSERT,
                1,
                1,
                List.of(valid)).encode();
        expectIllegal(() -> MonsterUiPacket.validateCount(
                MonsterUiPacket.Operation.UPSERT,
                MonsterUiPacket.MAX_MONSTERS_PER_PACKET + 1));
        expectIllegal(() -> new MonsterUiPacket(
                MonsterUiPacket.Operation.UPSERT,
                1,
                1,
                List.of(new MonsterUiPacket.Entry(
                        1,
                        UUID.randomUUID(),
                        "bad",
                        "bad",
                        MonsterRank.NORMAL,
                        1,
                        MonsterUiMath.ThreatBand.WHITE,
                        Double.NaN,
                        10.0,
                        48.0,
                        null,
                        List.of()))));
    }

    private static void expectIllegal(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void assertClose(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000_001) {
            throw new AssertionError(
                    "Expected " + expected + " but got " + actual);
        }
    }
}
