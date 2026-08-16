package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.*;
import io.github.gyai.projects.feature.FeatureKey;

import java.util.Set;

public final class PartyQuestRewardRuntimeModule extends AbstractTrack4RuntimeModule {
    private final StagingPartyRuntime party;
    private final StagingTrainingDummyQuestRuntime quest;
    private final TrainingDummyParticipationRelay participationRelay;
    private final StagingEconomyOperationPort economy;

    public PartyQuestRewardRuntimeModule(StagingPartyRuntime party,
                                         StagingTrainingDummyQuestRuntime quest) {
        this(party, quest, null, null);
    }

    public PartyQuestRewardRuntimeModule(StagingPartyRuntime party,
                                         StagingTrainingDummyQuestRuntime quest,
                                         TrainingDummyParticipationRelay participationRelay) {
        this(party, quest, participationRelay, null);
    }

    public PartyQuestRewardRuntimeModule(StagingPartyRuntime party,
                                         StagingTrainingDummyQuestRuntime quest,
                                         TrainingDummyParticipationRelay participationRelay,
                                         StagingEconomyOperationPort economy) {
        super(new BetaRuntimeModuleDescriptor(BetaRuntimeModuleId.PARTY_QUEST_REWARD,
                Set.of(BetaRuntimeModuleId.PLAYER_PERSISTENCE,
                        BetaRuntimeModuleId.GATHERING_CRAFTING),
                Set.of(FeatureKey.PARTY, FeatureKey.QUESTS, FeatureKey.REWARD_V2),
                BetaMutationPolicy.READ_ONLY, true,
                Set.of("track1-progress-port", "track3-item-delivery-port")));
        this.party = java.util.Objects.requireNonNull(party);
        this.quest = java.util.Objects.requireNonNull(quest);
        this.participationRelay = participationRelay;
        this.economy = economy;
    }

    public StagingPartyRuntime party() { return party; }
    public StagingTrainingDummyQuestRuntime quest() { return quest; }

    @Override protected BetaRuntimeModuleResult startModule(BetaRuntimeModuleContext context) {
        if (economy != null && !economy.available()) {
            return BetaRuntimeModuleResult.failure("Track 3 economy port unavailable");
        }
        if (participationRelay != null) participationRelay.start();
        return BetaRuntimeModuleResult.running();
    }

    @Override protected void stopModule() {
        if (participationRelay != null) participationRelay.close();
        quest.close(); party.close();
    }

    public boolean usesEconomyPort(StagingEconomyOperationPort value) { return economy == value; }
}
