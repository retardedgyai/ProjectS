package io.github.gyai.projects.content.persistence;

import io.github.gyai.projects.ability.TargetSelector;
import io.github.gyai.projects.combat.damage.AttackMetadata;
import io.github.gyai.projects.combat.damage.AttackTag;
import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.combat.damage.DamageKind;
import io.github.gyai.projects.combat.damage.DamageType;
import io.github.gyai.projects.combat.damage.ElementProfile;
import io.github.gyai.projects.content.definition.AbilityDefinition;
import io.github.gyai.projects.content.definition.ContentDefinitionValidator;
import io.github.gyai.projects.content.definition.GrohmBossContentFixture;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** Assertion-main coverage for the Ability JSON persistence foundation. */
public final class AbilityContentPersistenceTest {
    private static final String SLAM_ID = GrohmBossContentFixture.SLAM_ID;
    private static final String CHARGE_ID = GrohmBossContentFixture.CHARGE_ID;
    private static final String SHOCKWAVE_ID = GrohmBossContentFixture.SHOCKWAVE_ID;

    private AbilityContentPersistenceTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--hold-lock".equals(args[0])) {
            holdLockForProcess(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        canonicalGrohmRoundTrips();
        nullVisualAndEnumCoverage();
        strictDocumentRejections();
        repositoryRevisionHistoryRollbackAndLayout();
        listPathAndSymlinkSafety();
        concurrentUpdatesSerialize();
        heldOsLockReturnsBoundedContention();
        processHeldOsLockReturnsBoundedContention();
        decodedAbilitiesFitGrohmCatalog();
        System.out.println("Ability content persistence tests passed");
    }

    private static void canonicalGrohmRoundTrips() {
        AbilityDefinitionJsonCodec codec = new AbilityDefinitionJsonCodec();
        assertCanonical(codec, GrohmBossContentFixture.slam(),
                "{\"format\":\"projects-content\",\"schemaVersion\":1,"
                        + "\"kind\":\"ability\",\"id\":\"projects:ability/grohm/slam\","
                        + "\"revision\":1,\"definition\":{\"displayName\":\"Breakwater Slam\","
                        + "\"timing\":{\"castTicks\":0,\"recoveryTicks\":10,\"cooldownTicks\":80},"
                        + "\"targeting\":{\"selector\":\"primary_target\",\"maxRange\":7.0},"
                        + "\"timeline\":[{\"type\":\"wait\",\"stepId\":\"windup\","
                        + "\"ticks\":20},{\"type\":\"telegraph\","
                        + "\"stepId\":\"slam-warning\",\"origin\":\"self\","
                        + "\"shape\":{\"type\":\"circle\",\"radius\":3.5},"
                        + "\"durationTicks\":20,\"lockAtCreation\":true},{\"type\":\"damage\","
                        + "\"stepId\":\"slam-hit\",\"target\":\"primary_target\","
                        + "\"shape\":{\"type\":\"circle\",\"radius\":3.5},"
                        + "\"damageType\":\"physical\",\"damageKind\":\"direct_skill\","
                        + "\"fixedDamage\":18.0,\"coefficient\":0.0,"
                        + "\"criticalAllowed\":false,\"metadata\":{\"tags\":[\"physical\"],"
                        + "\"elements\":{\"values\":{},\"scalingRates\":{}}}},{"
                        + "\"type\":\"knockback\",\"stepId\":\"slam-knockback\","
                        + "\"target\":\"primary_target\",\"shape\":{\"type\":\"circle\","
                        + "\"radius\":3.5},\"horizontalStrength\":0.6,"
                        + "\"verticalStrength\":0.25}],\"interruptPolicy\":\"on_hard_control\","
                        + "\"visualReference\":\"projects:vfx/grohm/slam-circle\"}}\n",
                "fe836e9232ec5cbf314f25121de1eda9edfe0e3cd1d4bc97b827a548d20599e7");
        assertCanonical(codec, GrohmBossContentFixture.charge(),
                "{\"format\":\"projects-content\",\"schemaVersion\":1,"
                        + "\"kind\":\"ability\",\"id\":\"projects:ability/grohm/charge\","
                        + "\"revision\":1,\"definition\":{\"displayName\":\"Hullbreaker Charge\","
                        + "\"timing\":{\"castTicks\":0,\"recoveryTicks\":12,\"cooldownTicks\":120},"
                        + "\"targeting\":{\"selector\":\"primary_target\",\"maxRange\":32.0},"
                        + "\"timeline\":[{\"type\":\"telegraph\","
                        + "\"stepId\":\"charge-warning\",\"origin\":\"self\","
                        + "\"shape\":{\"type\":\"line\",\"length\":12.0,\"width\":1.25},"
                        + "\"durationTicks\":15,\"lockAtCreation\":true},{\"type\":\"charge\","
                        + "\"stepId\":\"charge-movement\",\"target\":\"primary_target\","
                        + "\"path\":{\"type\":\"line\",\"length\":12.0,\"width\":1.0},"
                        + "\"durationTicks\":18,\"speed\":1.0},{\"type\":\"damage\","
                        + "\"stepId\":\"charge-hit\",\"target\":\"primary_target\","
                        + "\"shape\":{\"type\":\"line\",\"length\":12.0,\"width\":1.5},"
                        + "\"damageType\":\"physical\",\"damageKind\":\"direct_skill\","
                        + "\"fixedDamage\":24.0,\"coefficient\":0.0,"
                        + "\"criticalAllowed\":false,\"metadata\":{\"tags\":[\"physical\"],"
                        + "\"elements\":{\"values\":{},\"scalingRates\":{}}}}],"
                        + "\"interruptPolicy\":\"on_hard_control\","
                        + "\"visualReference\":\"projects:vfx/grohm/charge-line\"}}\n",
                "426a31e920ee8f9f53a5b994c6c64228f865e22d5eb7fa66fdf0ab68950c9928");
        assertCanonical(codec, GrohmBossContentFixture.shockwave(),
                "{\"format\":\"projects-content\",\"schemaVersion\":1,"
                        + "\"kind\":\"ability\",\"id\":\"projects:ability/grohm/shockwave\","
                        + "\"revision\":1,\"definition\":{\"displayName\":\"Deep Tide Shockwave\","
                        + "\"timing\":{\"castTicks\":0,\"recoveryTicks\":10,\"cooldownTicks\":100},"
                        + "\"targeting\":{\"selector\":\"self\",\"maxRange\":8.0},"
                        + "\"timeline\":[{\"type\":\"telegraph\","
                        + "\"stepId\":\"shockwave-warning\",\"origin\":\"self\","
                        + "\"shape\":{\"type\":\"donut\",\"innerRadius\":2.0,"
                        + "\"outerRadius\":6.0},\"durationTicks\":25,\"lockAtCreation\":true},"
                        + "{\"type\":\"damage\",\"stepId\":\"shockwave-hit\","
                        + "\"target\":\"primary_target\",\"shape\":{\"type\":\"donut\","
                        + "\"innerRadius\":2.0,\"outerRadius\":6.0},"
                        + "\"damageType\":\"magical\",\"damageKind\":\"direct_skill\","
                        + "\"fixedDamage\":20.0,\"coefficient\":0.0,"
                        + "\"criticalAllowed\":false,\"metadata\":{\"tags\":[\"magic\"],"
                        + "\"elements\":{\"values\":{},\"scalingRates\":{}}}},{"
                        + "\"type\":\"knockback\",\"stepId\":\"shockwave-knockback\","
                        + "\"target\":\"primary_target\",\"shape\":{\"type\":\"donut\","
                        + "\"innerRadius\":2.0,\"outerRadius\":6.0},"
                        + "\"horizontalStrength\":0.8,\"verticalStrength\":0.3}],"
                        + "\"interruptPolicy\":\"on_hard_control\","
                        + "\"visualReference\":\"projects:vfx/grohm/shockwave-donut\"}}\n",
                "183fb5c7fe76e217dcf42d5545583a817bccf357fb1b709a5051088f03c209f9");
    }

    private static void assertCanonical(AbilityDefinitionJsonCodec codec,
                                        AbilityDefinition definition, String expected,
                                        String expectedSha) {
        AbilityDefinitionJsonCodec.EncodeResult encoded = codec.encode(definition);
        assert encoded.success() : encoded.error();
        assert new String(encoded.bytes(), StandardCharsets.UTF_8).equals(expected)
                : new String(encoded.bytes(), StandardCharsets.UTF_8);
        assert sha256(encoded.bytes()).equals(expectedSha) : sha256(encoded.bytes());
        AbilityDefinitionJsonCodec.DecodeResult decoded = codec.decode(encoded.bytes());
        assert decoded.success() : decoded.error();
        assert decoded.definition().equals(definition) : decoded.definition();
        AbilityDefinitionJsonCodec.EncodeResult reencoded = codec.encode(decoded.definition());
        assert reencoded.success() : reencoded.error();
        assert Arrays.equals(encoded.bytes(), reencoded.bytes());
        assert !new String(encoded.bytes(), StandardCharsets.UTF_8).contains("AbilityDefinition");
    }

    private static void nullVisualAndEnumCoverage() {
        AbilityDefinitionJsonCodec codec = new AbilityDefinitionJsonCodec();
        AbilityDefinition source = enumCoverage();
        AbilityDefinitionJsonCodec.EncodeResult encoded = codec.encode(source);
        assert encoded.success() : encoded.error();
        String json = new String(encoded.bytes(), StandardCharsets.UTF_8);
        assert json.contains("\"visualReference\":null");
        assert json.contains("[\"fire\",\"ice\",\"lightning\",\"magic\",\"melee\","
                + "\"normal_attack\",\"physical\",\"projectile\",\"shatter\",\"skill\"]")
                : json;
        assert json.contains("\"values\":{\"fire\":1.0,\"ice\":2.0,\"lightning\":3.0}");
        assert json.contains("\"scalingRates\":{\"fire\":0.25,\"ice\":0.5,\"lightning\":0.75}");
        AbilityDefinitionJsonCodec.DecodeResult decoded = codec.decode(encoded.bytes());
        assert decoded.success() : decoded.error();
        assert decoded.definition().equals(source);
        for (AbilityDefinition.InterruptPolicy policy : AbilityDefinition.InterruptPolicy.values()) {
            AbilityDefinition variant = new AbilityDefinition(
                    source.schemaVersion(), source.abilityId() + "/" + policy.name().toLowerCase(),
                    source.revision(), source.displayName(), source.timing(), source.targeting(),
                    source.timeline(), policy, null);
            AbilityDefinitionJsonCodec.DecodeResult policyDecoded = codec.decode(codec.encode(variant).bytes());
            assert policyDecoded.success() : policyDecoded.error();
            assert policyDecoded.definition().equals(variant);
        }
    }

    private static void strictDocumentRejections() {
        AbilityDefinitionJsonCodec codec = new AbilityDefinitionJsonCodec();
        String canonical = canonical(codec, GrohmBossContentFixture.slam());
        reject(codec, canonical.replace("\"kind\":\"ability\"",
                "\"kind\":\"mob\""), AbilityPersistenceError.WRONG_KIND, "$.kind");
        reject(codec, canonical.replace("\"type\":\"wait\"",
                "\"type\":\"unsupported\""), AbilityPersistenceError.UNKNOWN_VARIANT,
                "$.definition.timeline[0].type");
        reject(codec, canonical.replace("\"ticks\":20}", "\"ticks\":20,\"radius\":1.0}"),
                AbilityPersistenceError.UNKNOWN_KEY, "$.definition.timeline[0].radius");
        reject(codec, canonical.replace("\"stepId\":\"slam-warning\"",
                "\"stepId\":\"windup\""), AbilityPersistenceError.DUPLICATE_LOCAL_ID,
                "$.definition.timeline[1].stepId");
        reject(codec, canonical.replace("projects:vfx/grohm/slam-circle",
                "projects:vfx/../escape"), AbilityPersistenceError.INVALID_NAMESPACED_ID,
                "$.definition.visualReference");
        reject(codec, canonical.replace("\"maxRange\":7.0", "\"maxRange\":NaN"),
                AbilityPersistenceError.NON_FINITE_NUMBER, "$.definition.targeting.maxRange");
        reject(codec, canonical.replace("\"displayName\":\"Breakwater Slam\"",
                "\"displayName\":null"), AbilityPersistenceError.NULL_REQUIRED_FIELD,
                "$.definition.displayName");
        reject(codec, canonical.replace("\"selector\":\"primary_target\"",
                "\"selector\":\"PRIMARY_TARGET\""), AbilityPersistenceError.UNSUPPORTED_ENUM,
                "$.definition.targeting.selector");
        String charge = canonical(codec, GrohmBossContentFixture.charge());
        reject(codec, charge.replace("\"path\":{\"type\":\"line\"",
                "\"path\":{\"type\":\"circle\""), AbilityPersistenceError.UNKNOWN_KEY,
                "$.definition.timeline[1].path.length");
        reject(codec, canonical.replace("\"format\":\"projects-content\"",
                "\"format\":\"projects-content\",\"format\":\"projects-content\""),
                AbilityPersistenceError.DUPLICATE_KEY, "$.format");
        reject(codec, canonical + "{}", AbilityPersistenceError.TRAILING_DATA, "$");
        reject(codec, "\"not-an-envelope\"", AbilityPersistenceError.INVALID_VALUE, "$");
        int timelineStart = canonical.indexOf("\"timeline\"");
        int interruptStart = canonical.indexOf("\"interruptPolicy\"");
        String nullTimeline = canonical.substring(0, timelineStart)
                + "\"timeline\":null,"
                + canonical.substring(interruptStart);
        reject(codec, nullTimeline,
                AbilityPersistenceError.NULL_REQUIRED_FIELD, "$.definition.timeline");
        reject(codec, canonical.replace("\"visualReference\":\"projects:vfx/grohm/slam-circle\"",
                "\"visualReference\":null"), null, null);
        reject(codec, canonical.replace("\"revision\":1", "\"revision\":1.5"),
                AbilityPersistenceError.NON_INTEGRAL_NUMBER, "$.revision");
        reject(codec, canonical.replace("\"revision\":1", "\"revision\":-1"),
                AbilityPersistenceError.NEGATIVE_REVISION, "$.revision");
        reject(codec, canonical.replace("\"revision\":1", "\"revision\":9223372036854775808"),
                AbilityPersistenceError.REVISION_OVERFLOW, "$.revision");
        reject(codec, canonical.replace("\"shape\":{\"type\":\"circle\",\"radius\":3.5}",
                "\"shape\":{\"type\":\"circle\",\"radius\":0.0}"),
                AbilityPersistenceError.NUMBER_OUT_OF_RANGE,
                "$.definition.timeline[1].shape.radius");
        reject(codec, canonical.replace("\"visualReference\":\"projects:vfx/grohm/slam-circle\"",
                "\"visualReference\":\"Projects:Bad\""),
                AbilityPersistenceError.INVALID_NAMESPACED_ID, "$.definition.visualReference");
    }

    private static void repositoryRevisionHistoryRollbackAndLayout() throws IOException {
        Path root = Files.createTempDirectory("projects-ability-json-");
        AbilityDefinition base = withRevision(GrohmBossContentFixture.slam(), 0);
        AbilityDefinitionJsonRepository repository = new AbilityDefinitionJsonRepository(root);
        try {
            AbilityDefinitionJsonRepository.SaveResult invalidCreate = repository.create(base, 1);
            assert invalidCreate.status() == AbilityDefinitionJsonRepository.SaveStatus.REJECTED;
            assert AbilityPersistenceError.INVALID_BASE_REVISION.equals(invalidCreate.error().code());

            AbilityDefinitionJsonRepository.SaveResult created = repository.save(base, 0);
            assert created.success() : created.error();
            assert created.definition().revision() == 1;
            Path current = root.resolve("abilities/projects/ability/grohm/slam.json");
            Path lock = root.resolve(AbilityDefinitionJsonRepository.LOCK_FILE_NAME);
            assert Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS);
            assert Files.isRegularFile(lock, LinkOption.NOFOLLOW_LINKS);
            assert current.equals(repository.load(SLAM_ID).path());

            AbilityDefinitionJsonRepository.SaveResult duplicate = repository.create(base, 0);
            assert duplicate.status() == AbilityDefinitionJsonRepository.SaveStatus.TARGET_EXISTS;
            assert AbilityPersistenceError.TARGET_EXISTS.equals(duplicate.error().code());

            byte[] revisionOne = Files.readAllBytes(current);
            AbilityDefinition changed = withDisplayName(base, "Characterized", 1);
            AbilityDefinitionJsonRepository.SaveResult updated = repository.update(changed, 1);
            assert updated.success() : updated.error();
            assert updated.definition().revision() == 2;
            Path historyOne = root.resolve(".history/abilities/projects/ability/grohm/slam/1.json");
            assert Files.isRegularFile(historyOne, LinkOption.NOFOLLOW_LINKS);
            assert Arrays.equals(revisionOne, Files.readAllBytes(historyOne));
            assert repository.history(SLAM_ID).equals(List.of(1L));

            byte[] beforeConflict = Files.readAllBytes(current);
            AbilityDefinitionJsonRepository.SaveResult conflict = repository.update(
                    withDisplayName(base, "Stale", 1), 1);
            assert conflict.conflict() : conflict;
            assert AbilityPersistenceError.CONFLICT.equals(conflict.error().code());
            assert Arrays.equals(beforeConflict, Files.readAllBytes(current));

            AbilityDefinitionJsonRepository.SaveResult rolledBack = repository.rollback(SLAM_ID, 1, 2);
            assert rolledBack.success() : rolledBack.error();
            assert rolledBack.definition().revision() == 3;
            assert rolledBack.definition().displayName().equals("Breakwater Slam");
            Path historyTwo = root.resolve(".history/abilities/projects/ability/grohm/slam/2.json");
            assert Files.isRegularFile(historyTwo, LinkOption.NOFOLLOW_LINKS);
            assert Arrays.equals(beforeConflict, Files.readAllBytes(historyTwo));
            assert repository.history(SLAM_ID).equals(List.of(1L, 2L));
            assert repository.load(SLAM_ID).definition().revision() == 3;
        } finally {
            repository.close();
            assert repository.load(SLAM_ID).status() == AbilityDefinitionJsonRepository.LoadStatus.CLOSED;
            deleteTree(root);
        }
    }

    private static void listPathAndSymlinkSafety() throws IOException {
        AbilityDefinition base = withRevision(GrohmBossContentFixture.slam(), 0);
        Path root = Files.createTempDirectory("projects-ability-list-");
        try {
            AbilityDefinitionJsonRepository repository = new AbilityDefinitionJsonRepository(root);
            try {
                assert repository.create(base).success();
                Path malformed = root.resolve("abilities/projects/ability/bad.json");
                Files.createDirectories(malformed.getParent());
                Files.writeString(malformed, "{\"format\":\"projects-content\"}\n",
                        StandardCharsets.UTF_8);
                List<AbilityDefinitionJsonRepository.LoadResult> listed = repository.list();
                assert listed.size() == 2 : listed;
                assert listed.stream().anyMatch(AbilityDefinitionJsonRepository.LoadResult::success);
                assert listed.stream().anyMatch(result -> !result.success()
                        && AbilityPersistenceError.MISSING_VALUE.equals(result.error().code()));
                assert listed.get(0).id().compareTo(listed.get(1).id()) <= 0;

                AbilityDefinition dotted = withRevision(base, 0, "projects:ability/./escape");
                AbilityDefinitionJsonRepository.SaveResult dottedResult = repository.create(dotted);
                assert !dottedResult.success();
                assert AbilityPersistenceError.UNSAFE_PATH.equals(dottedResult.error().code());
                assert !Files.exists(root.resolve("escape.json"));
            } finally {
                repository.close();
            }

            Path outside = Files.createTempDirectory("projects-ability-outside-");
            try {
                Path linkedRoot = Files.createTempDirectory("projects-ability-root-link-");
                try {
                    Files.createSymbolicLink(linkedRoot.resolve("root-link"), outside);
                    AbilityDefinitionJsonRepository linked = new AbilityDefinitionJsonRepository(
                            linkedRoot.resolve("root-link"));
                    assert linked.load(SLAM_ID).status()
                            == AbilityDefinitionJsonRepository.LoadStatus.UNSAFE_PATH;
                } finally {
                    deleteTree(linkedRoot);
                }
            } finally {
                deleteTree(outside);
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void concurrentUpdatesSerialize() throws Exception {
        Path root = Files.createTempDirectory("projects-ability-race-");
        AbilityDefinition base = withRevision(GrohmBossContentFixture.slam(), 0);
        AbilityDefinitionJsonRepository first = new AbilityDefinitionJsonRepository(root);
        AbilityDefinitionJsonRepository second = new AbilityDefinitionJsonRepository(
                root.resolve("nested").resolve(".."));
        try {
            assert first.create(base).success();
            AbilityDefinition draft = withDisplayName(base, "Concurrent", 1);
            List<AbilityDefinitionJsonRepository.SaveResult> results = runConcurrent(
                    () -> first.update(draft, 1), () -> second.update(draft, 1));
            assert results.stream().filter(AbilityDefinitionJsonRepository.SaveResult::success)
                    .count() == 1 : results;
            assert results.stream().anyMatch(result -> result.conflict()
                    || AbilityPersistenceError.LOCK_UNAVAILABLE.equals(
                    result.error() == null ? null : result.error().code())) : results;
            assert first.load(SLAM_ID).definition().revision() == 2;
            assert first.history(SLAM_ID).equals(List.of(1L));
        } finally {
            first.close();
            second.close();
            deleteTree(root);
        }
    }

    private static void heldOsLockReturnsBoundedContention() throws Exception {
        Path root = Files.createTempDirectory("projects-ability-held-lock-");
        AbilityDefinition base = withRevision(GrohmBossContentFixture.slam(), 0);
        AbilityDefinitionJsonRepository repository = new AbilityDefinitionJsonRepository(root);
        try {
            assert repository.create(base).success();
            Path target = root.resolve("abilities/projects/ability/grohm/slam.json");
            byte[] before = Files.readAllBytes(target);
            Path lockPath = root.resolve(AbilityDefinitionJsonRepository.LOCK_FILE_NAME);
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.READ, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
                 FileLock ignored = channel.lock()) {
                long started = System.nanoTime();
                AbilityDefinitionJsonRepository.SaveResult result = repository.update(
                        withDisplayName(base, "Blocked", 1), 1);
                long elapsed = System.nanoTime() - started;
                assert !result.success() : result;
                assert AbilityPersistenceError.LOCK_UNAVAILABLE.equals(result.error().code());
                assert elapsed < 2_000_000_000L : elapsed;
                assert Arrays.equals(before, Files.readAllBytes(target));
                assert repository.history(SLAM_ID).isEmpty();
            }
        } finally {
            repository.close();
            deleteTree(root);
        }
    }

    private static void processHeldOsLockReturnsBoundedContention() throws Exception {
        Path root = Files.createTempDirectory("projects-ability-process-lock-");
        Path ready = root.resolve("child.ready");
        Process child = null;
        AbilityDefinition base = withRevision(GrohmBossContentFixture.slam(), 0);
        AbilityDefinitionJsonRepository repository = new AbilityDefinitionJsonRepository(root);
        try {
            assert repository.create(base).success();
            Path target = root.resolve("abilities/projects/ability/grohm/slam.json");
            byte[] before = Files.readAllBytes(target);
            child = startLockHolder(root, ready);
            awaitReady(child, ready);
            long started = System.nanoTime();
            AbilityDefinitionJsonRepository.SaveResult result = repository.update(
                    withDisplayName(base, "Blocked By Process", 1), 1);
            long elapsed = System.nanoTime() - started;
            assert !result.success() : result;
            assert AbilityPersistenceError.LOCK_UNAVAILABLE.equals(result.error().code());
            assert elapsed < 2_000_000_000L : elapsed;
            assert Arrays.equals(before, Files.readAllBytes(target));
            assert repository.history(SLAM_ID).isEmpty();
        } finally {
            repository.close();
            stopProcess(child);
            deleteTree(root);
        }
    }

    private static void decodedAbilitiesFitGrohmCatalog() {
        AbilityDefinitionJsonCodec codec = new AbilityDefinitionJsonCodec();
        List<AbilityDefinition> decoded = List.of(SLAM_ID, CHARGE_ID, SHOCKWAVE_ID).stream()
                .map(id -> switch (id) {
                    case SLAM_ID -> GrohmBossContentFixture.slam();
                    case CHARGE_ID -> GrohmBossContentFixture.charge();
                    default -> GrohmBossContentFixture.shockwave();
                })
                .map(definition -> codec.decode(codec.encode(definition).bytes()).definition())
                .toList();
        ContentDefinitionValidator.Catalog catalog = new ContentDefinitionValidator.Catalog(
                List.of(GrohmBossContentFixture.mob()), decoded,
                List.of(GrohmBossContentFixture.encounter()),
                GrohmBossContentFixture.catalog().visuals(),
                GrohmBossContentFixture.catalog().rewardReferences(),
                GrohmBossContentFixture.catalog().equipmentIds(),
                GrohmBossContentFixture.catalog().validEntityTypeIds());
        ContentDefinitionValidator.ValidationResult result =
                new ContentDefinitionValidator().validate(catalog);
        assert result.valid() : result.issues();
    }

    private static AbilityDefinition enumCoverage() {
        EnumMap<DamageElement, Double> values = new EnumMap<>(DamageElement.class);
        values.put(DamageElement.FIRE, 1.0);
        values.put(DamageElement.ICE, 2.0);
        values.put(DamageElement.LIGHTNING, 3.0);
        EnumMap<DamageElement, Double> rates = new EnumMap<>(DamageElement.class);
        rates.put(DamageElement.FIRE, 0.25);
        rates.put(DamageElement.ICE, 0.5);
        rates.put(DamageElement.LIGHTNING, 0.75);
        AttackMetadata allMetadata = new AttackMetadata(EnumSet.allOf(AttackTag.class),
                new ElementProfile(values, rates));
        return new AbilityDefinition(
                1, "projects:ability/test/enum-coverage", 7, "Enum Coverage",
                new AbilityDefinition.Timing(1, 2, 3),
                new AbilityDefinition.Targeting(TargetSelector.SELF, 4.0),
                List.of(
                        new AbilityDefinition.Wait("wait-step", 1),
                        new AbilityDefinition.Telegraph("telegraph-step", TargetSelector.PRIMARY_TARGET,
                                new AbilityDefinition.Circle(1.0), 1, false),
                        new AbilityDefinition.Damage("normal-physical", TargetSelector.SELF,
                                new AbilityDefinition.Circle(1.0), DamageType.PHYSICAL,
                                DamageKind.NORMAL_ATTACK, 1.0, 0.0, true,
                                new AttackMetadata(Set.of(AttackTag.PHYSICAL, AttackTag.MELEE,
                                        AttackTag.NORMAL_ATTACK), null)),
                        new AbilityDefinition.Damage("direct-physical", TargetSelector.PRIMARY_TARGET,
                                new AbilityDefinition.Line(2.0, 1.0), DamageType.PHYSICAL,
                                DamageKind.DIRECT_SKILL, 2.0, 0.5, false,
                                new AttackMetadata(Set.of(AttackTag.PHYSICAL, AttackTag.SKILL,
                                        AttackTag.FIRE), new ElementProfile(
                                        Map.of(DamageElement.FIRE, 1.0),
                                        Map.of(DamageElement.FIRE, 0.25)))),
                        new AbilityDefinition.Damage("dot-magical", TargetSelector.SELF,
                                new AbilityDefinition.Donut(1.0, 2.0), DamageType.MAGICAL,
                                DamageKind.DAMAGE_OVER_TIME, 3.0, 0.0, false,
                                new AttackMetadata(Set.of(AttackTag.MAGIC, AttackTag.PROJECTILE,
                                        AttackTag.ICE), new ElementProfile(
                                        Map.of(DamageElement.ICE, 2.0),
                                        Map.of(DamageElement.ICE, 0.5)))),
                        new AbilityDefinition.Damage("reflected-true", TargetSelector.PRIMARY_TARGET,
                                new AbilityDefinition.Circle(2.0), DamageType.TRUE,
                                DamageKind.REFLECTED, 4.0, 0.0, false, allMetadata),
                        new AbilityDefinition.Damage("percent-true", TargetSelector.SELF,
                                new AbilityDefinition.Line(3.0, 1.0), DamageType.TRUE,
                                DamageKind.PERCENT_HEALTH, 5.0, 0.0, false,
                                new AttackMetadata(Set.of(AttackTag.LIGHTNING), null)),
                        new AbilityDefinition.Charge("charge-step", new AbilityDefinition.Line(4.0, 1.0),
                                TargetSelector.SELF, 2, 1.0),
                        new AbilityDefinition.Knockback("knockback-step",
                                new AbilityDefinition.Donut(1.0, 3.0), TargetSelector.PRIMARY_TARGET,
                                1.0, -1.0)),
                AbilityDefinition.InterruptPolicy.ALWAYS, null);
    }

    private static List<AbilityDefinitionJsonRepository.SaveResult> runConcurrent(
            SaveOperation firstOperation, SaveOperation secondOperation) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<AbilityDefinitionJsonRepository.SaveResult> firstResult =
                new AtomicReference<>();
        AtomicReference<AbilityDefinitionJsonRepository.SaveResult> secondResult =
                new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = concurrentThread(start, firstOperation, firstResult, failure, "ability-update-1");
        Thread second = concurrentThread(start, secondOperation, secondResult, failure, "ability-update-2");
        first.start();
        second.start();
        start.countDown();
        joinThread(first);
        joinThread(second);
        if (failure.get() != null) throw new AssertionError("concurrent operation failed", failure.get());
        assert firstResult.get() != null;
        assert secondResult.get() != null;
        return List.of(firstResult.get(), secondResult.get());
    }

    private static Thread concurrentThread(CountDownLatch start, SaveOperation operation,
                                           AtomicReference<AbilityDefinitionJsonRepository.SaveResult> result,
                                           AtomicReference<Throwable> failure, String name) {
        Thread thread = new Thread(() -> {
            try {
                start.await();
                result.set(operation.run());
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void joinThread(Thread thread) throws InterruptedException {
        thread.join(2_000);
        if (thread.isAlive()) {
            thread.interrupt();
            thread.join(1_000);
        }
        assert !thread.isAlive() : "concurrent test thread survived: " + thread.getName();
    }

    private static Process startLockHolder(Path root, Path ready) throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        ProcessBuilder builder = new ProcessBuilder(java.toString(), "-cp",
                System.getProperty("java.class.path"), AbilityContentPersistenceTest.class.getName(),
                "--hold-lock", root.toString(), ready.toString());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    private static void awaitReady(Process child, Path ready) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!Files.exists(ready, LinkOption.NOFOLLOW_LINKS)) {
            if (!child.isAlive()) throw new AssertionError("lock-holder exited before acquiring lock");
            if (System.nanoTime() >= deadline) throw new AssertionError("timed out waiting for lock-holder");
            Thread.sleep(10);
        }
    }

    private static void stopProcess(Process process) throws Exception {
        if (process == null) return;
        if (process.isAlive()) process.destroy();
        if (!process.waitFor(2, TimeUnit.SECONDS) && process.isAlive()) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        }
        assert !process.isAlive() : "lock-holder process survived cleanup";
    }

    private static void holdLockForProcess(Path root, Path ready) throws Exception {
        Files.createDirectories(root);
        Path lockPath = root.resolve(AbilityDefinitionJsonRepository.LOCK_FILE_NAME);
        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock ignored = channel.lock()) {
            Files.writeString(ready, "ready\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            while (true) Thread.sleep(1_000);
        }
    }

    private static String canonical(AbilityDefinitionJsonCodec codec, AbilityDefinition definition) {
        AbilityDefinitionJsonCodec.EncodeResult result = codec.encode(definition);
        assert result.success() : result.error();
        return new String(result.bytes(), StandardCharsets.UTF_8);
    }

    private static void reject(AbilityDefinitionJsonCodec codec, String json,
                               String code, String path) {
        AbilityDefinitionJsonCodec.DecodeResult result = codec.decode(
                json.getBytes(StandardCharsets.UTF_8));
        if (code == null) {
            assert result.success() : result.error();
            return;
        }
        assert !result.success() : "document was accepted: " + json;
        assert code.equals(result.error().code()) : result.error();
        assert path.equals(result.error().path()) : result.error();
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new AssertionError("SHA-256 is unavailable", exception);
        }
    }

    private static AbilityDefinition withRevision(AbilityDefinition source, long revision) {
        return withRevision(source, revision, source.abilityId());
    }

    private static AbilityDefinition withRevision(AbilityDefinition source, long revision,
                                                  String abilityId) {
        return new AbilityDefinition(1, abilityId, revision, source.displayName(), source.timing(),
                source.targeting(), source.timeline(), source.interruptPolicy(),
                source.visualReference());
    }

    private static AbilityDefinition withDisplayName(AbilityDefinition source, String name,
                                                      long revision) {
        return new AbilityDefinition(1, source.abilityId(), revision, name, source.timing(),
                source.targeting(), source.timeline(), source.interruptPolicy(),
                source.visualReference());
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
    }

    @FunctionalInterface
    private interface SaveOperation {
        AbilityDefinitionJsonRepository.SaveResult run();
    }
}
