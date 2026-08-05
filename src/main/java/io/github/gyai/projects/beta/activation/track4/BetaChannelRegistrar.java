package io.github.gyai.projects.beta.activation.track4;

public interface BetaChannelRegistrar {
    void register(String channel, Direction direction);
    void unregister(String channel, Direction direction);
    enum Direction { INCOMING, OUTGOING }
}
