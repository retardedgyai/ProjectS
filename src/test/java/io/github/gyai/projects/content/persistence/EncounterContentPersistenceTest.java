package io.github.gyai.projects.content.persistence;

import io.github.gyai.projects.content.definition.ContentDefinitionValidator;
import io.github.gyai.projects.content.definition.EncounterDefinition;
import io.github.gyai.projects.content.definition.GrohmBossContentFixture;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Assertion-main coverage for the encounter JSON persistence foundation. */
public final class EncounterContentPersistenceTest {
    private static final String ENCOUNTER_ID = GrohmBossContentFixture.ENCOUNTER_ID;

    private EncounterContentPersistenceTest() {
    }

    public static void main(String[] args) throws Exception {
        canonicalGrohmRoundTrip();
        allVariantsAndMultiActorOwnership();
        strictDocumentRejections();
        structuralAndGraphRejections();
        repositoryRevisionHistoryRollbackAndListing();
        lockAndPathSafety();
        System.out.println("Encounter content persistence tests passed");
    }

    private static void canonicalGrohmRoundTrip() {
        EncounterDefinitionJsonCodec codec = new EncounterDefinitionJsonCodec();
        EncounterDefinition fixture = GrohmBossContentFixture.encounter();
        EncounterDefinitionJsonCodec.EncodeResult encoded = codec.encode(fixture);
        assert encoded.success() : encoded.error();
        String json = new String(encoded.bytes(), StandardCharsets.UTF_8);
        assert json.startsWith("{\"format\":\"projects-content\",\"schemaVersion\":1,")
                : json;
        assert json.contains("\"kind\":\"encounter\"") : json;
        assert json.contains("\"type\":\"ordered\"") : json;
        assert json.contains("\"type\":\"weighted\"") : json;
        assert json.contains("\"type\":\"actor_health_ratio_at_most\"") : json;
        assert json.contains("\"type\":\"elapsed_ticks_at_least\"") : json;
        assert json.contains("\"type\":\"all\"") : json;
        assert json.contains("\"type\":\"any\"") : json;
        assert json.contains("\"clock\":\"phase\"") : json;
        assert json.contains("\"clock\":\"encounter\"") : json;
        assert json.contains("cancel_ability_clear_current_cc_suppress_no_buffer") : json;
        assert json.contains("unsuppress_cc_no_restore") : json;
        assert json.endsWith("\n") : json;

        EncounterDefinitionJsonCodec.DecodeResult decoded = codec.decode(encoded.bytes());
        assert decoded.success() : decoded.error();
        assert decoded.definition().equals(fixture) : decoded.definition();
        EncounterDefinitionJsonCodec.EncodeResult reencoded = codec.encode(decoded.definition());
        assert reencoded.success() : reencoded.error();
        assert Arrays.equals(encoded.bytes(), reencoded.bytes());

        ContentDefinitionValidator.Catalog source = GrohmBossContentFixture.catalog();
        ContentDefinitionValidator.Catalog combined = new ContentDefinitionValidator.Catalog(
                source.mobs(), source.abilities(), List.of(decoded.definition()), source.visuals(),
                source.rewardReferences(), source.equipmentIds(), source.validEntityTypeIds());
        assert new ContentDefinitionValidator().validate(combined).valid()
                : new ContentDefinitionValidator().validate(combined).issues();

        EncounterDefinition encounter = decoded.definition();
        assert encounter.phases().get(0).actorBehaviors().getFirst().state()
                == EncounterDefinition.ActorState.ACTIVE;
        assert encounter.phases().get(1).actorBehaviors().getFirst().abilitySelectionPolicy()
                instanceof EncounterDefinition.WeightedSelection;
        assert encounter.phases().get(2).actorBehaviors().getFirst().state()
                == EncounterDefinition.ActorState.DOWNED;
        assert encounter.phases().get(2).actorBehaviors().getFirst()
                .abilitySelectionPolicy() == null;
        assert encounter.phases().get(1).transitions().getFirst().condition()
                instanceof EncounterDefinition.ElapsedTicksAtLeast;
        assert ((EncounterDefinition.ElapsedTicksAtLeast) encounter.phases().get(1)
                .transitions().getFirst().condition()).clock() == EncounterDefinition.Clock.PHASE;
        assert ((EncounterDefinition.ElapsedTicksAtLeast) encounter.phases().get(2)
                .transitions().getFirst().condition()).clock() == EncounterDefinition.Clock.PHASE;
        assert encounter.failurePolicy().condition() instanceof EncounterDefinition.Any;
    }

    private static void allVariantsAndMultiActorOwnership() {
        EncounterDefinition source = GrohmBossContentFixture.encounter();
        EncounterDefinition.Actor second = new EncounterDefinition.Actor(
                "second", "projects:mob/second");
        EncounterDefinition.ActorBehavior firstBehavior =
                source.phases().getFirst().actorBehaviors().getFirst();
        EncounterDefinition.ActorBehavior secondBehavior = new EncounterDefinition.ActorBehavior(
                "second", EncounterDefinition.ActorState.ACTIVE,
                Set.of(GrohmBossContentFixture.CHARGE_ID),
                new EncounterDefinition.OrderedSelection(
                        List.of(GrohmBossContentFixture.CHARGE_ID)));
        EncounterDefinition.Phase phase = new EncounterDefinition.Phase(
                "phase-one", true, List.of(firstBehavior, secondBehavior), List.of());
        EncounterDefinition multiActor = new EncounterDefinition(
                1, "projects:encounter/multi", 1, List.of(
                source.actors().getFirst(), second), List.of(phase), source.resetPolicy(),
                new EncounterDefinition.VictoryPolicy(
                        new EncounterDefinition.ActorHealthRatioAtMost("second", 0.0)),
                new EncounterDefinition.FailurePolicy(
                        new EncounterDefinition.All(List.of(
                                EncounterDefinition.Always.INSTANCE,
                                new EncounterDefinition.ElapsedTicksAtLeast(10,
                                        EncounterDefinition.Clock.ENCOUNTER))),
                        EncounterDefinition.FailureMode.TIMEOUT),
                source.rewardReferences());
        EncounterDefinitionJsonCodec codec = new EncounterDefinitionJsonCodec();
        EncounterDefinitionJsonCodec.EncodeResult encoded = codec.encode(multiActor);
        assert encoded.success() : encoded.error();
        EncounterDefinitionJsonCodec.DecodeResult decoded = codec.decode(encoded.bytes());
        assert decoded.success() : decoded.error();
        assert decoded.definition().equals(multiActor);

        EncounterDefinition.ActorBehavior downed = new EncounterDefinition.ActorBehavior(
                "second", EncounterDefinition.ActorState.DOWNED, Set.of(), null);
        EncounterDefinition.Phase downPhase = new EncounterDefinition.Phase(
                "phase-down", false, List.of(firstBehavior, downed), List.of());
        EncounterDefinition withDownedOwner = new EncounterDefinition(
                1, "projects:encounter/down-owner", 1,
                multiActor.actors(), List.of(phase, downPhase), source.resetPolicy(),
                multiActor.victoryPolicy(), multiActor.failurePolicy(), source.rewardReferences());
        EncounterDefinitionJsonCodec.EncodeResult downEncoded = codec.encode(withDownedOwner);
        assert !downEncoded.success();
        assert EncounterPersistenceError.UNREACHABLE_PHASE.equals(downEncoded.error().code())
                || EncounterPersistenceError.PHASE_CYCLE.equals(downEncoded.error().code())
                || EncounterPersistenceError.MISSING_STATE_TRANSITION.equals(
                downEncoded.error().code());
    }

    private static void strictDocumentRejections() {
        EncounterDefinitionJsonCodec codec = new EncounterDefinitionJsonCodec();
        String canonical = canonical(codec, GrohmBossContentFixture.encounter());
        reject(codec, canonical.replace("\"format\":\"projects-content\"",
                "\"format\":\"projects-content\",\"format\":\"projects-content\""),
                EncounterPersistenceError.DUPLICATE_KEY, "$.format");
        reject(codec, canonical.replace(",\"definition\":{",
                ",\"unknown\":0,\"definition\":{"),
                EncounterPersistenceError.UNKNOWN_KEY, "$.unknown");
        reject(codec, canonical + "{}", EncounterPersistenceError.TRAILING_DATA, "$");
        reject(codec, canonical.replace("\"kind\":\"encounter\"",
                "\"kind\":\"mob\""), EncounterPersistenceError.WRONG_KIND, "$.kind");
        reject(codec, canonical.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                EncounterPersistenceError.UNSUPPORTED_SCHEMA, "$.schemaVersion");
        reject(codec, canonical.replace("\"type\":\"always\"",
                "\"type\":\"not_a_condition\""),
                EncounterPersistenceError.UNKNOWN_VARIANT,
                "$.definition.failurePolicy.condition.conditions[1].conditions[1].type");
        reject(codec, canonical.replace("\"type\":\"ordered\",\"abilityReferences\"",
                "\"type\":\"weighted\",\"abilityReferences\""),
                EncounterPersistenceError.UNKNOWN_KEY,
                "$.definition.phases[0].actorBehaviors[0].abilitySelectionPolicy.abilityReferences");
        reject(codec, canonical.replace("\"rewardReferences\":[\"projects:reward/grohm-placeholder\"]",
                "\"rewardReferences\":null"), EncounterPersistenceError.NULL_REQUIRED_FIELD,
                "$.definition.rewardReferences");
        reject(codec, new byte[]{'{', (byte) 0xc3, '('},
                EncounterPersistenceError.INVALID_UTF8, "$");
        reject(codec, concat(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf},
                canonical.getBytes(StandardCharsets.UTF_8)),
                EncounterPersistenceError.BOM_REJECTED, "$");
    }

    private static void structuralAndGraphRejections() {
        EncounterDefinition source = GrohmBossContentFixture.encounter();
        EncounterDefinitionJsonCodec codec = new EncounterDefinitionJsonCodec();
        EncounterDefinition.Phase phaseTwo = source.phases().get(1);
        EncounterDefinition.Phase downed = source.phases().get(2);

        EncounterDefinition.Transition missingEffect = new EncounterDefinition.Transition(
                "enter-down", new EncounterDefinition.ElapsedTicksAtLeast(120,
                EncounterDefinition.Clock.PHASE), "phase-downed", List.of());
        EncounterDefinition.Phase missingEffectPhase = new EncounterDefinition.Phase(
                phaseTwo.phaseId(), false, phaseTwo.actorBehaviors(), List.of(missingEffect));
        rejectEncode(codec, withPhases(source, List.of(source.phases().getFirst(),
                missingEffectPhase, downed)), EncounterPersistenceError.MISSING_STATE_TRANSITION);

        EncounterDefinition.Transition mismatchedEffect = new EncounterDefinition.Transition(
                "enter-down", new EncounterDefinition.ElapsedTicksAtLeast(120,
                EncounterDefinition.Clock.PHASE), "phase-downed", List.of(
                new EncounterDefinition.ActorStateTransition("grohm",
                        EncounterDefinition.ActorState.DOWNED,
                        EncounterDefinition.ActorState.ACTIVE,
                        EncounterDefinition.DownControlPolicy.EXIT_DOWN)));
        EncounterDefinition.Phase mismatchedPhase = new EncounterDefinition.Phase(
                phaseTwo.phaseId(), false, phaseTwo.actorBehaviors(), List.of(mismatchedEffect));
        rejectEncode(codec, withPhases(source, List.of(source.phases().getFirst(),
                mismatchedPhase, downed)), EncounterPersistenceError.INVALID_STATE_TRANSITION);

        EncounterDefinition.Transition redundant = new EncounterDefinition.Transition(
                "health-half", new EncounterDefinition.ActorHealthRatioAtMost("grohm", 0.5),
                "phase-two", List.of(new EncounterDefinition.ActorStateTransition("grohm",
                EncounterDefinition.ActorState.ACTIVE, EncounterDefinition.ActorState.DOWNED,
                EncounterDefinition.DownControlPolicy.ENTER_DOWN)));
        EncounterDefinition.Phase redundantPhase = new EncounterDefinition.Phase(
                "phase-one", true, source.phases().getFirst().actorBehaviors(), List.of(redundant));
        rejectEncode(codec, withPhases(source, List.of(redundantPhase, phaseTwo, downed)),
                EncounterPersistenceError.INVALID_STATE_TRANSITION);

        EncounterDefinition.ActorBehavior downedWithPool = new EncounterDefinition.ActorBehavior(
                "grohm", EncounterDefinition.ActorState.DOWNED,
                Set.of(GrohmBossContentFixture.SLAM_ID),
                new EncounterDefinition.OrderedSelection(List.of(GrohmBossContentFixture.SLAM_ID)));
        EncounterDefinition.Phase brokenDown = new EncounterDefinition.Phase(
                "phase-downed", false, List.of(downedWithPool), downed.transitions());
        rejectEncode(codec, withPhases(source, List.of(source.phases().getFirst(), phaseTwo,
                brokenDown)), EncounterPersistenceError.DOWNED_ABILITY_POOL);

        EncounterDefinition.Phase duplicatePhase = new EncounterDefinition.Phase(
                "phase-one", false, source.phases().getFirst().actorBehaviors(),
                source.phases().getFirst().transitions());
        rejectEncode(codec, withPhases(source, List.of(source.phases().getFirst(), duplicatePhase,
                phaseTwo, downed)), EncounterPersistenceError.DUPLICATE_LOCAL_ID);

        EncounterDefinition.ActorBehavior active = source.phases().getFirst()
                .actorBehaviors().getFirst();
        EncounterDefinition.Phase instantCycle = new EncounterDefinition.Phase(
                "phase-one", true, List.of(active), List.of(new EncounterDefinition.Transition(
                "loop", EncounterDefinition.Always.INSTANCE, "phase-one")));
        rejectEncode(codec, withPhases(source, List.of(instantCycle)),
                EncounterPersistenceError.PHASE_CYCLE);

        EncounterDefinition.Phase safeCycle = new EncounterDefinition.Phase(
                "phase-one", true, List.of(active), List.of(new EncounterDefinition.Transition(
                "loop", new EncounterDefinition.ElapsedTicksAtLeast(1,
                EncounterDefinition.Clock.PHASE), "phase-one")));
        EncounterDefinition safe = withPhases(source, List.of(safeCycle));
        EncounterDefinitionJsonCodec.EncodeResult safeResult = codec.encode(safe);
        assert safeResult.success() : safeResult.error();

        EncounterDefinition.Condition deep = EncounterDefinition.Always.INSTANCE;
        for (int index = 0; index < EncounterDefinitionJsonCodec.MAX_CONDITION_DEPTH + 1;
             index++) {
            deep = new EncounterDefinition.All(List.of(deep));
        }
        EncounterDefinition.Phase deepPhase = new EncounterDefinition.Phase(
                "phase-one", true, List.of(active), List.of(new EncounterDefinition.Transition(
                "loop", deep, "phase-one")));
        rejectEncode(codec, withPhases(source, List.of(deepPhase)),
                EncounterPersistenceError.NUMBER_OUT_OF_RANGE);
    }

    private static void repositoryRevisionHistoryRollbackAndListing() throws IOException {
        Path root = Files.createTempDirectory("projects-encounter-json-");
        try {
            EncounterDefinitionJsonRepository repository =
                    new EncounterDefinitionJsonRepository(root);
            EncounterDefinition base = withRevision(GrohmBossContentFixture.encounter(), 0);
            assert repository.create(base, 0).success();
            assert repository.load(ENCOUNTER_ID).success();
            assert Files.isRegularFile(root.resolve("encounters/projects/encounter/grohm.json"),
                    LinkOption.NOFOLLOW_LINKS);
            assert Files.isRegularFile(root.resolve(
                    EncounterDefinitionJsonRepository.LOCK_FILE_NAME), LinkOption.NOFOLLOW_LINKS);

            EncounterDefinitionJsonRepository.SaveResult updated = repository.update(
                    withFailureMode(base, EncounterDefinition.FailureMode.ABORT, 1), 1);
            assert updated.success() : updated.error();
            assert updated.currentRevision() == 2;
            assert repository.history(ENCOUNTER_ID).equals(List.of(1L));
            byte[] revisionTwo = Files.readAllBytes(root.resolve(
                    "encounters/projects/encounter/grohm.json"));

            EncounterDefinitionJsonRepository.SaveResult conflict = repository.update(
                    withFailureMode(base, EncounterDefinition.FailureMode.TIMEOUT, 1), 1);
            assert conflict.conflict() : conflict;
            assert Arrays.equals(revisionTwo, Files.readAllBytes(root.resolve(
                    "encounters/projects/encounter/grohm.json")));

            EncounterDefinitionJsonRepository.SaveResult rollback = repository.rollback(
                    ENCOUNTER_ID, 1, 2);
            assert rollback.success() : rollback.error();
            assert rollback.currentRevision() == 3;
            assert rollback.definition().failurePolicy().mode()
                    == EncounterDefinition.FailureMode.RESET;
            assert repository.history(ENCOUNTER_ID).equals(List.of(1L, 2L));

            Path malformed = root.resolve("encounters/projects/encounter/bad.json");
            Files.createDirectories(malformed.getParent());
            Files.writeString(malformed, "{\"format\":\"projects-content\"}\n",
                    StandardCharsets.UTF_8);
            List<EncounterDefinitionJsonRepository.LoadResult> listed = repository.list();
            assert listed.size() == 2 : listed;
            assert listed.stream().anyMatch(EncounterDefinitionJsonRepository.LoadResult::success);
            assert listed.stream().anyMatch(result -> !result.success()
                    && EncounterPersistenceError.MISSING_VALUE.equals(result.error().code()));
            assert listed.get(0).id().compareTo(listed.get(1).id()) <= 0;
            repository.close();
            assert repository.load(ENCOUNTER_ID).status()
                    == EncounterDefinitionJsonRepository.LoadStatus.CLOSED;
        } finally {
            deleteTree(root);
        }
    }

    private static void lockAndPathSafety() throws Exception {
        Path root = Files.createTempDirectory("projects-encounter-lock-");
        try {
            EncounterDefinitionJsonRepository repository =
                    new EncounterDefinitionJsonRepository(root);
            EncounterDefinition base = withRevision(GrohmBossContentFixture.encounter(), 0);
            assert repository.create(base).success();
            assert repository.load("projects:encounter/../escape").status()
                    == EncounterDefinitionJsonRepository.LoadStatus.UNSAFE_PATH;
            Path lockPath = root.resolve(EncounterDefinitionJsonRepository.LOCK_FILE_NAME);
            byte[] before = Files.readAllBytes(root.resolve(
                    "encounters/projects/encounter/grohm.json"));
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.READ,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock ignored = channel.lock()) {
                EncounterDefinitionJsonRepository.SaveResult blocked = repository.update(
                        withFailureMode(base, EncounterDefinition.FailureMode.ABORT, 1), 1);
                assert !blocked.success() : blocked;
                assert EncounterPersistenceError.LOCK_UNAVAILABLE.equals(blocked.error().code());
                assert Arrays.equals(before, Files.readAllBytes(root.resolve(
                        "encounters/projects/encounter/grohm.json")));
                assert repository.history(ENCOUNTER_ID).isEmpty();
            }
            repository.close();
        } finally {
            deleteTree(root);
        }
    }

    private static String canonical(EncounterDefinitionJsonCodec codec,
                                    EncounterDefinition definition) {
        EncounterDefinitionJsonCodec.EncodeResult result = codec.encode(definition);
        assert result.success() : result.error();
        return new String(result.bytes(), StandardCharsets.UTF_8);
    }

    private static void reject(EncounterDefinitionJsonCodec codec, String json,
                               String code, String path) {
        EncounterDefinitionJsonCodec.DecodeResult result = codec.decode(
                json.getBytes(StandardCharsets.UTF_8));
        assert !result.success() : "document was accepted";
        assert code.equals(result.error().code()) : result.error();
        assert path.equals(result.error().path()) : result.error();
    }

    private static void reject(EncounterDefinitionJsonCodec codec, byte[] bytes,
                               String code, String path) {
        EncounterDefinitionJsonCodec.DecodeResult result = codec.decode(bytes);
        assert !result.success() : "document was accepted";
        assert code.equals(result.error().code()) : result.error();
        assert path.equals(result.error().path()) : result.error();
    }

    private static void rejectEncode(EncounterDefinitionJsonCodec codec,
                                     EncounterDefinition definition, String code) {
        EncounterDefinitionJsonCodec.EncodeResult result = codec.encode(definition);
        assert !result.success() : "definition was accepted";
        assert code.equals(result.error().code()) : result.error();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static EncounterDefinition withRevision(EncounterDefinition source, long revision) {
        return new EncounterDefinition(1, source.encounterId(), revision, source.actors(),
                source.phases(), source.resetPolicy(), source.victoryPolicy(),
                source.failurePolicy(), source.rewardReferences());
    }

    private static EncounterDefinition withFailureMode(EncounterDefinition source,
                                                       EncounterDefinition.FailureMode mode,
                                                       long revision) {
        return new EncounterDefinition(1, source.encounterId(), revision, source.actors(),
                source.phases(), source.resetPolicy(), source.victoryPolicy(),
                new EncounterDefinition.FailurePolicy(source.failurePolicy().condition(), mode),
                source.rewardReferences());
    }

    private static EncounterDefinition withPhases(EncounterDefinition source,
                                                  List<EncounterDefinition.Phase> phases) {
        return new EncounterDefinition(1, source.encounterId(), source.revision(), source.actors(),
                phases, source.resetPolicy(), source.victoryPolicy(), source.failurePolicy(),
                source.rewardReferences());
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
    }
}
