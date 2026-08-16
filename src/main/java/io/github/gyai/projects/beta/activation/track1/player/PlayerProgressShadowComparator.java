package io.github.gyai.projects.beta.activation.track1.player;

import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class PlayerProgressShadowComparator {
    public List<String> differences(PlayerProgressSnapshot legacy,
                                    PlayerProgressSnapshot staging) {
        if (legacy == null || staging == null) return List.of("snapshot");
        ArrayList<String> differences = new ArrayList<>();
        if (!legacy.playerId().equals(staging.playerId())) differences.add("playerId");
        if (legacy.level() != staging.level()) differences.add("level");
        if (legacy.experience() != staging.experience()) differences.add("experience");
        if (legacy.grantedPassivePoints() != staging.grantedPassivePoints()) differences.add("grantedPassivePoints");
        if (legacy.spentPassivePoints() != staging.spentPassivePoints()) differences.add("spentPassivePoints");
        if (!legacy.allocatedPassiveNodeIds().equals(staging.allocatedPassiveNodeIds())) differences.add("passiveNodes");
        if (!java.util.Objects.equals(legacy.selectedClassId(), staging.selectedClassId())) differences.add("selectedClassId");
        if (!legacy.professionMastery().equals(staging.professionMastery())) differences.add("professionMastery");
        if (!legacy.questStates().equals(staging.questStates())) differences.add("questStates");
        if (!legacy.unlockIds().equals(staging.unlockIds())) differences.add("unlockIds");
        if (!legacy.currencies().equals(staging.currencies())) differences.add("currencies");
        if (!legacy.persistentResources().equals(staging.persistentResources())) differences.add("persistentResources");
        if (!legacy.settings().equals(staging.settings())) differences.add("settings");
        return List.copyOf(differences);
    }
}
