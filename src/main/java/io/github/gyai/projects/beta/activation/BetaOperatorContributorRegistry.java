package io.github.gyai.projects.beta.activation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One bounded command registry for all staging contributors. */
public final class BetaOperatorContributorRegistry {
    public static final String DISABLED_MESSAGE =
            "Beta module is disabled. Restart with approved staging policy.";
    public static final int MAXIMUM_CONTRIBUTORS = 16;
    private final Map<String, Entry> entries;

    public BetaOperatorContributorRegistry(List<Entry> source) {
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
        return new Entry(subject, moduleId, arguments -> new Result(false, List.of(DISABLED_MESSAGE)));
    }

    public Result execute(List<String> arguments, BetaRuntimeHealthSnapshot health) {
        if (arguments == null || arguments.size() < 2
                || !"staging".equalsIgnoreCase(arguments.get(0))) {
            return new Result(false, List.of("usage: /projects beta staging <player|equipment|element|economy|party|quest|reward|mob> ..."));
        }
        Entry entry = entries.get(arguments.get(1).toLowerCase(Locale.ROOT));
        if (entry == null) return new Result(false, List.of("unknown staging subject"));
        if (health.moduleStates().get(entry.moduleId()) != BetaRuntimeModuleState.RUNNING) {
            return new Result(false, List.of(DISABLED_MESSAGE));
        }
        Result result = entry.contributor().execute(List.copyOf(arguments.subList(2, arguments.size())));
        return bounded(result);
    }

    public int size() { return entries.size(); }

    private static Result bounded(Result source) {
        ArrayList<String> lines = new ArrayList<>();
        for (String line : source.messages()) {
            if (lines.size() == BetaRuntimeCommandService.MAXIMUM_RESPONSE_LINES) break;
            lines.add(BetaRuntimeModuleResult.bounded(line));
        }
        return new Result(source.success(), lines);
    }

    public record Entry(String subject, BetaRuntimeModuleId moduleId, Contributor contributor) {
        public Entry {
            if (subject == null || !subject.matches("[a-z][a-z0-9-]{0,31}")
                    || moduleId == null || contributor == null) {
                throw new IllegalArgumentException("invalid operator contributor");
            }
        }
    }

    @FunctionalInterface public interface Contributor { Result execute(List<String> arguments); }

    public record Result(boolean success, List<String> messages) {
        public Result {
            messages = List.copyOf(messages == null ? List.of() : messages);
            if (messages.size() > BetaRuntimeCommandService.MAXIMUM_RESPONSE_LINES) {
                throw new IllegalArgumentException("operator response is oversized");
            }
        }
    }
}
