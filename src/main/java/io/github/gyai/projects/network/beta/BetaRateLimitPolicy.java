package io.github.gyai.projects.network.beta;

public record BetaRateLimitPolicy(double requestsPerSecond, int burst) {
    public static final BetaRateLimitPolicy READ = new BetaRateLimitPolicy(10.0, 20);
    public static final BetaRateLimitPolicy PERSISTENT = new BetaRateLimitPolicy(2.0, 4);
    public static final BetaRateLimitPolicy MOB_EDITOR_SAVE_APPLY =
            new BetaRateLimitPolicy(1.0, 2);

    public BetaRateLimitPolicy {
        BetaProtocolCodec.requireFinite(requestsPerSecond, "requestsPerSecond");
        if (requestsPerSecond <= 0 || burst <= 0) {
            throw new IllegalArgumentException("Rate-limit values must be positive");
        }
    }
}
