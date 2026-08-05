package io.github.gyai.projects.beta.activation.track2;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** `/projects beta staging element ...` contribution; not centrally registered in this Track. */
public final class CombatElementsOperatorCommandContributor
        implements BetaOperatorCommandContributor {
    private static final List<String> USAGE = List.of(
            "Usage: /projects beta staging element <none|fire|ice|status|reset-target>");

    private final TrainingDummyElementRuntime runtime;

    CombatElementsOperatorCommandContributor(TrainingDummyElementRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public String route() {
        return "staging element";
    }

    @Override
    public List<String> execute(boolean permitted, UUID actorId, List<String> arguments) {
        if (!permitted) return List.of("Missing permission: projects.dev");
        if (actorId == null) return List.of("A player UUID is required for staging element profile");
        List<String> args = arguments == null ? List.of() : List.copyOf(arguments);
        if (args.isEmpty()) return USAGE;
        String action = args.getFirst().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "none", "fire", "ice" -> select(actorId, action);
            case "status" -> List.of("staging element profile="
                    + runtime.snapshots().playerProfile(actorId).name());
            case "reset-target" -> resetTarget(args);
            default -> USAGE;
        };
    }

    private List<String> select(UUID actorId, String action) {
        StagingElementProfile profile = StagingElementProfile.valueOf(
                action.toUpperCase(Locale.ROOT));
        if (!runtime.setProfile(actorId, profile)) {
            return List.of("staging element profile rejected");
        }
        return List.of("staging element profile=" + profile.name());
    }

    private List<String> resetTarget(List<String> args) {
        if (args.size() != 2) {
            return List.of("Usage: /projects beta staging element reset-target <dummy-uuid>");
        }
        try {
            UUID targetId = UUID.fromString(args.get(1));
            runtime.targetRemoved(targetId);
            return List.of("staging element target reset=" + targetId);
        } catch (IllegalArgumentException exception) {
            return List.of("Invalid Training Dummy UUID");
        }
    }
}
