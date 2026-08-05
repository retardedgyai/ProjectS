package io.github.gyai.projects.beta.activation.track3;

import io.github.gyai.projects.beta.activation.track3.spi.BetaOperatorCommandContributor;
import io.github.gyai.projects.enhancement.v2.EnhancementOutcome;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Descriptor and pure dispatcher only; ProjectCommand remains untouched. */
public final class StagingEconomyOperatorContributor
        implements BetaOperatorCommandContributor {
    private static final String PREFIX = "/projects beta staging economy ";
    private final StagingEconomyOperationPort operations;

    public StagingEconomyOperatorContributor(StagingEconomyOperationPort operations) {
        if (operations == null) throw new IllegalArgumentException("operations port is required");
        this.operations = operations;
    }

    @Override
    public String contributorId() {
        return "track3-staging-economy";
    }

    @Override
    public Set<String> commandPaths() {
        return Set.of(
                PREFIX + "give",
                PREFIX + "refine",
                PREFIX + "craft",
                PREFIX + "promote",
                PREFIX + "outcome <success|no-change|downgrade|broken>",
                PREFIX + "enhance",
                PREFIX + "break",
                PREFIX + "repair",
                PREFIX + "status");
    }

    @Override
    public CommandResponse execute(StagingOperationAccess access, List<String> arguments) {
        if (access == null || !access.projectsDev()) {
            return new CommandResponse(false, "projects.dev required");
        }
        List<String> args = arguments == null ? List.of() : List.copyOf(arguments);
        if (args.isEmpty()) return usage();
        String action = args.getFirst().toLowerCase(Locale.ROOT);
        try {
            if (action.equals("outcome")) {
                if (args.size() != 2) return usage();
                operations.selectEnhancementOutcome(access, parseOutcome(args.get(1)));
                return new CommandResponse(true, "staging enhancement outcome armed once");
            }
            if (action.equals("status")) {
                var snapshot = operations.status(access.playerId());
                return new CommandResponse(true,
                        "revision=%d resources=%d equipment=%d reservations=%d".formatted(
                                snapshot.revision(), snapshot.resources().size(),
                                snapshot.equipment().size(), snapshot.activeReservations()));
            }
            StagingEconomyOperationPort.OperationKind kind = switch (action) {
                case "give" -> StagingEconomyOperationPort.OperationKind.GIVE;
                case "refine" -> StagingEconomyOperationPort.OperationKind.REFINE;
                case "craft" -> StagingEconomyOperationPort.OperationKind.CRAFT;
                case "promote" -> StagingEconomyOperationPort.OperationKind.PROMOTE;
                case "enhance" -> StagingEconomyOperationPort.OperationKind.ENHANCE;
                case "break" -> StagingEconomyOperationPort.OperationKind.BREAK;
                case "repair" -> StagingEconomyOperationPort.OperationKind.REPAIR;
                default -> null;
            };
            if (kind == null) return usage();
            Optional<String> item = Optional.empty();
            long quantity = 0;
            if (kind == StagingEconomyOperationPort.OperationKind.GIVE) {
                if (args.size() != 3) return new CommandResponse(false,
                        "give requires canonical staging ID and positive quantity");
                item = Optional.of(args.get(1));
                quantity = Long.parseLong(args.get(2));
            } else if (args.size() != 1) {
                return usage();
            }
            var result = operations.execute(new StagingEconomyOperationPort.OperationRequest(
                    UUID.randomUUID(), access, kind, item, quantity));
            return new CommandResponse(
                    result.status() == StagingEconomyOperationPort.Status.COMMITTED
                            || result.status() == StagingEconomyOperationPort.Status.REPLAYED,
                    action + " " + result.status().name().toLowerCase(Locale.ROOT)
                            + (result.detail().isBlank() ? "" : ": " + result.detail()));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return new CommandResponse(false, failure.getMessage());
        }
    }

    private static EnhancementOutcome parseOutcome(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "success" -> EnhancementOutcome.SUCCESS;
            case "no-change" -> EnhancementOutcome.NO_CHANGE;
            case "downgrade" -> EnhancementOutcome.DOWNGRADE;
            case "broken" -> EnhancementOutcome.BROKEN;
            default -> throw new IllegalArgumentException("unknown staging outcome");
        };
    }

    private static CommandResponse usage() {
        return new CommandResponse(false, "usage: /projects beta staging economy <action>");
    }
}
