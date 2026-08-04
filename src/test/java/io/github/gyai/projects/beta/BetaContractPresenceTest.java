package io.github.gyai.projects.beta;

import io.github.gyai.projects.feature.FeatureKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public final class BetaContractPresenceTest {
    private static final List<String> REQUIRED_DOCUMENTS = List.of(
            "docs/beta/beta-full-build-master-plan.md",
            "docs/beta/dependency-graph.md",
            "docs/beta/canonical-ids.md",
            "docs/beta/acceptance-matrix.md",
            "docs/beta/wave-1-owner-decisions.md",
            "docs/beta/contracts/player-data-contract.md",
            "docs/beta/contracts/item-metadata-contract.md",
            "docs/beta/contracts/mod-contract.md",
            "docs/beta/contracts/recipe-transaction-contract.md",
            "docs/beta/contracts/mob-editor-v2-contract.md",
            "docs/beta/contracts/client-protocol-contract.md",
            "docs/beta/tracks/track-a-player-progression-persistence.md",
            "docs/beta/tracks/track-b-equipment-item-mods.md",
            "docs/beta/tracks/track-c-combat-elements-classes.md",
            "docs/beta/tracks/track-d-gathering-refining-crafting.md",
            "docs/beta/tracks/track-e-enhancement-tier-repair.md",
            "docs/beta/tracks/track-f-party-quest-rewards.md",
            "docs/beta/tracks/track-g-mob-editor-content.md",
            "docs/beta/tracks/track-h-client-ui-protocol.md");

    public static void main(String[] args) throws Exception {
        requiredContractsExistAndAreNonEmpty();
        exactlyEightTrackBriefsExist();
        allFeatureDefaultsAreFalse();
        integrationBranchRunsCiChecks();
        phaseZeroDoesNotWireGameplay();
    }

    private static void requiredContractsExistAndAreNonEmpty() throws IOException {
        for (String document : REQUIRED_DOCUMENTS) {
            Path path = Path.of(document);
            assert Files.isRegularFile(path) : document;
            assert Files.size(path) > 100 : document;
        }
    }

    private static void exactlyEightTrackBriefsExist() throws IOException {
        Path tracks = Path.of("docs/beta/tracks");
        try (var files = Files.list(tracks)) {
            assert files.filter(path -> path.getFileName().toString().startsWith("track-"))
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .count() == 8;
        }
    }

    private static void allFeatureDefaultsAreFalse() throws IOException {
        String config = read("src/main/resources/config.yml");
        for (FeatureKey key : FeatureKey.values()) {
            Pattern disabled = Pattern.compile(
                    "(?m)^  " + Pattern.quote(key.id()) + ": false\\s*$");
            assert disabled.matcher(config).find() : key.configPath();
        }
        assert config.contains("starter-sword-authoritative-enabled: false");
        assert config.contains("starter-sword-shadow-enabled: false");
        assert config.contains("warrior-spin-slash-shadow-enabled: false");
    }

    private static void integrationBranchRunsCiChecks() throws IOException {
        String workflow = read(".github/workflows/ci.yml");
        assert workflow.contains("- main");
        assert workflow.contains("- integration/beta-full-build");
        assert workflow.contains("pull_request:");
        assert workflow.contains(
                "./gradlew clean check -PskipAutoStart --no-daemon");
        assert !workflow.contains("deploy-and-start-server");
    }

    private static void phaseZeroDoesNotWireGameplay() throws IOException {
        String plugin = read(
                "src/main/java/io/github/gyai/projects/ProjectSPlugin.java");
        assert !plugin.contains("FeatureFlagService");
        assert !plugin.contains("FeatureKey");
        assert !plugin.contains("SchemaVersions");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}

