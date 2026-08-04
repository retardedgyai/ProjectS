package io.github.gyai.projects.mod;

public sealed interface ModSlotEntry permits ModEntry, UnknownModEntry {
    int slotIndex();
    boolean effectEnabled();
}
