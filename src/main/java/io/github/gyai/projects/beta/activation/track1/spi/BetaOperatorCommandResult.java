package io.github.gyai.projects.beta.activation.track1.spi;

import java.util.List;

public record BetaOperatorCommandResult(boolean accepted, List<String> messages) {
    public static final int MAX_MESSAGES = 32;
    public static final int MAX_MESSAGE_LENGTH = 256;

    public BetaOperatorCommandResult {
        List<String> source = messages == null ? List.of() : messages;
        if (source.size() > MAX_MESSAGES) throw new IllegalArgumentException("too many messages");
        messages = source.stream().map(BetaOperatorCommandResult::bounded).toList();
    }

    public static BetaOperatorCommandResult denied() {
        return new BetaOperatorCommandResult(false, List.of("projects.dev is required"));
    }

    private static String bounded(String value) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return text.length() <= MAX_MESSAGE_LENGTH ? text : text.substring(0, MAX_MESSAGE_LENGTH);
    }
}
