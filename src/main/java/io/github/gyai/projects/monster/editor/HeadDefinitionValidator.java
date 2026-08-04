package io.github.gyai.projects.monster.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class HeadDefinitionValidator {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final Pattern TEXTURE_URL = Pattern.compile(
            "https://textures\\.minecraft\\.net/texture/[A-Za-z0-9_-]{1,256}");
    public static final int MAX_TEXTURE_VALUE_BYTES = 16_384;
    private final Predicate<String> projectsItemExists;
    private final Function<String, String> projectsItemMaterial;

    public HeadDefinitionValidator(Predicate<String> projectsItemExists) {
        this(projectsItemExists, ignored -> "");
    }

    public HeadDefinitionValidator(
            Predicate<String> projectsItemExists,
            Function<String, String> projectsItemMaterial
    ) {
        this.projectsItemExists = projectsItemExists;
        this.projectsItemMaterial = projectsItemMaterial;
    }

    public ValidationResult validate(HeadDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        if (definition == null) return new ValidationResult(List.of("Head定義がありません"));
        if (definition.schemaVersion() != HeadDefinition.SCHEMA_VERSION) {
            errors.add("schema-versionが未対応です");
        }
        if (!ID.matcher(safe(definition.id())).matches()) {
            errors.add("Head IDが不正です");
        }
        if (safe(definition.displayName()).isBlank()
                || bytes(definition.displayName()) > 128) {
            errors.add("Head表示名が空か長すぎます");
        }
        if (definition.sourceType() == null) {
            errors.add("Head source-typeがありません");
        } else switch (definition.sourceType()) {
            case TEXTURE_VALUE -> {
                validateTexture(definition.textureValue(), errors);
                rejectUnused(definition.playerName(), definition.projectsItemId(), errors);
            }
            case PROJECTS_ITEM -> {
                String itemId = safe(definition.projectsItemId());
                if (!projectsItemExists.test(itemId)) {
                    errors.add("存在しないProjectS Item IDです");
                } else {
                    String material = safe(projectsItemMaterial.apply(itemId))
                            .toUpperCase(java.util.Locale.ROOT);
                    if (!material.isBlank() && !material.endsWith("_HELMET")
                            && !material.endsWith("_HEAD")
                            && !material.endsWith("_SKULL")
                            && !material.equals("CARVED_PUMPKIN")) {
                        errors.add("頭装備に利用できないProjectS Itemです");
                    }
                }
                rejectUnused(definition.playerName(), definition.textureValue(), errors);
            }
            case PLAYER_PROFILE -> errors.add(
                    "プレイヤー名解決はMVPでは未対応です。Texture Valueを登録してください");
            case SAVED_HEAD -> errors.add("SAVED_HEADの入れ子参照はMVPでは未対応です");
            case VANILLA_HEAD -> rejectUnused(
                    definition.playerName(), definition.textureValue(),
                    definition.projectsItemId(), errors);
        }
        if (definition.tags() == null || definition.tags().size() > 32) {
            errors.add("Headタグは32件以下にしてください");
        } else {
            HashSet<String> unique = new HashSet<>();
            for (String tag : definition.tags()) {
                if (safe(tag).isBlank() || bytes(tag) > 32
                        || hasControl(tag) || !unique.add(tag)) {
                    errors.add("Headタグが空、重複、または長すぎます");
                    break;
                }
            }
        }
        if (bytes(definition.sourceNote()) > 256 || hasControl(definition.sourceNote())) {
            errors.add("出典メモが長すぎるか不正です");
        }
        return new ValidationResult(errors);
    }

    private static void rejectUnused(
            String first,
            String second,
            ArrayList<String> errors
    ) {
        rejectUnused(first, second, "", errors);
    }

    private static void rejectUnused(
            String first,
            String second,
            String third,
            ArrayList<String> errors
    ) {
        if (!safe(first).isBlank() || !safe(second).isBlank()
                || !safe(third).isBlank()) {
            errors.add("source-typeで使用しないHead値は設定できません");
        }
    }

    private static void validateTexture(String textureValue, ArrayList<String> errors) {
        String value = safe(textureValue).trim();
        if (value.isBlank() || bytes(value) > MAX_TEXTURE_VALUE_BYTES
                || hasControl(value)) {
            errors.add("Texture Valueが空か長すぎます");
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded)).toString();
            if (decoded.length > 8_192 || hasControl(json)
                    || validatedTextureUrl(json) == null) {
                errors.add("Texture Valueの形式が不正です");
            }
        } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException exception) {
            errors.add("Texture Valueが有効なBase64/UTF-8ではありません");
        }
    }

    public static String canonicalTextureValue(String textureValue) {
        try {
            byte[] decoded = Base64.getDecoder().decode(safe(textureValue).trim());
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded)).toString();
            String url = validatedTextureUrl(json);
            if (url == null) return "";
            String canonical = "{\"textures\":{\"SKIN\":{\"url\":\""
                    + url + "\"}}}";
            return Base64.getEncoder().encodeToString(
                    canonical.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException exception) {
            return "";
        }
    }

    private static String validatedTextureUrl(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonObject textures = root.getAsJsonObject().getAsJsonObject("textures");
            if (textures == null || textures.size() != 1 || !textures.has("SKIN")) {
                return null;
            }
            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null || !skin.has("url")
                    || !skin.get("url").isJsonPrimitive()) return null;
            String url = skin.get("url").getAsString();
            if (!TEXTURE_URL.matcher(url).matches()) return null;
            int[] urlFields = {0};
            countUrlFields(root, url, urlFields);
            return urlFields[0] == 1 ? url : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void countUrlFields(
            JsonElement element,
            String allowedUrl,
            int[] count
    ) {
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("url")) {
                    if (!entry.getValue().isJsonPrimitive()
                            || !allowedUrl.equals(entry.getValue().getAsString())) {
                        count[0] = Integer.MAX_VALUE;
                        return;
                    }
                    count[0]++;
                }
                countUrlFields(entry.getValue(), allowedUrl, count);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                countUrlFields(child, allowedUrl, count);
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int bytes(String value) {
        return safe(value).getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean hasControl(String value) {
        return safe(value).chars().anyMatch(character ->
                Character.isISOControl(character) && !Character.isWhitespace(character));
    }
}
