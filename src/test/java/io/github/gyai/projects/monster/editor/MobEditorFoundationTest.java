package io.github.gyai.projects.monster.editor;

import io.github.gyai.projects.ability.AbilityRegistry;
import io.github.gyai.projects.ability.AbilityRuntime;
import io.github.gyai.projects.ability.DevAbilityDefinitions;
import io.github.gyai.projects.ability.MobAbilityAssignmentPolicy;
import io.github.gyai.projects.network.MobEditorChannel;
import io.github.gyai.projects.network.MobEditorPacketIO;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import sun.misc.Unsafe;

public final class MobEditorFoundationTest {
    private MobEditorFoundationTest() {
    }

    public static void main(String[] args) throws Exception {
        Set<String> living = Set.of("ZOMBIE", "SHEEP", "SLIME");
        MobDefinitionValidator validator = new MobDefinitionValidator(
                living,
                material -> Set.of("IRON_SWORD", "LEATHER_HELMET")
                        .contains(material),
                item -> item.equals("starter_sword"),
                head -> head.equals("pirate_head"));
        MobDefinition valid = MobDefinition.create("pirate_swordsman");
        assert validator.validate(valid).valid();
        assert valid.abilityIds().isEmpty();
        MobDefinition assigned = valid.withAbilityIds(List.of(
                "projects:arcane-burst", "projects:slow-wave"));
        assert assigned.abilityIds().equals(List.of(
                "projects:arcane-burst", "projects:slow-wave"));
        assert assigned.withRevision(7).abilityIds().equals(assigned.abilityIds());
        assertThrowsIllegal(() -> valid.withAbilityIds(null));
        assertThrowsIllegal(() -> valid.withAbilityIds(java.util.Arrays.asList(
                "projects:arcane-burst", null)));
        assertThrowsIllegal(() -> valid.withAbilityIds(List.of("bad")));
        assertThrowsIllegal(() -> valid.withAbilityIds(List.of(
                "projects:arcane-burst", "projects:arcane-burst")));
        assertThrowsIllegal(() -> valid.withAbilityIds(
                java.util.Collections.nCopies(
                        MobDefinition.MAX_ABILITY_IDS + 1, "projects:overflow")));

        assertInvalid(validator, copy(valid, "Bad ID", valid.stats(),
                valid.ai(), valid.appearance(), valid.entityType()));
        assertInvalid(validator, copy(valid, valid.id(), valid.stats(),
                valid.ai(), valid.appearance(), "PLAYER"));
        assertInvalid(validator, copy(valid, valid.id(),
                new MobStatsDefinition(Double.NaN, 1, 0, 0, 0,
                        1, 1, 0, 1.75, 0),
                valid.ai(), valid.appearance(), valid.entityType()));
        assertInvalid(validator, copy(valid, valid.id(),
                new MobStatsDefinition(-1, 1, 0, 0, 0,
                        1, 1, 0, 1.75, 0),
                valid.ai(), valid.appearance(), valid.entityType()));
        assertInvalid(validator, copy(valid, valid.id(),
                new MobStatsDefinition(.5, 1, 0, 0, 0,
                        1, 1, 0, 1.75, 0),
                valid.ai(), valid.appearance(), valid.entityType()));
        MobAiDefinition invalidAi = new MobAiDefinition(
                MobAiDefinition.Preset.AGGRESSIVE,
                MobAiDefinition.TargetPriority.NEAREST,
                30, 20, 10, 2, 1,
                true, true, true, false);
        assertInvalid(validator, copy(valid, valid.id(), valid.stats(),
                invalidAi, valid.appearance(), valid.entityType()));
        assertInvalid(validator, withEquipment(valid,
                MobAppearanceDefinition.Slot.MAIN_HAND,
                new MobEquipmentEntry(
                        MobEquipmentEntry.SourceType.PROJECTS_ITEM,
                        "missing", "", "", false, true, true)));
        assertInvalid(validator, withEquipment(valid,
                MobAppearanceDefinition.Slot.HEAD,
                new MobEquipmentEntry(
                        MobEquipmentEntry.SourceType.VANILLA_ITEM,
                        "", "MISSING", "", false, true, true)));
        assertInvalid(validator, withEquipment(valid,
                MobAppearanceDefinition.Slot.CHEST,
                new MobEquipmentEntry(
                        MobEquipmentEntry.SourceType.VANILLA_ITEM,
                        "", "LEATHER_HELMET", "", false, true, true)));
        assertInvalid(validator, withEquipment(valid,
                MobAppearanceDefinition.Slot.HEAD,
                new MobEquipmentEntry(
                        MobEquipmentEntry.SourceType.CUSTOM_HEAD,
                        "missing", "", "", false, true, true)));
        assertInvalid(validator, withEquipment(valid,
                MobAppearanceDefinition.Slot.MAIN_HAND,
                new MobEquipmentEntry(
                        MobEquipmentEntry.SourceType.VANILLA_ITEM,
                        "", "IRON_SWORD", "", false, true, false)));
        assertInvalid(validator, copy(valid, valid.id(), valid.stats(), valid.ai(),
                new MobAppearanceDefinition(4.01,
                        MobAppearanceDefinition.Age.ADULT, false, "WHITE",
                        java.util.Map.of(), java.util.Map.of()),
                valid.entityType()));
        assertInvalid(validator, copy(valid, valid.id(), valid.stats(), valid.ai(),
                new MobAppearanceDefinition(1,
                        MobAppearanceDefinition.Age.BABY, false, "WHITE",
                        java.util.Map.of(), java.util.Map.of()),
                "SLIME"));
        MobDefinition invalidSlime = copy(valid, valid.id(), valid.stats(), valid.ai(),
                new MobAppearanceDefinition(1, MobAppearanceDefinition.Age.ADULT,
                        false, "WHITE", java.util.Map.of("size", "huge"),
                        java.util.Map.of()), "SLIME");
        assertInvalid(validator, invalidSlime);
        MobDefinition validSheep = copy(valid, valid.id(), valid.stats(), valid.ai(),
                new MobAppearanceDefinition(1, MobAppearanceDefinition.Age.ADULT,
                        false, "WHITE", java.util.Map.of(
                        "color", "BLUE", "sheared", "true"), java.util.Map.of()),
                "SHEEP");
        assert validator.validate(validSheep).valid();

        YamlConfiguration encoded = MobDefinitionYaml.write(valid.withRevision(3));
        YamlConfiguration decoded = new YamlConfiguration();
        decoded.loadFromString(encoded.saveToString());
        assert MobDefinitionYaml.read(decoded).equals(valid.withRevision(3));
        YamlConfiguration assignedYaml = MobDefinitionYaml.write(assigned);
        assert MobDefinitionYaml.read(assignedYaml).equals(assigned);
        YamlConfiguration legacyYaml = MobDefinitionYaml.write(valid);
        legacyYaml.set("abilities", null);
        assert MobDefinitionYaml.read(legacyYaml).abilityIds().isEmpty();
        YamlConfiguration malformedAbilities = MobDefinitionYaml.write(valid);
        malformedAbilities.set("abilities", "projects:arcane-burst");
        assertThrowsIllegal(() -> MobDefinitionYaml.read(malformedAbilities));
        YamlConfiguration nonStringAbilities = MobDefinitionYaml.write(valid);
        nonStringAbilities.set("abilities", List.of(3));
        assertThrowsIllegal(() -> MobDefinitionYaml.read(nonStringAbilities));

        Path directory = Files.createTempDirectory("projects-mobs-");
        MobDefinitionRepository repository = new MobDefinitionRepository(
                directory, validator, message -> { });
        assert repository.reload().success();
        var firstSave = repository.save(valid, 0);
        assert firstSave.success() && firstSave.definition().revision() == 1;
        assert repository.save(valid, 0).revisionConflict();
        assert repository.reload().success();
        assert repository.get(valid.id()).equals(firstSave.definition());
        MobDefinition refreshedDefinition = firstSave.definition().withRevision(2)
                .withAbilityIds(List.of("projects:server"));
        String refreshedYaml = MobDefinitionYaml.write(refreshedDefinition).saveToString();
        Files.writeString(directory.resolve(valid.id() + ".yml"), refreshedYaml);
        assert repository.reload().success();
        assert repository.get(valid.id()).equals(refreshedDefinition);
        MobEditorManager.Session reloadSession = new MobEditorManager.Session();
        reloadSession.draft = firstSave.definition();
        reloadSession.originalId = valid.id();
        reloadSession.baseRevision = firstSave.definition().revision();
        MobEditorManager.refreshSessionDraft(reloadSession,
                reloadSession.reloadGeneration, valid.id(), repository.get(valid.id()));
        assert reloadSession.draft == repository.get(valid.id());
        assert reloadSession.baseRevision == 2;
        assert reloadSession.draft.abilityIds().equals(List.of("projects:server"));
        assert reloadSession.reloadGeneration == 1;
        MobEditorManager.Session locallyChangedSession = new MobEditorManager.Session();
        MobDefinition localDraft = firstSave.definition().withAbilityIds(
                List.of("projects:local-edit"));
        locallyChangedSession.draft = localDraft;
        locallyChangedSession.originalId = valid.id();
        locallyChangedSession.baseRevision = firstSave.definition().revision();
        long requestedReloadGeneration = locallyChangedSession.reloadGeneration;
        locallyChangedSession.reloadGeneration++;
        locallyChangedSession.draftMutation++;
        MobEditorManager.refreshSessionDraft(locallyChangedSession,
                requestedReloadGeneration, valid.id(), refreshedDefinition);
        assert locallyChangedSession.draft == localDraft;
        assert locallyChangedSession.baseRevision == firstSave.definition().revision();
        MobEditorManager.refreshSessionDraft(locallyChangedSession,
                locallyChangedSession.reloadGeneration, valid.id(),
                copy(refreshedDefinition, "other-mob", refreshedDefinition.stats(),
                        refreshedDefinition.ai(), refreshedDefinition.appearance(),
                        refreshedDefinition.entityType()));
        assert locallyChangedSession.draft == localDraft;
        MobEditorManager.Session removedSession = new MobEditorManager.Session();
        removedSession.draft = firstSave.definition();
        removedSession.originalId = "removed-mob";
        MobEditorManager.refreshSessionDraft(removedSession,
                removedSession.reloadGeneration, "removed-mob", null);
        assert removedSession.draft == null && removedSession.originalId == null;
        DefinitionReloadGuard.validateCount(
                MobDefinitionRepository.MAX_DEFINITIONS,
                MobDefinitionRepository.MAX_DEFINITIONS);
        assertThrowsIo(() -> DefinitionReloadGuard.validateCount(
                MobDefinitionRepository.MAX_DEFINITIONS + 1,
                MobDefinitionRepository.MAX_DEFINITIONS));
        DefinitionReloadGuard.validateFileSize(
                MobDefinitionRepository.MAX_DEFINITION_FILE_BYTES,
                MobDefinitionRepository.MAX_DEFINITION_FILE_BYTES);
        assertThrowsIo(() -> DefinitionReloadGuard.validateFileSize(
                MobDefinitionRepository.MAX_DEFINITION_FILE_BYTES + 1,
                MobDefinitionRepository.MAX_DEFINITION_FILE_BYTES));
        Files.copy(directory.resolve(valid.id() + ".yml"),
                directory.resolve("duplicate.yml"));
        assert repository.reload().rejected() == 1;
        Path directoryTarget = directory.resolve("must-remain-directory.yml");
        Files.createDirectory(directoryTarget);
        assertThrowsIo(() -> MobDefinitionRepository.writeAtomic(
                directoryTarget, "must not replace the directory"));
        assert Files.isDirectory(directoryTarget);
        Path rejectedFile = directory.resolve("blocked.yml");
        Files.writeString(rejectedFile, "not: [valid yaml");
        assert !repository.reload().success();
        String rejectedContents = Files.readString(rejectedFile);
        MobDefinition blocked = copy(valid, "blocked", valid.stats(), valid.ai(),
                valid.appearance(), valid.entityType());
        assert !repository.save(blocked, 0).success();
        assert Files.readString(rejectedFile).equals(rejectedContents);
        assert repository.get(valid.id()).equals(refreshedDefinition);

        Path staleDirectory = Files.createTempDirectory("projects-stale-abilities-");
        MobDefinitionRepository staleRepository = new MobDefinitionRepository(
                staleDirectory, validator, message -> { });
        assert staleRepository.save(assigned, 0).success();
        assert staleRepository.reload().success();
        assert staleRepository.get(assigned.id()).abilityIds()
                .equals(assigned.abilityIds());
        var sharedAbility = DevAbilityDefinitions.sharedArcaneBurst();
        AbilityRegistry exactRegistry = new AbilityRegistry(
                AbilityRuntime.standardActions());
        exactRegistry.register(sharedAbility);
        assert staleRepository.save(assigned.withAbilityIds(List.of(sharedAbility.id())), 1)
                .success();
        assert staleRepository.reload().success();
        MobDefinition resolvedAfterReload = staleRepository.get(assigned.id());
        assert new MobAbilityAssignmentPolicy(exactRegistry)
                .resolve(resolvedAfterReload, sharedAbility.id()).definition()
                == sharedAbility;

        String textureJson = "{\"textures\":{\"SKIN\":{\"url\":"
                + "\"https://textures.minecraft.net/texture/abc\"}}}";
        String texture = Base64.getEncoder().encodeToString(
                textureJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        HeadDefinitionValidator headValidator =
                new HeadDefinitionValidator(id -> id.equals("starter_sword"));
        HeadDefinition head = new HeadDefinition(
                1, 0, "pirate_head", "海賊の頭",
                HeadDefinition.SourceType.TEXTURE_VALUE,
                "", texture, "", List.of("pirate"), true, "管理者登録");
        assert headValidator.validate(head).valid();
        assert !headValidator.validate(new HeadDefinition(
                1, 0, "bad", "bad", HeadDefinition.SourceType.TEXTURE_VALUE,
                "", "not-base64", "", List.of(), false, "")).valid();
        String hostileJson = "{\"textures\":{\"SKIN\":{\"url\":"
                + "\"https://attacker.example/track\"}},\"junk\":{\"url\":"
                + "\"https://textures.minecraft.net/texture/abc\"}}";
        String hostileTexture = Base64.getEncoder().encodeToString(
                hostileJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assert !headValidator.validate(new HeadDefinition(
                1, 0, "hostile", "hostile", HeadDefinition.SourceType.TEXTURE_VALUE,
                "", hostileTexture, "", List.of(), false, "")).valid();
        String canonicalTexture = HeadDefinitionValidator.canonicalTextureValue(texture);
        assert !canonicalTexture.isBlank();
        assert new String(Base64.getDecoder().decode(canonicalTexture),
                java.nio.charset.StandardCharsets.UTF_8).equals(textureJson);
        Path headDirectory = Files.createTempDirectory("projects-heads-");
        HeadDefinitionRepository headRepository = new HeadDefinitionRepository(
                headDirectory, headValidator, message -> { });
        assert headRepository.reload().success();
        var firstHeadSave = headRepository.create(head);
        assert firstHeadSave.success() && firstHeadSave.definition().revision() == 1;
        DefinitionReloadGuard.validateCount(
                HeadDefinitionRepository.MAX_DEFINITIONS,
                HeadDefinitionRepository.MAX_DEFINITIONS);
        assertThrowsIo(() -> DefinitionReloadGuard.validateCount(
                HeadDefinitionRepository.MAX_DEFINITIONS + 1,
                HeadDefinitionRepository.MAX_DEFINITIONS));
        DefinitionReloadGuard.validateFileSize(
                HeadDefinitionRepository.MAX_DEFINITION_FILE_BYTES,
                HeadDefinitionRepository.MAX_DEFINITION_FILE_BYTES);
        assertThrowsIo(() -> DefinitionReloadGuard.validateFileSize(
                HeadDefinitionRepository.MAX_DEFINITION_FILE_BYTES + 1,
                HeadDefinitionRepository.MAX_DEFINITION_FILE_BYTES));
        assert !headRepository.create(head.withRevision(1)).success();
        assert headRepository.save(firstHeadSave.definition(), 0).revisionConflict();
        Path rejectedHeadFile = headDirectory.resolve("blocked_head.yml");
        Files.writeString(rejectedHeadFile, "not: [valid yaml");
        assert !headRepository.reload().success();
        String rejectedHeadContents = Files.readString(rejectedHeadFile);
        HeadDefinition blockedHead = new HeadDefinition(
                1, 0, "blocked_head", "blocked", HeadDefinition.SourceType.TEXTURE_VALUE,
                "", texture, "", List.of(), false, "");
        assert !headRepository.create(blockedHead).success();
        assert Files.readString(rejectedHeadFile).equals(rejectedHeadContents);
        assert headRepository.get(head.id()).equals(firstHeadSave.definition());
        Files.delete(rejectedHeadFile);
        Path rejectedHeadDirectory = headDirectory.resolve("directory.yml");
        Files.createDirectory(rejectedHeadDirectory);
        assert !headRepository.reload().success();
        assert headRepository.get(head.id()).equals(firstHeadSave.definition());

        ByteArrayOutputStream packetBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(packetBytes)) {
            MobEditorPacketIO.writeMob(output, valid);
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(packetBytes.toByteArray()))) {
            assert MobEditorPacketIO.readMob(input).equals(valid);
            assert input.available() == 0;
        }
        ByteArrayOutputStream assignedPacket = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(assignedPacket)) {
            MobEditorPacketIO.writeMob(output, assigned);
        }
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(assignedPacket.toByteArray()))) {
            MobDefinition decodedV1 = MobEditorPacketIO.readMob(input);
            assert decodedV1.abilityIds().isEmpty() && input.available() == 0;
            assert MobEditorManager.preserveAbilityIds(assigned, decodedV1)
                    .equals(assigned);
        }
        assertThrowsIo(() -> {
            try (DataOutputStream output = new DataOutputStream(
                    new ByteArrayOutputStream())) {
                MobEditorPacketIO.writeString(output, "あ".repeat(22), 64);
            }
        });
        String boundedMessage = MobEditorPacketIO.boundedUtf8("不正です / ".repeat(100), 256);
        assert boundedMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 256;
        assert boundedMessage.endsWith("…");

        closeThenReopenDoesNotLeakV1State();

        deleteTree(directory);
        deleteTree(staleDirectory);
        deleteTree(headDirectory);
    }

    private static void assertThrowsIo(IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("IOException expected");
        } catch (IOException expected) {
            // Expected.
        }
    }

    private static void closeThenReopenDoesNotLeakV1State() throws Exception {
        assertV1StateRace("save", true);
        assertV1StateRace("reload", false);
    }

    private static void assertV1StateRace(String oldOperation,
                                          boolean oldRateLimited) throws Exception {
        MobEditorChannel channel = new MobEditorChannel(
                null, emptyManager(), null, null);
        List<byte[]> sent = new ArrayList<>();
        UUID playerId = UUID.randomUUID();
        Player player = racePlayer(playerId, sent);
        long oldToken = beginV1(channel, player, oldRateLimited);
        assert oldToken != 0 : oldOperation + " request did not start";

        channel.onPluginMessageReceived(MobEditorChannel.REQUEST_CHANNEL, player,
                new byte[]{(byte) MobEditorPacketIO.VERSION,
                        (byte) MobEditorChannel.CLOSE});

        long newToken = beginV1(channel, player, !oldRateLimited);
        assert newToken != 0 && newToken != oldToken
                : oldOperation + " reopen did not get an independent token";
        finishV1(channel, player, oldToken, snapshot("old " + oldOperation));
        assert sent.isEmpty() : "stale v1 " + oldOperation + " response leaked";
        assert inFlight(channel, playerId) == newToken
                : "stale v1 " + oldOperation + " completion removed the new token";

        finishV1(channel, player, newToken, snapshot("new " + oldOperation));
        assert sent.size() == 1 : "valid v1 response was lost";
        assert stateMessage(sent.getFirst(), MobEditorPacketIO.VERSION)
                .equals("new " + oldOperation);
        assert inFlight(channel, playerId) == null : "v1 token was not retired";
    }

    private static long beginV1(MobEditorChannel channel, Player player,
                                boolean rateLimited) throws Exception {
        Method method = MobEditorChannel.class.getDeclaredMethod(
                "beginStateIo", Player.class, boolean.class);
        method.setAccessible(true);
        return (long) method.invoke(channel, player, rateLimited);
    }

    private static void finishV1(MobEditorChannel channel, Player player,
                                 long token, MobEditorManager.Snapshot snapshot)
            throws Exception {
        Method method = MobEditorChannel.class.getDeclaredMethod(
                "finishStateIo", Player.class, long.class, MobEditorManager.Snapshot.class);
        method.setAccessible(true);
        method.invoke(channel, player, token, snapshot);
    }

    private static MobEditorManager.Snapshot snapshot(String message) {
        return new MobEditorManager.Snapshot(true, false, message,
                List.of(), null, List.of(), null);
    }

    @SuppressWarnings("unchecked")
    private static Long inFlight(MobEditorChannel channel, UUID playerId)
            throws Exception {
        Field field = MobEditorChannel.class.getDeclaredField("stateIoInFlight");
        field.setAccessible(true);
        return ((java.util.Map<UUID, Long>) field.get(channel)).get(playerId);
    }

    private static MobEditorManager emptyManager() throws Exception {
        MobEditorManager manager = (MobEditorManager) unsafe().allocateInstance(
                MobEditorManager.class);
        Field sessions = MobEditorManager.class.getDeclaredField("sessions");
        sessions.setAccessible(true);
        sessions.set(manager, new HashMap<>());
        return manager;
    }

    private static Player racePlayer(UUID playerId, List<byte[]> sent) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "hasPermission" -> true;
                    case "isOnline" -> true;
                    case "sendPluginMessage" -> {
                        sent.add((byte[]) arguments[2]);
                        yield null;
                    }
                    case "hashCode" -> playerId.hashCode();
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "MobEditorRacePlayer";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private static String stateMessage(byte[] packet, int version) throws IOException {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(packet))) {
            assert input.readUnsignedByte() == version;
            input.readBoolean();
            input.readBoolean();
            input.readBoolean();
            return MobEditorPacketIO.readString(input, 256);
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void assertThrowsIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws Exception;
    }

    private static void assertInvalid(
            MobDefinitionValidator validator,
            MobDefinition definition
    ) {
        assert !validator.validate(definition).valid();
    }

    private static MobDefinition copy(
            MobDefinition source,
            String id,
            MobStatsDefinition stats,
            MobAiDefinition ai,
            MobAppearanceDefinition appearance,
            String entityType
    ) {
        return new MobDefinition(
                source.schemaVersion(), source.revision(), id,
                source.displayName(), entityType, source.category(),
                source.enabled(), source.level(), source.nameplateMode(),
                source.tags(), stats, source.basicAttack(), ai, appearance);
    }

    private static MobDefinition withEquipment(
            MobDefinition source,
            MobAppearanceDefinition.Slot slot,
            MobEquipmentEntry entry
    ) {
        java.util.EnumMap<MobAppearanceDefinition.Slot, MobEquipmentEntry> equipment =
                new java.util.EnumMap<>(MobAppearanceDefinition.Slot.class);
        equipment.putAll(source.appearance().equipment());
        equipment.put(slot, entry);
        return copy(source, source.id(), source.stats(), source.ai(),
                new MobAppearanceDefinition(
                        source.appearance().scale(), source.appearance().age(),
                        source.appearance().glowing(),
                        source.appearance().glowingColor(),
                        source.appearance().variants(), equipment),
                source.entityType());
    }

    private static void deleteTree(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
