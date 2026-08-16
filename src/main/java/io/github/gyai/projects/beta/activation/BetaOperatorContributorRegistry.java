package io.github.gyai.projects.beta.activation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** One bounded command registry for all staging contributors. */
public final class BetaOperatorContributorRegistry {
    public static final String DISABLED_MESSAGE =
            "Beta module is disabled. Restart with approved staging policy.";
    public static final int MAXIMUM_CONTRIBUTORS = 16;
    private final Map<String, Entry> entries;
    private final Supplier<List<String>> healthDetails;

    public BetaOperatorContributorRegistry(List<Entry> source) {
        this(source, List::of);
    }

    public BetaOperatorContributorRegistry(
            List<Entry> source,
            Supplier<List<String>> healthDetails
    ) {
        if (source == null || source.size() > MAXIMUM_CONTRIBUTORS) {
            throw new IllegalArgumentException("invalid contributor registry");
        }
        LinkedHashMap<String, Entry> copy = new LinkedHashMap<>();
        for (Entry entry : source) {
            if (entry == null || copy.put(entry.subject(), entry) != null) {
                throw new IllegalArgumentException("duplicate contributor subject");
            }
        }
        entries = Map.copyOf(copy);
        this.healthDetails = healthDetails == null ? List::of : healthDetails;
    }

    public static BetaOperatorContributorRegistry disabledDefaults(BetaRuntime runtime) {
        List<Entry> values = new ArrayList<>();
        values.add(disabled("player", BetaRuntimeModuleId.PLAYER_PERSISTENCE, runtime));
        values.add(disabled("equipment", BetaRuntimeModuleId.EQUIPMENT, runtime));
        values.add(disabled("element", BetaRuntimeModuleId.COMBAT_ELEMENTS, runtime));
        values.add(disabled("economy", BetaRuntimeModuleId.GATHERING_CRAFTING, runtime));
        values.add(disabled("party", BetaRuntimeModuleId.PARTY_QUEST_REWARD, runtime));
        values.add(disabled("quest", BetaRuntimeModuleId.PARTY_QUEST_REWARD, runtime));
        values.add(disabled("reward", BetaRuntimeModuleId.PARTY_QUEST_REWARD, runtime));
        values.add(disabled("mob", BetaRuntimeModuleId.MOB_EDITOR_V2, runtime));
        return new BetaOperatorContributorRegistry(values);
    }

    private static Entry disabled(String subject, BetaRuntimeModuleId moduleId, BetaRuntime runtime) {
        return new Entry(subject, moduleId,
                (context, arguments) -> new Result(false, List.of(DISABLED_MESSAGE)));
    }

    public Result execute(List<String> arguments, BetaRuntimeHealthSnapshot health) {
        return execute(arguments, health, new Context(null, "", true, false));
    }

    public Result execute(
            List<String> arguments,
            BetaRuntimeHealthSnapshot health,
            Context context
    ) {
        if (arguments == null || arguments.size() < 2
                || !"staging".equalsIgnoreCase(arguments.get(0))) {
            return new Result(false, List.of("usage: /projects beta staging <player|equipment|element|economy|party|quest|reward|mob> ..."));
        }
        Entry entry = entries.get(arguments.get(1).toLowerCase(Locale.ROOT));
        if (entry == null) return new Result(false, List.of("unknown staging subject"));
        if (health.moduleStates().get(entry.moduleId()) != BetaRuntimeModuleState.RUNNING
                && !allowsReadOnlyEconomyUi(entry, arguments, context)) {
            return new Result(false, List.of(DISABLED_MESSAGE));
        }
        try {
            Result result = entry.contributor().execute(context,
                    List.copyOf(arguments.subList(2, arguments.size())));
            return bounded(result);
        } catch (RuntimeException failure) {
            return new Result(false, List.of("Beta staging contributor failed safely."));
        }
    }

    public int size() { return entries.size(); }

    public List<String> healthDetails() {
        List<String> source;
        try {
            source = healthDetails.get();
        } catch (RuntimeException failure) {
            return List.of("handshakeDiagnostics=unavailable");
        }
        ArrayList<String> result = new ArrayList<>();
        if (source != null) for (String line : source) {
            if (result.size() >= 8) break;
            result.add(BetaRuntimeModuleResult.bounded(line));
        }
        return List.copyOf(result);
    }

    private static Result bounded(Result source) {
        ArrayList<String> lines = new ArrayList<>();
        for (String line : source.messages()) {
            if (lines.size() == BetaRuntimeCommandService.MAXIMUM_RESPONSE_LINES) break;
            lines.add(BetaRuntimeModuleResult.bounded(line));
        }
        return new Result(source.success(), lines);
    }

    /**
     * The staging workbench is a read-only diagnostic surface until its own
     * presenter admits writes. Keep this exception deliberately narrower than
     * a general economy bypass: it needs the exact UI path and projects.dev.
     */
    private static boolean allowsReadOnlyEconomyUi(
            Entry entry, List<String> arguments, Context context
    ) {
        return context != null && context.projectsDev()
                && "economy".equals(entry.subject())
                && arguments.size() == 3
                && "ui".equalsIgnoreCase(arguments.get(2));
    }

    public record Entry(String subject, BetaRuntimeModuleId moduleId, Contributor contributor) {
        public Entry {
            if (subject == null || !subject.matches("[a-z][a-z0-9-]{0,31}")
                    || moduleId == null || contributor == null) {
                throw new IllegalArgumentException("invalid operator contributor");
            }
        }
    }

    @FunctionalInterface public interface Contributor {
        Result execute(Context context, List<String> arguments);
    }

    public record Context(UUID actorId, String worldName,
                          boolean projectsDev, boolean compatibleClient) {
        public Context {
            worldName = worldName == null ? "" : worldName;
            if (worldName.length() > 64) throw new IllegalArgumentException("world name oversized");
        }
    }

    public record Result(boolean success, List<String> messages) {
        public Result {
            messages = List.copyOf(messages == null ? List.of() : messages);
            if (messages.size() > BetaRuntimeCommandService.MAXIMUM_RESPONSE_LINES) {
                throw new IllegalArgumentException("operator response is oversized");
            }
        }
    }
}
