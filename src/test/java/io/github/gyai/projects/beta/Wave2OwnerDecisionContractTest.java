package io.github.gyai.projects.beta;

import io.github.gyai.projects.feature.FeatureKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Wave2OwnerDecisionContractTest {
    private Wave2OwnerDecisionContractTest() {
    }

    public static void main(String[] args) throws Exception {
        String decisions = read("docs/beta/wave-2-owner-decisions.md");
        assert decisions.contains("inclusive `0..30`");
        for (String outcome : List.of(
                "`SUCCESS`", "`NO_CHANGE`", "`DOWNGRADE`",
                "`BROKEN`", "`REJECTED`")) {
            assert decisions.contains(outcome) : outcome;
        }
        assert decisions.contains("Only `T1 -> T2` and `T2 -> T3`");
        assert decisions.contains("An incomplete policy is rejected");
        assert decisions.contains("Only `broken` changes to `false`");
        assert decisions.contains("Party state is temporary runtime state");
        assert decisions.contains("oldest join sequence");
        assert decisions.contains("encounter ID, player ID, participation");
        assert decisions.contains("returns the persisted terminal result");
        assert decisions.contains("Concrete reward contents and quantities");

        String trackE = read(
                "docs/beta/tracks/track-e-enhancement-tier-repair.md");
        String trackF = read(
                "docs/beta/tracks/track-f-party-quest-rewards.md");
        String matrix = read("docs/beta/acceptance-matrix.md");
        assert trackE.contains("wave-2-owner-decisions.md");
        assert trackF.contains("wave-2-owner-decisions.md");
        assert trackE.contains("WAVE_2_BASE_SHA");
        assert trackF.contains("WAVE_2_BASE_SHA");
        assert matrix.contains("Wave 2 foundation gate");
        assert matrix.contains("neither Track is automatically merged");

        String config = read("src/main/resources/config.yml");
        for (FeatureKey key : List.of(
                FeatureKey.TIER_PROMOTION,
                FeatureKey.ENHANCEMENT_V2,
                FeatureKey.REPAIR_V2,
                FeatureKey.PARTY,
                FeatureKey.QUESTS,
                FeatureKey.REWARD_V2)) {
            assert config.contains("  " + key.id() + ": false") : key;
        }

        String plugin = read(
                "src/main/java/io/github/gyai/projects/ProjectSPlugin.java");
        assert !plugin.contains("enhancement.v2");
        assert !plugin.contains("equipment.operation");
        assert !plugin.contains("io.github.gyai.projects.repair");
        assert !plugin.contains("io.github.gyai.projects.party");
        assert !plugin.contains("io.github.gyai.projects.quest");
        assert !plugin.contains("io.github.gyai.projects.reward");
        assert !plugin.contains("io.github.gyai.projects.participation");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
