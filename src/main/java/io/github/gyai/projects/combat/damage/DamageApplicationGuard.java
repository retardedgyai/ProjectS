package io.github.gyai.projects.combat.damage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Maintains per-attacker/target application context with nested LIFO semantics. */
public final class DamageApplicationGuard<T> {
    private final Map<DamageKey, Deque<T>> applying = new HashMap<>();

    public void run(
            UUID attackerId,
            UUID targetId,
            T context,
            Runnable application
    ) {
        DamageKey key = key(attackerId, targetId);
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(application, "application");
        applying.computeIfAbsent(key, ignored -> new ArrayDeque<>()).push(context);
        try {
            application.run();
        } finally {
            Deque<T> stack = applying.get(key);
            if (stack != null) {
                stack.poll();
                if (stack.isEmpty()) applying.remove(key);
            }
        }
    }

    public boolean isApplying(UUID attackerId, UUID targetId) {
        Deque<T> stack = applying.get(key(attackerId, targetId));
        return stack != null && !stack.isEmpty();
    }

    public T current(UUID attackerId, UUID targetId) {
        Deque<T> stack = applying.get(key(attackerId, targetId));
        return stack == null ? null : stack.peek();
    }

    public void clear() {
        applying.clear();
    }

    private static DamageKey key(UUID attackerId, UUID targetId) {
        return new DamageKey(
                Objects.requireNonNull(attackerId, "attackerId"),
                Objects.requireNonNull(targetId, "targetId"));
    }

    private record DamageKey(UUID attackerId, UUID targetId) {
    }
}
