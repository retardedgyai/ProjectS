package io.github.gyai.projects.network.beta;

@FunctionalInterface
public interface BetaCommandAuthorization {
    Decision authorize(BetaCommandContext context, BetaCommandEnvelope command);

    record Decision(boolean allowed, String reason) {
        public Decision {
            reason = reason == null ? "" : reason;
            if (reason.length() > 256) reason = reason.substring(0, 256);
        }

        public static Decision allow() {
            return new Decision(true, "");
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }
}
