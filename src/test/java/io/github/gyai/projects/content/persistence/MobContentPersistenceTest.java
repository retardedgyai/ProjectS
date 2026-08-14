package io.github.gyai.projects.content.persistence;

import io.github.gyai.projects.combat.damage.DamageElement;
import io.github.gyai.projects.content.definition.GrohmBossContentFixture;
import io.github.gyai.projects.content.definition.MobDefinition;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Assertion-main coverage for the Mob JSON persistence foundation. */
public final class MobContentPersistenceTest {
    private static final String MOB_ID = GrohmBossContentFixture.MOB_ID;

    private MobContentPersistenceTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--hold-lock".equals(args[0])) {
            holdLockForProcess(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        canonicalGrohmRoundTrip();
        publicResultsAndLayoutRemainStable();
        deterministicMapsAndEmptyCollections();
        escapingAndDocumentSize();
        strictDocumentRejections();
        repositoryRevisionHistoryAndRollback();
        failedWritePreservesTarget();
        pathAndSymlinkSafety();
        listIsolatesMalformedDocuments();
        concurrentUpdatesSerializePerNormalizedRoot();
        concurrentUpdateAndRollbackPreserveHistory();
        heldOsLockReturnsBoundedContention();
        processHeldOsLockReturnsBoundedContention();
        lockPathSafety();
        sharedStoreSupportsAnotherTypedLayout();
        System.out.println("Mob content persistence tests passed");
    }

    private static void canonicalGrohmRoundTrip() {
        MobDefinitionJsonCodec codec = new MobDefinitionJsonCodec();
        MobDefinition fixture = GrohmBossContentFixture.mob();
        MobDefinitionJsonCodec.EncodeResult encoded = codec.encode(fixture);
        assert encoded.success() : encoded.error();
        String expected = "{\"format\":\"projects-content\",\"schemaVersion\":1,"
                + "\"kind\":\"mob\",\"id\":\"projects:mob/grohm\",\"revision\":1,"
                + "\"definition\":{\"presentation\":{\"displayName\":\"Grohm\","
                + "\"nameplatePolicy\":\"boss_bar\"},\"entityType\":\"minecraft:ravager\","
                + "\"category\":\"boss\",\"stats\":{\"maxHealth\":1200.0,"
                + "\"attackDamage\":24.0,\"movementSpeed\":0.35,"
                + "\"knockbackResistance\":1.0,\"followRange\":32.0,\"scale\":1.0},"
                + "\"elementValues\":{},\"resistanceValues\":{},"
                + "\"equipmentReferences\":[\"projects:equipment/grohm/anchor\"],"
                + "\"abilityReferences\":[\"projects:ability/grohm/slam\","
                + "\"projects:ability/grohm/charge\",\"projects:ability/grohm/shockwave\"]}}\n";
        assert new String(encoded.bytes(), StandardCharsets.UTF_8).equals(expected)
                : new String(encoded.bytes(), StandardCharsets.UTF_8);
        assert sha256(encoded.bytes())
                .equals("b45b835109e56634c0e7219665aa9015ad8c72c673f7c27da805317d43ce3986");

        MobDefinitionJsonCodec.DecodeResult decoded = codec.decode(encoded.bytes());
        assert decoded.success() : decoded.error();
        assert decoded.definition().equals(fixture) : decoded.definition();
        MobDefinitionJsonCodec.EncodeResult reencoded = codec.encode(decoded.definition());
        assert reencoded.success() : reencoded.error();
        assert Arrays.equals(encoded.bytes(), reencoded.bytes());
    }

    private static void publicResultsAndLayoutRemainStable() throws IOException {
        Path root = Files.createTempDirectory("projects-mob-characterization-");
        MobDefinitionJsonRepository repository = new MobDefinitionJsonRepository(root);
        try {
            MobDefinition base = withRevision(GrohmBossContentFixture.mob(), 0);
            MobDefinitionJsonRepository.SaveResult invalidCreate = repository.create(base, 1);
            assert invalidCreate.status() == MobDefinitionJsonRepository.SaveStatus.REJECTED;
            assert MobPersistenceError.INVALID_BASE_REVISION.equals(invalidCreate.error().code());

            MobDefinitionJsonRepository.SaveResult created = repository.save(base, 0);
            assert created.status() == MobDefinitionJsonRepository.SaveStatus.SAVED;
            assert created.currentRevision() == 1;
            Path current = root.resolve("mobs/projects/mob/grohm.json");
            Path lock = root.resolve(MobDefinitionJsonRepository.LOCK_FILE_NAME);
            assert current.equals(repository.load(MOB_ID).path());
            assert Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS);
            assert Files.isRegularFile(lock, LinkOption.NOFOLLOW_LINKS);

            MobDefinitionJsonRepository.SaveResult duplicate = repository.create(base, 0);
            assert duplicate.status() == MobDefinitionJsonRepository.SaveStatus.TARGET_EXISTS;
            assert MobPersistenceError.TARGET_EXISTS.equals(duplicate.error().code());

            MobDefinitionJsonRepository.SaveResult updated = repository.save(
                    withPresentation(base, "Characterized", 1), 1);
            assert updated.status() == MobDefinitionJsonRepository.SaveStatus.SAVED;
            assert updated.currentRevision() == 2;
            Path history = root.resolve(".history/mobs/projects/mob/grohm/1.json");
            assert Files.isRegularFile(history, LinkOption.NOFOLLOW_LINKS);
            assert repository.history(MOB_ID).equals(List.of(1L));
        } finally {
            repository.close();
            assert repository.load(MOB_ID).status()
                    == MobDefinitionJsonRepository.LoadStatus.CLOSED;
            deleteTree(root);
        }
    }

    private static void deterministicMapsAndEmptyCollections() {
        MobDefinitionJsonCodec codec = new MobDefinitionJsonCodec();
        MobDefinition source = GrohmBossContentFixture.mob();
        Map<DamageElement, Double> first = new LinkedHashMap<>();
        first.put(DamageElement.LIGHTNING, 3.0);
        first.put(DamageElement.FIRE, 1.0);
        Map<DamageElement, Double> second = new LinkedHashMap<>();
        second.put(DamageElement.FIRE, 1.0);
        second.put(DamageElement.LIGHTNING, 3.0);
        MobDefinition left = new MobDefinition(1, "projects:mob/maps", 1,
                source.presentation(), source.entityType(), source.category(), source.stats(),
                first, Map.of(DamageElement.ICE, 0.5), List.of(), List.of());
        MobDefinition right = new MobDefinition(1, "projects:mob/maps", 1,
                source.presentation(), source.entityType(), source.category(), source.stats(),
                second, Map.of(DamageElement.ICE, 0.5), null, null);
        MobDefinitionJsonCodec.EncodeResult leftBytes = codec.encode(left);
        MobDefinitionJsonCodec.EncodeResult rightBytes = codec.encode(right);
        assert leftBytes.success() : leftBytes.error();
        assert rightBytes.success() : rightBytes.error();
        assert Arrays.equals(leftBytes.bytes(), rightBytes.bytes());
        String json = new String(leftBytes.bytes(), StandardCharsets.UTF_8);
        assert json.contains("\"fire\":1.0,\"lightning\":3.0");
        assert json.contains("\"equipmentReferences\":[]");
        assert json.contains("\"abilityReferences\":[]");
    }

    private static void escapingAndDocumentSize() {
        MobDefinition source = GrohmBossContentFixture.mob();
        MobDefinition escaped = new MobDefinition(1, "projects:mob/escaped", 1,
                new MobDefinition.Presentation("Quote \"line\\\n", "default"),
                source.entityType(), source.category(), source.stats(), source.elementValues(),
                source.resistanceValues(), List.of(), List.of());
        MobDefinitionJsonCodec codec = new MobDefinitionJsonCodec();
        MobDefinitionJsonCodec.EncodeResult encoded = codec.encode(escaped);
        assert encoded.success() : encoded.error();
        String json = new String(encoded.bytes(), StandardCharsets.UTF_8);
        assert json.contains("Quote \\\"line\\\\\\n");
        MobDefinitionJsonCodec.DecodeResult decoded = codec.decode(encoded.bytes());
        assert decoded.success() : decoded.error();
        assert decoded.definition().equals(escaped);

        byte[] oversized = (canonical(codec, source) + " ".repeat(
                MobDefinitionJsonCodec.MAX_DOCUMENT_BYTES)).getBytes(StandardCharsets.UTF_8);
        reject(codec, oversized, MobPersistenceError.DOCUMENT_TOO_LARGE, "$");
    }

    private static void strictDocumentRejections() {
        MobDefinitionJsonCodec codec = new MobDefinitionJsonCodec();
        String canonical = canonical(codec, GrohmBossContentFixture.mob());
        reject(codec, canonical.replace("\"format\":\"projects-content\"",
                "\"format\":\"projects-content\",\"format\":\"projects-content\""),
                MobPersistenceError.DUPLICATE_KEY, "$.format");
        reject(codec, canonical.replace("\"displayName\":\"Grohm\"",
                "\"displayName\":\"Grohm\",\"displayName\":\"Grohm\""),
                MobPersistenceError.DUPLICATE_KEY, "$.definition.presentation.displayName");
        reject(codec, canonical.replace(",\"definition\":{",
                ",\"unknown\":0,\"definition\":{"),
                MobPersistenceError.UNKNOWN_KEY, "$.unknown");
        reject(codec, canonical.replace("\"entityType\":\"minecraft:ravager\"",
                "\"entityType\":\"minecraft:ravager\",\"unknown\":0"),
                MobPersistenceError.UNKNOWN_KEY, "$.definition.unknown");
        reject(codec, canonical + "{}", MobPersistenceError.TRAILING_DATA, "$");
        reject(codec, "/* comment */" + canonical, MobPersistenceError.INVALID_JSON, "$");
        reject(codec, canonical.replace("projects-content", "other-format"),
                MobPersistenceError.WRONG_FORMAT, "$.format");
        reject(codec, canonical.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                MobPersistenceError.UNSUPPORTED_SCHEMA, "$.schemaVersion");
        reject(codec, canonical.replace("\"kind\":\"mob\"", "\"kind\":\"ability\""),
                MobPersistenceError.WRONG_KIND, "$.kind");
        reject(codec, canonical.replace("\"revision\":1", "\"revision\":1.5"),
                MobPersistenceError.NON_INTEGRAL_NUMBER, "$.revision");
        reject(codec, canonical.replace("\"revision\":1", "\"revision\":9223372036854775808"),
                MobPersistenceError.REVISION_OVERFLOW, "$.revision");
        reject(codec, canonical.replace("\"maxHealth\":1200.0", "\"maxHealth\":NaN"),
                MobPersistenceError.NON_FINITE_NUMBER, "$.definition.stats.maxHealth");
        reject(codec, canonical.replace("\"maxHealth\":1200.0", "\"maxHealth\":Infinity"),
                MobPersistenceError.NON_FINITE_NUMBER, "$.definition.stats.maxHealth");
        reject(codec, canonical.replace("\"displayName\":\"Grohm\"",
                "\"displayName\":null"),
                MobPersistenceError.NULL_REQUIRED_FIELD,
                "$.definition.presentation.displayName");
        reject(codec, canonical.replace("\"category\":\"boss\"", "\"category\":\"dragon\""),
                MobPersistenceError.UNSUPPORTED_ENUM, "$.definition.category");
        byte[] bom = concat(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf},
                canonical.getBytes(StandardCharsets.UTF_8));
        reject(codec, bom, MobPersistenceError.BOM_REJECTED, "$");
        reject(codec, new byte[]{'{', (byte) 0xc3, '('}, MobPersistenceError.INVALID_UTF8, "$");
        reject(codec, "[".repeat(33) + "0" + "]".repeat(33),
                MobPersistenceError.NESTING_TOO_DEEP, "$" + "[0]".repeat(32));
        String manyValues = "[" + String.join(",", java.util.Collections.nCopies(4_097, "0")) + "]";
        reject(codec, manyValues, MobPersistenceError.COLLECTION_TOO_LARGE, "$");
        reject(codec, "\"" + "a".repeat(8_193) + "\"",
                MobPersistenceError.STRING_TOO_LONG, "$");
        reject(codec, canonical.replace(
                "\"abilityReferences\":[\"projects:ability/grohm/slam\","
                        + "\"projects:ability/grohm/charge\",\"projects:ability/grohm/shockwave\"]",
                "\"abilityReferences\":null"),
                MobPersistenceError.NULL_REQUIRED_FIELD, "$.definition.abilityReferences");
    }

    private static void repositoryRevisionHistoryAndRollback() throws IOException {
        Path root = Files.createTempDirectory("projects-mob-json-");
        try {
            MobDefinitionJsonRepository repository = new MobDefinitionJsonRepository(root);
            MobDefinition base = withRevision(GrohmBossContentFixture.mob(), 0);
            MobDefinitionJsonRepository.SaveResult created = repository.create(base, 0);
            assert created.success() : created.error();
            assert created.definition().revision() == 1;
            Path target = root.resolve("mobs/projects/mob/grohm.json");
            byte[] revisionOne = Files.readAllBytes(target);

            MobDefinition changed = withPresentation(base, "Grohm Updated", 1);
            MobDefinitionJsonRepository.SaveResult updated = repository.update(changed, 1);
            assert updated.success() : updated.error();
            assert updated.definition().revision() == 2;
            byte[] revisionTwo = Files.readAllBytes(target);
            Path historyOne = root.resolve(".history/mobs/projects/mob/grohm/1.json");
            assert Files.exists(historyOne);
            assert Arrays.equals(revisionOne, Files.readAllBytes(historyOne));

            byte[] beforeConflict = Files.readAllBytes(target);
            List<Long> historyBeforeConflict = repository.history(MOB_ID);
            MobDefinition stale = withPresentation(base, "Stale", 1);
            MobDefinitionJsonRepository.SaveResult conflict = repository.update(stale, 1);
            assert conflict.conflict() : conflict;
            assert MobPersistenceError.CONFLICT.equals(conflict.error().code());
            assert Arrays.equals(beforeConflict, Files.readAllBytes(target));
            assert repository.history(MOB_ID).equals(historyBeforeConflict);

            MobDefinitionJsonRepository.SaveResult rolledBack = repository.rollback(MOB_ID, 1, 2);
            assert rolledBack.success() : rolledBack.error();
            assert rolledBack.definition().revision() == 3;
            assert rolledBack.definition().presentation().displayName().equals("Grohm");
            Path historyTwo = root.resolve(".history/mobs/projects/mob/grohm/2.json");
            assert Files.exists(historyTwo);
            assert Arrays.equals(revisionTwo, Files.readAllBytes(historyTwo));
            assert repository.history(MOB_ID).equals(List.of(1L, 2L));
            assert repository.load(MOB_ID).definition().revision() == 3;
        } finally {
            deleteTree(root);
        }
    }

    private static void failedWritePreservesTarget() throws IOException {
        Path root = Files.createTempDirectory("projects-mob-failure-");
        try {
            MobDefinition base = withRevision(GrohmBossContentFixture.mob(), 0);
            MobDefinitionJsonRepository normal = new MobDefinitionJsonRepository(root);
            assert normal.create(base).success();
            Path target = root.resolve("mobs/projects/mob/grohm.json");
            byte[] before = Files.readAllBytes(target);
            MobDefinitionJsonRepository failing = new MobDefinitionJsonRepository(root,
                    new MobDefinitionJsonCodec(), (path, bytes, replace) -> {
                        throw new IOException("induced write failure");
                    });
            MobDefinitionJsonRepository.SaveResult result = failing.update(
                    withPresentation(base, "Should Not Commit", 1), 1);
            assert !result.success();
            assert MobPersistenceError.IO_FAILURE.equals(result.error().code());
            assert Arrays.equals(before, Files.readAllBytes(target));
            try (Stream<Path> paths = Files.list(target.getParent())) {
                assert paths.noneMatch(path -> path.getFileName().toString().endsWith(".tmp"));
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void pathAndSymlinkSafety() throws IOException {
        MobDefinitionJsonCodec codec = new MobDefinitionJsonCodec();
        Path root = Files.createTempDirectory("projects-mob-path-");
        try {
            MobDefinition base = withRevision(GrohmBossContentFixture.mob(), 0);
            MobDefinition dotted = withRevision(base, 0, "projects:mob/./escape");
            MobDefinitionJsonRepository repository = new MobDefinitionJsonRepository(root);
            MobDefinitionJsonRepository.SaveResult dottedResult = repository.create(dotted);
            assert !dottedResult.success();
            assert MobPersistenceError.UNSAFE_PATH.equals(dottedResult.error().code());
            assert !Files.exists(root.resolve("escape.json"));
            MobDefinition traversal = withRevision(base, 0, "projects:mob/../escape");
            MobDefinitionJsonRepository.SaveResult traversalResult = repository.create(traversal);
            assert !traversalResult.success();
            assert MobPersistenceError.INVALID_NAMESPACED_ID.equals(traversalResult.error().code());

            Path outside = Files.createTempDirectory("projects-mob-outside-");
            try {
                Path symlinkRoot = Files.createTempDirectory("projects-mob-root-link-");
                try {
                    Files.createSymbolicLink(symlinkRoot.resolve("root-link"), outside);
                    MobDefinitionJsonRepository rootLinkRepository =
                            new MobDefinitionJsonRepository(symlinkRoot.resolve("root-link"));
                    MobDefinitionJsonRepository.LoadResult rootLink = rootLinkRepository.load(MOB_ID);
                    assert rootLink.status() == MobDefinitionJsonRepository.LoadStatus.UNSAFE_PATH;
                } finally {
                    deleteTree(symlinkRoot);
                }

                Path kindRoot = Files.createTempDirectory("projects-mob-kind-link-");
                try {
                    Files.createSymbolicLink(kindRoot.resolve("mobs"), outside);
                    MobDefinitionJsonRepository kindLinkRepository =
                            new MobDefinitionJsonRepository(kindRoot);
                    assert kindLinkRepository.load(MOB_ID).status()
                            == MobDefinitionJsonRepository.LoadStatus.UNSAFE_PATH;
                } finally {
                    deleteTree(kindRoot);
                }

                Path parentRoot = Files.createTempDirectory("projects-mob-parent-link-");
                try {
                    Files.createDirectories(parentRoot.resolve("mobs"));
                    Files.createSymbolicLink(parentRoot.resolve("mobs/projects"), outside);
                    MobDefinitionJsonRepository parentLinkRepository =
                            new MobDefinitionJsonRepository(parentRoot);
                    assert parentLinkRepository.load(MOB_ID).status()
                            == MobDefinitionJsonRepository.LoadStatus.UNSAFE_PATH;
                } finally {
                    deleteTree(parentRoot);
                }

                Path fileRoot = Files.createTempDirectory("projects-mob-file-link-");
                try {
                    MobDefinitionJsonRepository normal = new MobDefinitionJsonRepository(fileRoot);
                    assert normal.create(base).success();
                    Path target = fileRoot.resolve("mobs/projects/mob/grohm.json");
                    byte[] bytes = codec.encode(withRevision(base, 1)).bytes();
                    Path linkedTarget = outside.resolve("linked.json");
                    Files.write(linkedTarget, bytes);
                    Files.delete(target);
                    Files.createSymbolicLink(target, linkedTarget);
                    assert normal.load(MOB_ID).status()
                            == MobDefinitionJsonRepository.LoadStatus.UNSAFE_PATH;
                } finally {
                    deleteTree(fileRoot);
                }
            } finally {
                deleteTree(outside);
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void listIsolatesMalformedDocuments() throws IOException {
        Path root = Files.createTempDirectory("projects-mob-list-");
        try {
            MobDefinitionJsonRepository repository = new MobDefinitionJsonRepository(root);
            MobDefinition base = withRevision(GrohmBossContentFixture.mob(), 0);
            assert repository.create(base).success();
            Path malformed = root.resolve("mobs/projects/mob/bad.json");
            Files.createDirectories(malformed.getParent());
            Files.writeString(malformed, "{\"format\":\"projects-content\"}\n",
                    StandardCharsets.UTF_8);
            List<MobDefinitionJsonRepository.LoadResult> listed = repository.list();
            assert listed.size() == 2 : listed;
            assert listed.stream().anyMatch(MobDefinitionJsonRepository.LoadResult::success);
            assert listed.stream().anyMatch(result -> !result.success()
                    && MobPersistenceError.MISSING_VALUE.equals(result.error().code()));
            assert listed.get(0).id().compareTo(listed.get(1).id()) <= 0;
        } finally {
            deleteTree(root);
        }
    }

    private static void concurrentUpdatesSerializePerNormalizedRoot() throws Exception {
        Path root = Files.createTempDirectory("projects-mob-race-");
        try {
            MobDefinition base = withRevision(GrohmBossContentFixture.mob(), 0);
            Path equivalentRoot = root.resolve("nested").resolve("..");
            MobDefinitionJsonRepository first = new MobDefinitionJsonRepository(root);
            MobDefinitionJsonRepository second = new MobDefinitionJsonRepository(equivalentRoot);
            try {
                assert first.create(base).success();
                MobDefinition draft = withPresentation(base, "Concurrent", 1);
                List<MobDefinitionJsonRepository.SaveResult> results = runConcurrent(
                        () -> first.update(draft, 1), () -> second.update(draft, 1));
                long saved = results.stream()
                        .filter(MobDefinitionJsonRepository.SaveResult::success)
                        .count();
                assert saved == 1 : results;
                assert results.stream().anyMatch(result -> result.conflict()
                        || MobPersistenceError.LOCK_UNAVAILABLE.equals(
                        result.error() == null ? null : result.error().code())) : results;

                MobDefinitionJsonRepository.LoadResult current = first.load(MOB_ID);
                assert current.success() : current.error();
                assert current.definition().revision() == 2 : current.definition();
                assert first.history(MOB_ID).equals(List.of(1L)) : first.history(MOB_ID);
                Path history = root.resolve(".history/mobs/projects/mob/grohm/1.json");
                assert Files.isRegularFile(history, LinkOption.NOFOLLOW_LINKS);
                assert Arrays.equals(codecBytes(base, 1), Files.readAllBytes(history));
            } finally {
                first.close();
                second.close();
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void concurrentUpdateAndRollbackPreserveHistory() throws Exception {
        Path root = Files.createTempDirectory("projects-mob-update-rollback-");
        try {
            MobDefinition base = withRevision(GrohmBossContentFixture.mob(), 0);
            MobDefinitionJsonRepository first = new MobDefinitionJsonRepository(root);
            MobDefinitionJsonRepository second = new MobDefinitionJsonRepository(
                    root.resolve("nested").resolve(".."));
            try {
                assert first.create(base).success();
                MobDefinition revisionTwo = withPresentation(base, "Revision Two", 1);
                assert first.update(revisionTwo, 1).success();
                Path target = root.resolve("mobs/projects/mob/grohm.json");
                byte[] revisionTwoBytes = Files.readAllBytes(target);

                MobDefinition updateDraft = withPresentation(revisionTwo, "Concurrent Update", 2);
                List<MobDefinitionJsonRepository.SaveResult> results = runConcurrent(
                        () -> first.update(updateDraft, 2),
                        () -> second.rollback(MOB_ID, 1, 2));
                long saved = results.stream()
                        .filter(MobDefinitionJsonRepository.SaveResult::success)
                        .count();
                assert saved == 1 : results;
                assert results.stream().anyMatch(result -> result.conflict()
                        || MobPersistenceError.LOCK_UNAVAILABLE.equals(
                        result.error() == null ? null : result.error().code())) : results;

                MobDefinitionJsonRepository.LoadResult current = first.load(MOB_ID);
                assert current.success() : current.error();
                assert current.definition().revision() == 3 : current.definition();
                assert first.history(MOB_ID).equals(List.of(1L, 2L)) : first.history(MOB_ID);
                Path historyTwo = root.resolve(".history/mobs/projects/mob/grohm/2.json");
                assert Arrays.equals(revisionTwoBytes, Files.readAllBytes(historyTwo));
            } finally {
                first.close();
                second.close();
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void heldOsLockReturnsBoundedContention() throws Exception {
        Path root = Files.createTempDirectory("projects-mob-held-lock-");
        try {
            MobDefinition base = withRevision(GrohmBossContentFixture.mob(), 0);
            MobDefinitionJsonRepository repository = new MobDefinitionJsonRepository(root);
            try {
                assert repository.create(base).success();
                Path target = root.resolve("mobs/projects/mob/grohm.json");
                byte[] before = Files.readAllBytes(target);
                Path lockPath = root.resolve(MobDefinitionJsonRepository.LOCK_FILE_NAME);
                try (FileChannel channel = FileChannel.open(lockPath,
                        StandardOpenOption.READ, StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS);
                     FileLock ignored = channel.lock()) {
                    long started = System.nanoTime();
                    MobDefinitionJsonRepository.SaveResult result = repository.update(
                            withPresentation(base, "Blocked", 1), 1);
                    long elapsed = System.nanoTime() - started;
                    assert !result.success() : result;
                    assert MobPersistenceError.LOCK_UNAVAILABLE.equals(result.error().code())
                            : result.error();
                    assert elapsed < 2_000_000_000L : elapsed;
                    assert Arrays.equals(before, Files.readAllBytes(target));
                    assert repository.history(MOB_ID).isEmpty();
                }
            } finally {
                repository.close();
            }
        } finally {
            deleteTree(root);
        }
    }

    private static void processHeldOsLockReturnsBoundedContention() throws Exception {
        Path root = Files.createTempDirectory("projects-mob-process-lock-");
        Path ready = root.resolve("child.ready");
        Process child = null;
        try {
            MobDefinition base = withRevision(GrohmBossContentFixture.mob(), 0);
            MobDefinitionJsonRepository repository = new MobDefinitionJsonRepository(root);
            try {
                assert repository.create(base).success();
                Path target = root.resolve("mobs/projects/mob/grohm.json");
                byte[] before = Files.readAllBytes(target);
                child = startLockHolder(root, ready);
                awaitReady(child, ready);
                long started = System.nanoTime();
                MobDefinitionJsonRepository.SaveResult result = repository.update(
                        withPresentation(base, "Blocked By Process", 1), 1);
                long elapsed = System.nanoTime() - started;
                assert !result.success() : result;
                assert MobPersistenceError.LOCK_UNAVAILABLE.equals(result.error().code())
                        : result.error();
                assert elapsed < 2_000_000_000L : elapsed;
                assert Arrays.equals(before, Files.readAllBytes(target));
                assert repository.history(MOB_ID).isEmpty();
            } finally {
                repository.close();
            }
        } finally {
            stopProcess(child);
            deleteTree(root);
        }
    }

    private static void lockPathSafety() throws Exception {
        MobDefinition base = withRevision(GrohmBossContentFixture.mob(), 0);
        Path outside = Files.createTempDirectory("projects-mob-lock-outside-");
        Path symlinkRoot = Files.createTempDirectory("projects-mob-lock-link-");
        Path directoryRoot = Files.createTempDirectory("projects-mob-lock-directory-");
        try {
            Path linkedLock = outside.resolve("linked-lock");
            Files.writeString(linkedLock, "not a repository lock", StandardCharsets.UTF_8);
            Files.createSymbolicLink(symlinkRoot.resolve(
                    MobDefinitionJsonRepository.LOCK_FILE_NAME), linkedLock);
            MobDefinitionJsonRepository symlinkRepository =
                    new MobDefinitionJsonRepository(symlinkRoot);
            try {
                MobDefinitionJsonRepository.SaveResult result = symlinkRepository.create(base);
                assert !result.success() : result;
                assert MobPersistenceError.UNSAFE_PATH.equals(result.error().code())
                        : result.error();
                assert !Files.exists(symlinkRoot.resolve("mobs"), LinkOption.NOFOLLOW_LINKS);
            } finally {
                symlinkRepository.close();
            }

            Files.createDirectory(directoryRoot.resolve(MobDefinitionJsonRepository.LOCK_FILE_NAME));
            MobDefinitionJsonRepository directoryRepository =
                    new MobDefinitionJsonRepository(directoryRoot);
            try {
                MobDefinitionJsonRepository.SaveResult result = directoryRepository.create(base);
                assert !result.success() : result;
                assert MobPersistenceError.UNSAFE_PATH.equals(result.error().code())
                        : result.error();
                assert !Files.exists(directoryRoot.resolve("mobs"), LinkOption.NOFOLLOW_LINKS);
            } finally {
                directoryRepository.close();
            }
        } finally {
            deleteTree(symlinkRoot);
            deleteTree(directoryRoot);
            deleteTree(outside);
        }
    }

    private static void sharedStoreSupportsAnotherTypedLayout() throws Exception {
        Path root = Files.createTempDirectory("projects-shared-store-");
        RevisionedFileStore<ProbeDocument> store = new RevisionedFileStore<>(
                root, new ProbeLayout(), new ProbeCodec(), StrictJson.MAX_DOCUMENT_BYTES,
                RevisionedFileStore::writeAtomic);
        try {
            ProbeDocument document = new ProbeDocument("probe:item", 1);
            ProbeCodec codec = new ProbeCodec();
            byte[] encoded = probeBytes(document);
            try (RevisionedFileStore.MutationLock ignored = store.acquireMutationLock()) {
                store.commit(store.currentPath(document.id()), encoded, false);
                RevisionedFileStore.ReadResult<ProbeDocument> current =
                        store.readCurrent(document.id());
                assert current.status() == RevisionedFileStore.ReadStatus.LOADED;
                assert current.definition().equals(document);
                store.preserveHistory(document.id(), document.revision(), encoded);
                RevisionedFileStore.Historical<ProbeDocument> historical =
                        store.readHistory(document.id(), document.revision());
                assert historical.definition().equals(document);
                assert Arrays.equals(encoded, historical.bytes());
            }
            assert store.history(document.id()).equals(List.of(1L));
            assert Files.isRegularFile(root.resolve(".projects-probe-content.lock"),
                    LinkOption.NOFOLLOW_LINKS);
        } finally {
            store.close();
            deleteTree(root);
        }
    }

    private static List<MobDefinitionJsonRepository.SaveResult> runConcurrent(
            SaveOperation firstOperation, SaveOperation secondOperation) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<MobDefinitionJsonRepository.SaveResult> firstResult =
                new AtomicReference<>();
        AtomicReference<MobDefinitionJsonRepository.SaveResult> secondResult =
                new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = concurrentThread(start, firstOperation, firstResult, failure, "mob-update-1");
        Thread second = concurrentThread(start, secondOperation, secondResult, failure, "mob-update-2");
        first.start();
        second.start();
        start.countDown();
        joinThread(first);
        joinThread(second);
        if (failure.get() != null) throw new AssertionError("concurrent operation failed",
                failure.get());
        assert firstResult.get() != null;
        assert secondResult.get() != null;
        return List.of(firstResult.get(), secondResult.get());
    }

    private static Thread concurrentThread(CountDownLatch start, SaveOperation operation,
                                           AtomicReference<MobDefinitionJsonRepository.SaveResult> result,
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
                System.getProperty("java.class.path"), MobContentPersistenceTest.class.getName(),
                "--hold-lock", root.toString(), ready.toString());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    private static void awaitReady(Process child, Path ready) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!Files.exists(ready, LinkOption.NOFOLLOW_LINKS)) {
            if (!child.isAlive()) {
                throw new AssertionError("lock-holder process exited before acquiring lock");
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("timed out waiting for lock-holder process");
            }
            Thread.sleep(10);
        }
    }

    private static void stopProcess(Process process) throws Exception {
        if (process == null) return;
        if (process.isAlive()) process.destroy();
        if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) && process.isAlive()) {
            process.destroyForcibly();
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        }
        assert !process.isAlive() : "lock-holder process survived cleanup";
    }

    private static void holdLockForProcess(Path root, Path ready) throws Exception {
        Files.createDirectories(root);
        Path lockPath = root.resolve(MobDefinitionJsonRepository.LOCK_FILE_NAME);
        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.READ, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
             FileLock ignored = channel.lock()) {
            Files.writeString(ready, "ready\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            while (true) Thread.sleep(1_000);
        }
    }

    @FunctionalInterface
    private interface SaveOperation {
        MobDefinitionJsonRepository.SaveResult run();
    }

    private static byte[] codecBytes(MobDefinition definition, long revision) {
        MobDefinitionJsonCodec.EncodeResult result =
                new MobDefinitionJsonCodec().encode(withRevision(definition, revision));
        assert result.success() : result.error();
        return result.bytes();
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new AssertionError("SHA-256 is unavailable", exception);
        }
    }

    private static String canonical(MobDefinitionJsonCodec codec, MobDefinition definition) {
        MobDefinitionJsonCodec.EncodeResult result = codec.encode(definition);
        assert result.success() : result.error();
        return new String(result.bytes(), StandardCharsets.UTF_8);
    }

    private static void reject(MobDefinitionJsonCodec codec, String json,
                               String code, String path) {
        reject(codec, json.getBytes(StandardCharsets.UTF_8), code, path);
    }

    private static void reject(MobDefinitionJsonCodec codec, byte[] bytes,
                               String code, String path) {
        MobDefinitionJsonCodec.DecodeResult result = codec.decode(bytes);
        assert !result.success() : "document was accepted: " + new String(bytes, StandardCharsets.UTF_8);
        assert code.equals(result.error().code()) : result.error();
        assert path.equals(result.error().path()) : result.error();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static MobDefinition withRevision(MobDefinition source, long revision) {
        return withRevision(source, revision, source.mobId());
    }

    private static MobDefinition withRevision(MobDefinition source, long revision, String id) {
        return new MobDefinition(1, id, revision, source.presentation(), source.entityType(),
                source.category(), source.stats(), source.elementValues(),
                source.resistanceValues(), source.equipmentReferences(), source.abilityReferences());
    }

    private static MobDefinition withPresentation(MobDefinition source, String name,
                                                   long revision) {
        return new MobDefinition(1, source.mobId(), revision,
                new MobDefinition.Presentation(name, source.presentation().nameplatePolicy()),
                source.entityType(), source.category(), source.stats(), source.elementValues(),
                source.resistanceValues(), source.equipmentReferences(), source.abilityReferences());
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
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

    private record ProbeDocument(String id, long revision) {
    }

    private static byte[] probeBytes(ProbeDocument definition) {
        try {
            String json = "{\"id\":" + StrictJson.quote(definition.id())
                    + ",\"revision\":" + definition.revision() + "}\n";
            return StrictJson.encodeUtf8(json);
        } catch (Exception exception) {
            throw new AssertionError("probe value cannot be encoded", exception);
        }
    }

    private static final class ProbeCodec implements RevisionedFileStore.Codec<ProbeDocument> {
        @Override
        public RevisionedFileStore.Decoded<ProbeDocument> decode(byte[] bytes) {
            try {
                StrictJson.JsonObjectValue object =
                        (StrictJson.JsonObjectValue) StrictJson.parse(StrictJson.decodeUtf8(bytes));
                String id = ((StrictJson.JsonString) object.values().get("id")).value();
                long revision = ((StrictJson.JsonNumber) object.values().get("revision"))
                        .value().longValueExact();
                return new RevisionedFileStore.Decoded<>(new ProbeDocument(id, revision), null);
            } catch (Exception exception) {
                return new RevisionedFileStore.Decoded<>(null,
                        new RevisionedFileStore.StorageError("INVALID_JSON", "$",
                                "probe document is invalid"));
            }
        }

        @Override
        public String id(ProbeDocument definition) {
            return definition.id();
        }

        @Override
        public long revision(ProbeDocument definition) {
            return definition.revision();
        }
    }

    private static final class ProbeLayout implements RevisionedFileStore.Layout {
        private static final String ID = "probe:item";

        @Override
        public String kindName() {
            return "probes";
        }

        @Override
        public Path currentDirectory(Path root) {
            return root.resolve("probes");
        }

        @Override
        public Path historyDirectory(Path root) {
            return root.resolve(".history").resolve("probes");
        }

        @Override
        public Path lockPath(Path root) {
            return root.resolve(".projects-probe-content.lock");
        }

        @Override
        public Path currentPath(Path root, String id) {
            if (!ID.equals(id)) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", "$.id",
                        "probe ID is not supported");
            }
            return currentDirectory(root).resolve("probe").resolve("item.json");
        }

        @Override
        public Path historyPath(Path root, String id, long revision) {
            if (!ID.equals(id)) {
                throw RevisionedFileStore.failure("UNSAFE_PATH", "$.id",
                        "probe ID is not supported");
            }
            return historyDirectory(root).resolve("probe").resolve("item")
                    .resolve(revision + ".json");
        }

        @Override
        public String idFromCurrentPath(Path root, Path currentDirectory, Path candidate) {
            if (currentPath(root, ID).toAbsolutePath().normalize()
                    .equals(candidate.toAbsolutePath().normalize())) {
                return ID;
            }
            throw RevisionedFileStore.failure("UNSAFE_PATH", candidate,
                    "probe path is not canonical");
        }
    }
}
