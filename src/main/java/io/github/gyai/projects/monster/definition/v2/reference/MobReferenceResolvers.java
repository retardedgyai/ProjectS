package io.github.gyai.projects.monster.definition.v2.reference;

/** Ports owned by the referenced Tracks; Track G never opens their repositories. */
public record MobReferenceResolvers(
        SkillReferenceResolver skills,
        ItemReferenceResolver items,
        ResourceReferenceResolver resources,
        RewardReferenceResolver rewards,
        ParticipationPolicyResolver participationPolicies,
        RegionReferenceResolver regions
) {
    public MobReferenceResolvers {
        if (skills == null || items == null || resources == null || rewards == null
                || participationPolicies == null || regions == null) {
            throw new NullPointerException("reference resolvers");
        }
    }

    @FunctionalInterface public interface SkillReferenceResolver { boolean resolves(String id, long revision); }
    @FunctionalInterface public interface ItemReferenceResolver { boolean resolves(String id); }
    @FunctionalInterface public interface ResourceReferenceResolver { boolean resolves(String id); }
    @FunctionalInterface public interface RewardReferenceResolver { boolean resolves(String id); }
    @FunctionalInterface public interface ParticipationPolicyResolver { boolean resolves(String id); }
    @FunctionalInterface public interface RegionReferenceResolver { boolean resolves(String id); }
}
