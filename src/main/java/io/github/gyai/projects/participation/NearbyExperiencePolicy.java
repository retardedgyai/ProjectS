package io.github.gyai.projects.participation;

/** Production distance, world, eligibility and share values remain external policy input. */
@FunctionalInterface
public interface NearbyExperiencePolicy {
    Decision evaluate(ExperienceParticipationInput input, long sourceExperience);

    record Decision(boolean eligible, double shareRate, String policyRevision) {
        public Decision {
            if (!Double.isFinite(shareRate) || shareRate < 0.0 || shareRate > 1.0) {
                throw new IllegalArgumentException("Share rate must be finite in 0..1");
            }
            if (!eligible && shareRate != 0.0) {
                throw new IllegalArgumentException("Ineligible player cannot receive share");
            }
            if (policyRevision == null || policyRevision.isBlank()) {
                throw new IllegalArgumentException("Policy revision is required");
            }
        }
    }
}
