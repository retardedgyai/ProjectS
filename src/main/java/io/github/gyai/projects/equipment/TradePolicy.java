package io.github.gyai.projects.equipment;

public record TradePolicy(boolean directTradeAllowed, boolean marketAllowed,
                          boolean dismantleAllowed) {
    public static final TradePolicy DENY_ALL = new TradePolicy(false, false, false);
}
