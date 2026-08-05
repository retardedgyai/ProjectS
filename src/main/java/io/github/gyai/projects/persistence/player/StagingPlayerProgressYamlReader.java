package io.github.gyai.projects.persistence.player;

import io.github.gyai.projects.player.progress.PlayerProgressRecordV1;
import io.github.gyai.projects.player.progress.PlayerProgressSnapshot;

import java.util.Optional;
import java.util.Set;

/** Public read-only facade over the package-private canonical codec. */
public final class StagingPlayerProgressYamlReader {
    private final PlayerProgressYamlCodec codec;

    public StagingPlayerProgressYamlReader(Set<String> settingWhitelist) {
        codec = new PlayerProgressYamlCodec(settingWhitelist == null ? Set.of() : settingWhitelist);
    }

    public Result decode(String yaml) {
        try {
            PlayerProgressYamlCodec.Header header = codec.inspectHeader(yaml);
            if (!PlayerProgressRecordV1.SCHEMA_ID.equals(header.schemaId())
                    || header.schemaVersion() != PlayerProgressRecordV1.SCHEMA_VERSION) {
                return new Result(Status.UNSUPPORTED, Optional.empty(), "unsupported staging schema");
            }
            return new Result(Status.LOADED,
                    Optional.of(codec.decode(yaml).snapshot()), "");
        } catch (RuntimeException | org.bukkit.configuration.InvalidConfigurationException exception) {
            return new Result(Status.MALFORMED, Optional.empty(),
                    "malformed staging record: " + exception.getClass().getSimpleName());
        }
    }

    public record Result(Status status, Optional<PlayerProgressSnapshot> snapshot, String detail) {
        public Result {
            if (status == null) throw new IllegalArgumentException("status is required");
            snapshot = snapshot == null ? Optional.empty() : snapshot;
            detail = detail == null ? "" : detail;
        }
    }

    public enum Status { LOADED, UNSUPPORTED, MALFORMED }
}
