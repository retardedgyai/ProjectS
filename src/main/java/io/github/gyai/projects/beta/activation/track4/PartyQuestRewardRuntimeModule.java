package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.*;
import io.github.gyai.projects.feature.FeatureKey;

import java.util.Set;

public final class PartyQuestRewardRuntimeModule extends AbstractTrack4RuntimeModule {
    private final StagingPartyRuntime party;
    private final StagingTrainingDummyQuestRuntime quest;

    public PartyQuestRewardRuntimeModule(StagingPartyRuntime party,
                                         StagingTrainingDummyQuestRuntime quest) {
        super(new BetaRuntimeModuleDescriptor(BetaRuntimeModuleId.PARTY_QUEST_REWARD,
                Set.of(BetaRuntimeModuleId.PLAYER_PERSISTENCE,
                        BetaRuntimeModuleId.GATHERING_CRAFTING),
                Set.of(FeatureKey.PARTY, FeatureKey.QUESTS, FeatureKey.REWARD_V2),
                BetaMutationPolicy.READ_ONLY, true,
                Set.of("track1-progress-port", "track3-item-delivery-port")));
        this.party = java.util.Objects.requireNonNull(party);
        this.quest = java.util.Objects.requireNonNull(quest);
    }

    public StagingPartyRuntime party() { return party; }
    public StagingTrainingDummyQuestRuntime quest() { return quest; }

    @Override protected BetaRuntimeModuleResult startModule(BetaRuntimeModuleContext context) {
        return BetaRuntimeModuleResult.running();
    }

    @Override protected void stopModule() { quest.close(); party.close(); }
}
