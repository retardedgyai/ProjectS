package io.github.gyai.projects.monster.editor.catalog;

import java.util.List;

public record HeadCatalogPage(
        List<HeadCatalogEntry> entries,
        int page,
        int pageSize,
        int total,
        boolean hasNext,
        List<String> warnings
) {
    public HeadCatalogPage {
        page = Math.clamp(page, 0, HeadCatalogQuery.MAX_PAGE);
        pageSize = Math.clamp(pageSize, 1, HeadCatalogQuery.MAX_PAGE_SIZE);
        entries = entries == null ? List.of() : entries.stream()
                .filter(value -> value != null).limit(pageSize)
                .map(HeadCatalogEntry::withoutTexture).toList();
        warnings = warnings == null ? List.of() : warnings.stream().limit(8)
                .map(value -> HeadCatalogEntry.bounded(value, 256))
                .filter(value -> !value.isBlank()).toList();
        total = Math.max(0, total);
    }

    public static HeadCatalogPage empty(HeadCatalogQuery query, String warning) {
        HeadCatalogQuery safe = query == null
                ? HeadCatalogQuery.of("", "", 0, 24) : query;
        return new HeadCatalogPage(List.of(), safe.page(), safe.pageSize(), 0,
                false, warning == null || warning.isBlank() ? List.of() : List.of(warning));
    }
}
