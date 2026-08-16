package io.github.gyai.projects.transaction;

public final class QuantityMath {
    private QuantityMath() {
    }

    public static long requirePositive(long value, String label) {
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
        return value;
    }

    public static long add(long left, long right) {
        requirePositive(left, "left quantity");
        requirePositive(right, "right quantity");
        return Math.addExact(left, right);
    }

    public static long multiply(long quantity, long count) {
        requirePositive(quantity, "quantity");
        requirePositive(count, "count");
        return Math.multiplyExact(quantity, count);
    }
}
