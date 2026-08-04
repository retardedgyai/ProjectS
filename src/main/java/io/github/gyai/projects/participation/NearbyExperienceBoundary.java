package io.github.gyai.projects.participation;

import java.util.List;
import java.util.Objects;

public final class NearbyExperienceBoundary {
    public List<ExperienceShareProposal> propose(
            long sourceExperience,
            List<ExperienceParticipationInput> inputs,
            NearbyExperiencePolicy policy
    ) {
        if (sourceExperience < 0) throw new IllegalArgumentException("Negative source experience");
        Objects.requireNonNull(policy, "policy");
        return List.copyOf(inputs).stream().map(input -> {
            NearbyExperiencePolicy.Decision decision = Objects.requireNonNull(
                    policy.evaluate(input, sourceExperience), "XP policy decision");
            if (!decision.eligible()) return null;
            double proposed = sourceExperience * decision.shareRate();
            if (!Double.isFinite(proposed)) {
                throw new IllegalArgumentException("Proposed experience is not finite");
            }
            return new ExperienceShareProposal(input.encounterId(), input.playerId(),
                    sourceExperience, proposed, decision.policyRevision());
        }).filter(Objects::nonNull).toList();
    }
}
