package io.github.gyai.projects.beta.activation;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public record BetaActivationPolicy(
        BetaActivationAudience audience,
        BetaActivationTargetScope targetScope,
        BetaMutationPolicy mutationPolicy,
        Set<UUID> allowlistedPlayerUuids,
        Set<String> allowedWorlds,
        boolean failClosed,
        boolean requireCompatibleClient
) {
    public static final int MAXIMUM_ALLOWLISTED_PLAYERS = 512;
    public static final int MAXIMUM_ALLOWED_WORLDS = 128;
    public static final int MAXIMUM_WORLD_NAME_BYTES = 64;

    public BetaActivationPolicy {
        audience = audience == null ? BetaActivationAudience.OFF : audience;
        targetScope = targetScope == null
                ? BetaActivationTargetScope.TRAINING_DUMMY_ONLY : targetScope;
        mutationPolicy = mutationPolicy == null
                ? BetaMutationPolicy.READ_ONLY : mutationPolicy;
        allowlistedPlayerUuids = Set.copyOf(
                allowlistedPlayerUuids == null ? Set.of() : allowlistedPlayerUuids);
        allowedWorlds = Set.copyOf(allowedWorlds == null ? Set.of() : allowedWorlds);
        if (allowlistedPlayerUuids.size() > MAXIMUM_ALLOWLISTED_PLAYERS
                || allowedWorlds.size() > MAXIMUM_ALLOWED_WORLDS) {
            throw new IllegalArgumentException("Activation policy collections are oversized");
        }
        allowedWorlds.forEach(BetaActivationPolicy::validateWorld);
    }

    public static BetaActivationPolicy defaults() {
        return new BetaActivationPolicy(
                BetaActivationAudience.OFF,
                BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                BetaMutationPolicy.READ_ONLY,
                Set.of(), Set.of(), true, false);
    }

    public static BetaActivationPolicy parse(
            Map<String, ?> configured,
            Consumer<String> warningSink
    ) {
        Map<String, ?> values = configured == null ? Map.of() : configured;
        Consumer<String> warnings = warningSink == null ? ignored -> { } : warningSink;
        BetaActivationAudience audience = enumValue(values.get("audience"),
                BetaActivationAudience.class, BetaActivationAudience.OFF,
                "audience", warnings);
        BetaActivationTargetScope targetScope = enumValue(values.get("target-scope"),
                BetaActivationTargetScope.class,
                BetaActivationTargetScope.TRAINING_DUMMY_ONLY,
                "target-scope", warnings);
        BetaMutationPolicy mutation = enumValue(values.get("mutation-policy"),
                BetaMutationPolicy.class, BetaMutationPolicy.READ_ONLY,
                "mutation-policy", warnings);
        Set<UUID> players = parsePlayers(values.get("allowlisted-player-uuids"), warnings);
        Set<String> worlds = parseWorlds(values.get("allowed-worlds"), warnings);
        boolean failClosed = booleanValue(values.get("fail-closed"), true,
                "fail-closed", warnings);
        boolean compatible = booleanValue(values.get("require-compatible-client"), false,
                "require-compatible-client", warnings);
        return new BetaActivationPolicy(audience, targetScope, mutation,
                players, worlds, failClosed, compatible);
    }

    public boolean allowsAudience(UUID playerId, boolean compatibleClient) {
        if (requireCompatibleClient && !compatibleClient) return false;
        return switch (audience) {
            case OFF -> false;
            case GLOBAL -> true;
            case ALLOWLIST -> playerId != null && allowlistedPlayerUuids.contains(playerId);
        };
    }

    public boolean allowsWorld(String worldName) {
        if (worldName == null) return false;
        if (allowedWorlds.isEmpty()) return isValidWorld(worldName);
        return allowedWorlds.contains(worldName);
    }

    public boolean allowsTarget(BetaActivationTarget target) {
        if (target == null || target == BetaActivationTarget.PLAYER_PVP) return false;
        return switch (targetScope) {
            case TRAINING_DUMMY_ONLY -> target == BetaActivationTarget.TRAINING_DUMMY;
            case NON_PLAYER_PVE -> target == BetaActivationTarget.TRAINING_DUMMY
                    || target == BetaActivationTarget.NON_PLAYER_PVE;
            case ALL_PVE -> true;
        };
    }

    public boolean allowsMutation(BetaMutationPolicy required) {
        return mutationPolicy.allows(required);
    }

    public boolean restartRequired() {
        return true;
    }

    private static Set<UUID> parsePlayers(Object raw, Consumer<String> warnings) {
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        if (raw == null) return Set.of();
        if (!(raw instanceof Iterable<?> values)) {
            warn(warnings, "allowlisted-player-uuids must be a list");
            return Set.of();
        }
        for (Object value : values) {
            if (result.size() >= MAXIMUM_ALLOWLISTED_PLAYERS) {
                warn(warnings, "allowlisted-player-uuids limit reached");
                break;
            }
            try {
                result.add(UUID.fromString(String.valueOf(value)));
            } catch (IllegalArgumentException exception) {
                warn(warnings, "invalid allowlisted player UUID ignored");
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> parseWorlds(Object raw, Consumer<String> warnings) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (raw == null) return Set.of();
        if (!(raw instanceof Iterable<?> values)) {
            warn(warnings, "allowed-worlds must be a list");
            return Set.of();
        }
        for (Object value : values) {
            if (result.size() >= MAXIMUM_ALLOWED_WORLDS) {
                warn(warnings, "allowed-worlds limit reached");
                break;
            }
            String world = value == null ? "" : String.valueOf(value);
            if (!isValidWorld(world)) {
                warn(warnings, "invalid world name ignored");
                continue;
            }
            result.add(world);
        }
        return Set.copyOf(result);
    }

    private static boolean isValidWorld(String value) {
        return value != null && !value.isBlank()
                && value.getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_WORLD_NAME_BYTES
                && value.matches("[A-Za-z0-9._-]+");
    }

    private static void validateWorld(String value) {
        if (!isValidWorld(value)) throw new IllegalArgumentException("Invalid world name");
    }

    private static <E extends Enum<E>> E enumValue(
            Object raw,
            Class<E> type,
            E fallback,
            String name,
            Consumer<String> warnings
    ) {
        if (raw == null) return fallback;
        try {
            return Enum.valueOf(type, String.valueOf(raw).trim()
                    .toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            warn(warnings, "invalid " + name + "; safe default used");
            return fallback;
        }
    }

    private static boolean booleanValue(
            Object raw,
            boolean fallback,
            String name,
            Consumer<String> warnings
    ) {
        if (raw == null) return fallback;
        if (raw instanceof Boolean value) return value;
        warn(warnings, "invalid " + name + "; safe default used");
        return fallback;
    }

    private static void warn(Consumer<String> warnings, String message) {
        try {
            warnings.accept(message.length() <= 160 ? message : message.substring(0, 160));
        } catch (RuntimeException ignored) {
            // Diagnostic consumers cannot make parsing fail open.
        }
    }
}
