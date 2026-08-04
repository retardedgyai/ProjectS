package io.github.gyai.projects.player.progress;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerProgressDomainTest {
    private PlayerProgressDomainTest() {
    }

    public static void main(String[] args) {
        validAggregateAndAccounting();
        boundsAndIdentifiersAreStrict();
        collectionsAreImmutableCopies();
        settingsRequireExplicitWhitelist();
        temporaryAndBukkitStateAreAbsent();
    }

    private static void validAggregateAndAccounting() {
        UUID playerId = UUID.randomUUID();
        PlayerProgressSnapshot snapshot = fixture(playerId, 7);
        assert snapshot.playerId().equals(playerId);
        assert snapshot.level() == 45;
        assert snapshot.experience() == Long.MAX_VALUE;
        assert snapshot.availablePassivePoints() == 3;
        assert snapshot.selectedClassId().equals("warrior");
        assert snapshot.professionMastery().get("smithing") == 18L;
        assert snapshot.questStates().get("intro.quest").counters()
                .get("targets") == 2L;
        assert snapshot.currencies().get("beta.coin") == 99L;
        assert snapshot.revision() == 7;
        assert new PlayerProgressRecordV1(snapshot).schemaVersion() == 1;
    }

    private static void boundsAndIdentifiersAreStrict() {
        expectIllegal(() -> new PlayerProgressBuilder(UUID.randomUUID())
                .level(0).build());
        expectIllegal(() -> new PlayerProgressBuilder(UUID.randomUUID())
                .level(46).build());
        expectIllegal(() -> new PlayerProgressBuilder(UUID.randomUUID())
                .experience(-1).build());
        expectIllegal(() -> new PlayerProgressBuilder(UUID.randomUUID())
                .passivePoints(1, 2).build());
        expectIllegal(() -> new PlayerProgressBuilder(UUID.randomUUID())
                .passivePoints(0, 0)
                .allocatedPassiveNodeIds(Set.of("node.one")).build());
        expectIllegal(() -> new PlayerProgressBuilder(UUID.randomUUID())
                .currencies(Map.of("Bad ID", 1L)).build());
        expectIllegal(() -> new PlayerProgressBuilder(UUID.randomUUID())
                .professionMastery(Map.of("smithing", -1L)).build());
        expectIllegal(() -> new PlayerProgressRecordV1(
                "player-data", 2, fixture(UUID.randomUUID(), 1)));
    }

    private static void collectionsAreImmutableCopies() {
        HashMap<String, Long> currency = new HashMap<>();
        currency.put("beta.coin", 1L);
        HashSet<String> unlocks = new HashSet<>();
        unlocks.add("gate.one");
        PlayerProgressSnapshot snapshot = new PlayerProgressBuilder(
                UUID.randomUUID())
                .currencies(currency)
                .unlockIds(unlocks)
                .build();
        currency.put("beta.coin", 2L);
        unlocks.add("gate.two");
        assert snapshot.currencies().get("beta.coin") == 1L;
        assert !snapshot.unlockIds().contains("gate.two");
        expectUnsupported(() -> snapshot.currencies().put("x", 1L));
        expectUnsupported(() -> snapshot.unlockIds().add("x"));
    }

    private static void settingsRequireExplicitWhitelist() {
        expectIllegal(() -> new PlayerProgressBuilder(UUID.randomUUID())
                .settings(Map.of("locale", "ja_jp")));
        PlayerProgressSnapshot snapshot = new PlayerProgressBuilder(
                UUID.randomUUID(), Set.of("locale"))
                .settings(Map.of("locale", "ja_jp"))
                .build();
        assert snapshot.settings().equals(Map.of("locale", "ja_jp"));
    }

    private static void temporaryAndBukkitStateAreAbsent() {
        Set<String> forbiddenNames = Set.of(
                "cooldown", "buff", "debuff", "cc", "burn", "cold",
                "shield", "cast", "target", "ui", "shadow", "entity",
                "player", "world", "inventory", "location");
        for (Class<?> type : Set.of(
                PlayerProgressSnapshot.class,
                PlayerProgressRecordV1.class,
                QuestProgressState.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                String normalized = component.getName().toLowerCase();
                assert forbiddenNames.stream().noneMatch(normalized::contains)
                        || normalized.equals("playerid");
                assert !component.getType().getName().startsWith("org.bukkit.");
            }
        }
    }

    static PlayerProgressSnapshot fixture(UUID playerId, long revision) {
        return new PlayerProgressBuilder(playerId, Set.of("locale"))
                .level(45)
                .experience(Long.MAX_VALUE)
                .passivePoints(5, 2)
                .allocatedPassiveNodeIds(Set.of("warrior.node.one"))
                .selectedClassId("warrior")
                .professionMastery(Map.of("smithing", 18L))
                .questStates(Map.of("intro.quest", new QuestProgressState(
                        "active", Map.of("targets", 2L), Set.of("stage.one"))))
                .unlockIds(Set.of("endgame.gate"))
                .currencies(Map.of("beta.coin", 99L))
                .persistentResources(Map.of("account.token", 3L))
                .settings(Map.of("locale", "日本語"))
                .revision(revision)
                .lastSavedAt(Instant.parse("2026-08-05T01:02:03Z"))
                .build();
    }

    private static void expectIllegal(Runnable action) {
        boolean failed = false;
        try { action.run(); } catch (IllegalArgumentException expected) {
            failed = true;
        }
        assert failed;
    }

    private static void expectUnsupported(Runnable action) {
        boolean failed = false;
        try { action.run(); } catch (UnsupportedOperationException expected) {
            failed = true;
        }
        assert failed;
    }
}
