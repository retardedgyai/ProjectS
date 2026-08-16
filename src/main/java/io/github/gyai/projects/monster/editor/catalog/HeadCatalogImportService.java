package io.github.gyai.projects.monster.editor.catalog;

import io.github.gyai.projects.monster.editor.HeadDefinition;
import io.github.gyai.projects.monster.editor.HeadDefinitionRepository;
import io.github.gyai.projects.monster.editor.HeadDefinitionValidator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

public final class HeadCatalogImportService {
    private final HeadDefinitionRepository repository;

    public HeadCatalogImportService(HeadDefinitionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public ImportResult importEntry(HeadCatalogEntry entry, String requestedId) {
        if (entry == null || entry.textureValue().isBlank()
                || entry.textureValue().getBytes(StandardCharsets.UTF_8).length
                > HeadDefinitionValidator.MAX_TEXTURE_VALUE_BYTES
                || HeadDefinitionValidator.canonicalTextureValue(entry.textureValue()).isBlank()) {
            return ImportResult.failure("Importに必要なTexture Valueが不正です");
        }
        String id = requestedId == null || requestedId.isBlank()
                ? generateId(entry.providerId(), entry.entryId()) : normalizeId(requestedId);
        if (id.isBlank()) return ImportResult.failure("Head IDが不正です");
        if (repository.get(id) != null) {
            return ImportResult.failure("Head IDが重複しています: " + suggestAvailable(id));
        }
        LinkedHashSet<String> uniqueTags = new LinkedHashSet<>();
        for (String tag : entry.tags()) {
            String bounded = HeadCatalogEntry.bounded(tag, 32);
            if (!bounded.isBlank()) uniqueTags.add(bounded);
        }
        String category = HeadCatalogEntry.bounded(entry.category(), 32);
        if (!category.isBlank()) uniqueTags.add(category);
        ArrayList<String> tags = new ArrayList<>(uniqueTags.stream().limit(32).toList());
        HeadDefinition definition = new HeadDefinition(
                HeadDefinition.SCHEMA_VERSION, 0, id,
                entry.displayName().isBlank() ? id : entry.displayName(),
                HeadDefinition.SourceType.TEXTURE_VALUE, "",
                HeadDefinitionValidator.canonicalTextureValue(entry.textureValue()), "",
                List.copyOf(tags), false,
                "provider=" + safeNote(entry.providerId()) + "; entry="
                        + safeNote(entry.entryId()));
        HeadDefinitionRepository.SaveResult saved = repository.create(definition);
        return saved.success()
                ? ImportResult.success(saved.definition())
                : ImportResult.failure(saved.message());
    }

    public String suggestAvailable(String base) {
        String normalized = normalizeId(base);
        if (normalized.isBlank()) normalized = "minecraft_heads_head";
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String tail = "_" + suffix;
            String candidate = normalized.substring(0,
                    Math.min(normalized.length(), 64 - tail.length())) + tail;
            if (repository.get(candidate) == null) return candidate;
        }
        return "";
    }

    public static String generateId(String provider, String entryId) {
        return normalizeId((provider == null ? "" : provider) + "_"
                + (entryId == null ? "" : entryId));
    }

    public static String normalizeId(String value) {
        String ascii = Normalizer.normalize(value == null ? "" : value,
                Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^[_-]+|[_-]+$", "");
        return ascii.substring(0, Math.min(64, ascii.length()));
    }

    private static String safeNote(String value) {
        return HeadCatalogEntry.bounded(value, 64).replace(';', '_');
    }

    public record ImportResult(boolean success, HeadDefinition definition, String message) {
        public static ImportResult success(HeadDefinition value) {
            return new ImportResult(true, value, "Headをローカルへ登録しました");
        }
        public static ImportResult failure(String message) {
            return new ImportResult(false, null, message);
        }
    }
}
