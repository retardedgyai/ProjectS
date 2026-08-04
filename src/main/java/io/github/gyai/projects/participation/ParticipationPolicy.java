package io.github.gyai.projects.participation;

@FunctionalInterface
public interface ParticipationPolicy {
    Decision evaluate(ParticipationEvent event);

    record Decision(boolean eligible, double creditedContribution, String reason) {
        public Decision {
            if (!Double.isFinite(creditedContribution) || creditedContribution < 0.0) {
                throw new IllegalArgumentException("Credit must be finite and non-negative");
            }
            reason = reason == null ? "" : reason;
            if (!eligible && creditedContribution != 0.0) {
                throw new IllegalArgumentException("Rejected event cannot receive credit");
            }
        }

        public static Decision credit(double value) { return new Decision(true, value, ""); }
        public static Decision reject(String reason) { return new Decision(false, 0.0, reason); }
    }
}
