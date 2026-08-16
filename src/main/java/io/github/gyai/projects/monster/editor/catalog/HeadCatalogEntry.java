package io.github.gyai.projects.monster.editor.catalog;

import java.nio.charset.StandardCharsets;
import java.util.List;

public record HeadCatalogEntry(
        String providerId,
        String entryId,
        String displayName,
        String category,
        List<String> tags,
        String thumbnailUrl,
        String textureValue,
        boolean imported
) {
    public HeadCatalogEntry {
        providerId = bounded(providerId, 32);
        entryId = bounded(entryId, 128);
        displayName = bounded(displayName, 128);
        category = bounded(category, 48);
        tags = tags == null ? List.of() : tags.stream().limit(16)
                .map(value -> bounded(value, 32)).filter(value -> !value.isBlank())
                .distinct().toList();
        thumbnailUrl = bounded(thumbnailUrl, 512);
        if (!thumbnailUrl.isBlank() && !HeadThumbnailSecurity.safeUri(thumbnailUrl)) {
            thumbnailUrl = "";
        }
        textureValue = safe(textureValue).strip();
        if (textureValue.getBytes(StandardCharsets.UTF_8).length > 16_384) {
            textureValue = "";
        }
    }

    public HeadCatalogEntry withoutTexture() {
        return new HeadCatalogEntry(providerId, entryId, displayName, category,
                tags, thumbnailUrl, "", imported);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static String bounded(String value, int maximumBytes) {
        String safe = safe(value).strip().replaceAll("[\\p{Cntrl}]", "");
        int bytes = safe.getBytes(StandardCharsets.UTF_8).length;
        if (bytes <= maximumBytes) return safe;
        int usedBytes = 0;
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < safe.length();) {
            int codePoint = safe.codePointAt(offset);
            int codePointBytes = new String(Character.toChars(codePoint))
                    .getBytes(StandardCharsets.UTF_8).length;
            if (usedBytes + codePointBytes > maximumBytes) break;
            result.appendCodePoint(codePoint);
            usedBytes += codePointBytes;
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }
}
