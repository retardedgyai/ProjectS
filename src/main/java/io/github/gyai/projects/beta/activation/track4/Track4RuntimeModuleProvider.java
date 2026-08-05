package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaRuntimeModule;

import java.util.List;

public final class Track4RuntimeModuleProvider implements BetaRuntimeModuleProvider {
    private final List<BetaRuntimeModule> modules;

    public Track4RuntimeModuleProvider(PartyQuestRewardRuntimeModule partyQuestReward,
                                       MobEditorV2RuntimeModule mobEditor,
                                       ClientBetaProtocolRuntimeModule protocol) {
        modules = List.of(java.util.Objects.requireNonNull(partyQuestReward),
                java.util.Objects.requireNonNull(mobEditor),
                java.util.Objects.requireNonNull(protocol));
    }

    @Override public List<BetaRuntimeModule> modules() { return modules; }
}
