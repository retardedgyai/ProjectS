package io.github.gyai.projects.item.compatibility;

import java.util.Optional;

/** Read-only projection prevents compatibility reads from mutating legacy data. */
public interface LegacyPdcSource {
    String materialIdentity();
    boolean contains(String key);
    Optional<String> stringValue(String key);
    Optional<Integer> integerValue(String key);
    Optional<Byte> byteValue(String key);
    Optional<Double> doubleValue(String key);
}
