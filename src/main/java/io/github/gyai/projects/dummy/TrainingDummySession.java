package io.github.gyai.projects.dummy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

public class TrainingDummySession {
    private static final long RECENT_WINDOW_MILLIS = 3_000L;

    private final UUID playerId;
    private final UUID dummyId;
    private final long startedAtMillis;
    private final Deque<DamageHit> recentHits = new ArrayDeque<>();
    private long lastHitAtMillis;
    private double totalDamage;
    private double maximumHit;
    private int hitCount;
    private final Map<String, Double> skillDamage = new LinkedHashMap<>();

    public TrainingDummySession(UUID playerId, UUID dummyId, long nowMillis) {
        this.playerId = playerId;
        this.dummyId = dummyId;
        this.startedAtMillis = nowMillis;
        this.lastHitAtMillis = nowMillis;
    }

    public void recordHit(double damage, long nowMillis) {
        recordHit(damage, nowMillis, null);
    }

    public void recordHit(double damage, long nowMillis, String skillId) {
        lastHitAtMillis = nowMillis;
        totalDamage += damage;
        maximumHit = Math.max(maximumHit, damage);
        hitCount++;
        if (skillId != null) skillDamage.merge(skillId, damage, Double::sum);
        recentHits.addLast(new DamageHit(nowMillis, damage));
        discardOldHits(nowMillis);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getDummyId() {
        return dummyId;
    }

    public long getLastHitAtMillis() {
        return lastHitAtMillis;
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    public double getMaximumHit() {
        return maximumHit;
    }

    public int getHitCount() {
        return hitCount;
    }

    public Map<String, Double> getSkillDamage() { return Map.copyOf(skillDamage); }

    public double getElapsedSeconds(long nowMillis) {
        return Math.max((nowMillis - startedAtMillis) / 1_000.0, 0.001);
    }

    public double getAverageDps(long nowMillis) {
        return totalDamage / getElapsedSeconds(nowMillis);
    }

    public double getRecentDps(long nowMillis) {
        discardOldHits(nowMillis);
        return recentHits.stream().mapToDouble(DamageHit::damage).sum() / 3.0;
    }

    private void discardOldHits(long nowMillis) {
        while (!recentHits.isEmpty() && nowMillis - recentHits.peekFirst().timeMillis() > RECENT_WINDOW_MILLIS) {
            recentHits.removeFirst();
        }
    }

    private record DamageHit(long timeMillis, double damage) {
    }
}
