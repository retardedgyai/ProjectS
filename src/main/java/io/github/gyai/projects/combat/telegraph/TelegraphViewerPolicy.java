package io.github.gyai.projects.combat.telegraph;

public final class TelegraphViewerPolicy {
    private TelegraphViewerPolicy() {
    }

    public static boolean shouldSendFallback(
            boolean channelListening,
            boolean helloConfirmed
    ) {
        return !channelListening && !helloConfirmed;
    }
}
