package io.github.gyai.projects.manager;

public final class BalanceMathTest {
    private BalanceMathTest() {
    }

    public static void main(String[] args) {
        assertClose(16.8, BalanceMath.attackPower(10, 2, 1.4));
        assertClose(0.0, BalanceMath.attackPower(10, -20, 1.4));
        assertClose(0.39, BalanceMath.attackSpeed(.10, .24, .05));
        assertClose(33.0, BalanceMath.skillDamage(12, 15, 1.4));
        assertClose(15.0, BalanceMath.typedWeaponAttackPower(10, 15));
        assertClose(0.0, BalanceMath.typedWeaponAttackPower(0, 15));

        assert BalanceMath.finiteInRange(0, 0, 10_000);
        assert BalanceMath.finiteInRange(10_000, 0, 10_000);
        assert !BalanceMath.finiteInRange(-1, 0, 10_000);
        assert !BalanceMath.finiteInRange(Double.NaN, 0, 10_000);
        assert !BalanceMath.finiteInRange(
                Double.POSITIVE_INFINITY, 0, 10_000);
        assert BalanceMath.revisionMatches(7, 7);
        assert !BalanceMath.revisionMatches(6, 7);
    }

    private static void assertClose(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000_001) {
            throw new AssertionError(
                    "Expected " + expected + " but got " + actual);
        }
    }
}
