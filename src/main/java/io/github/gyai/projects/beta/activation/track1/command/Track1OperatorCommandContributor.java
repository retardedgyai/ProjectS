package io.github.gyai.projects.beta.activation.track1.command;

import io.github.gyai.projects.beta.activation.BetaActivationPolicy;
import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentInspectionService;
import io.github.gyai.projects.beta.activation.track1.equipment.EquipmentInspectionSnapshot;
import io.github.gyai.projects.beta.activation.track1.player.PlayerProgressObservation;
import io.github.gyai.projects.beta.activation.track1.player.StagingPlayerProgressService;
import io.github.gyai.projects.beta.activation.track1.spi.BetaOperatorCommandContributor;
import io.github.gyai.projects.beta.activation.track1.spi.BetaOperatorCommandResult;
import io.github.gyai.projects.beta.activation.track1.spi.BetaOperatorSubject;

import java.util.List;
import java.util.Locale;

public final class Track1OperatorCommandContributor implements BetaOperatorCommandContributor {
    private static final String USAGE = "beta staging player <status|snapshot> | beta staging equipment inspect";
    private final BetaActivationPolicy policy;
    private final StagingPlayerProgressService progress;
    private final EquipmentInspectionService equipment;
    private final EquipmentCommandSource equipmentSource;

    public Track1OperatorCommandContributor(BetaActivationPolicy policy,
                                            StagingPlayerProgressService progress,
                                            EquipmentInspectionService equipment,
                                            EquipmentCommandSource equipmentSource) {
        if (policy == null || progress == null || equipment == null || equipmentSource == null) {
            throw new IllegalArgumentException("command infrastructure is required");
        }
        this.policy = policy;
        this.progress = progress;
        this.equipment = equipment;
        this.equipmentSource = equipmentSource;
    }

    @Override public String route() { return "staging"; }

    @Override
    public BetaOperatorCommandResult execute(boolean permitted, BetaOperatorSubject subject,
                                             List<String> arguments) {
        if (!permitted) return BetaOperatorCommandResult.denied();
        if (subject == null || subject.playerId() == null) {
            return rejected("a player subject is required");
        }
        if (!policy.allowsAudience(subject.playerId(), subject.compatibleClient())
                || !policy.allowsWorld(subject.worldName())) {
            return rejected("activation policy denied the subject");
        }
        List<String> supplied = arguments == null ? List.of() : List.copyOf(arguments);
        List<String> args = supplied.size() == 3
                && "staging".equals(normalize(supplied.get(0)))
                ? supplied.subList(1, supplied.size()) : supplied;
        if (args.size() != 2) {
            return rejected(USAGE);
        }
        String area = normalize(args.get(0));
        String action = normalize(args.get(1));
        if ("player".equals(area) && "status".equals(action)) return playerStatus(subject);
        if ("player".equals(area) && "snapshot".equals(action)) return playerSnapshot(subject);
        if ("equipment".equals(area) && "inspect".equals(action)) return equipment(subject);
        return rejected(USAGE);
    }

    private BetaOperatorCommandResult playerStatus(BetaOperatorSubject subject) {
        return progress.observation(subject.playerId())
                .map(value -> accepted("player=" + subject.playerId()
                        + " status=" + value.status()
                        + " revision=" + value.observedRevision()
                        + " matches=" + value.matches()))
                .orElseGet(() -> accepted("player=" + subject.playerId() + " status=NOT_OBSERVED"));
    }

    private BetaOperatorCommandResult playerSnapshot(BetaOperatorSubject subject) {
        return progress.observation(subject.playerId())
                .map(this::snapshot)
                .orElseGet(() -> accepted("player=" + subject.playerId() + " snapshot=UNAVAILABLE"));
    }

    private BetaOperatorCommandResult snapshot(PlayerProgressObservation value) {
        return new BetaOperatorCommandResult(true, List.of(
                "player=" + value.playerId() + " legacyLevel=" + value.legacySnapshot().level()
                        + " stagingPresent=" + value.stagingSnapshot().isPresent(),
                "differences=" + value.differences()));
    }

    private BetaOperatorCommandResult equipment(BetaOperatorSubject subject) {
        EquipmentInspectionSnapshot snapshot = equipment.inspect(subject.playerId(),
                equipmentSource.scan(subject.playerId()));
        return accepted("player=" + subject.playerId()
                + " items=" + snapshot.items().size()
                + " legacyReadable=" + snapshot.readableLegacyItems()
                + " v1Valid=" + snapshot.validV1Items()
                + " unknownMods=" + snapshot.isolatedUnknownMods());
    }

    private static BetaOperatorCommandResult accepted(String message) {
        return new BetaOperatorCommandResult(true, List.of(message));
    }
    private static BetaOperatorCommandResult rejected(String message) {
        return new BetaOperatorCommandResult(false, List.of(message));
    }
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
