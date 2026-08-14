package io.github.gyai.projects.monster.editor.catalog;

import java.util.Locale;
import java.util.regex.Pattern;

public record HeadCatalogQuery(String text, String category, int page, int pageSize) {
    public static final int MAX_QUERY_LENGTH = 64;
    public static final int MAX_PAGE_SIZE = 48;
    static final int MAX_PAGE = 10_000;
    private static final Pattern CATEGORY = Pattern.compile("[a-z0-9_-]{0,48}");

    public HeadCatalogQuery {
        text = bounded(normalize(text), MAX_QUERY_LENGTH);
        category = bounded(normalize(category).toLowerCase(Locale.ROOT), 48);
        if (!CATEGORY.matcher(category).matches()) category = "";
        page = Math.clamp(page, 0, MAX_PAGE);
        pageSize = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
    }

    public static HeadCatalogQuery of(String text, String category, int page, int pageSize) {
        return new HeadCatalogQuery(text, category, page, pageSize);
    }

    public String cacheKey() {
        return text.toLowerCase(Locale.ROOT) + '\u0000' + category + '\u0000'
                + page + '\u0000' + pageSize;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.strip().replaceAll("\\s+", " ").replaceAll("[\\p{Cntrl}]", "");
    }

    private static String bounded(String value, int maximumBytes) {
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maximumBytes) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String next = new String(Character.toChars(codePoint));
            if (result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    + next.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    > maximumBytes) {
                break;
            }
            result.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }
}
