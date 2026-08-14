package io.github.gyai.projects.monster.editor.catalog;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface HeadCatalogProvider {
    String id();
    boolean enabled();
    String statusMessage();
    Set<HeadCatalogCapability> capabilities();
    CompletableFuture<HeadCatalogPage> search(HeadCatalogQuery query);
    CompletableFuture<HeadCatalogEntry> detail(String entryId);

    default CompletableFuture<List<String>> categories() {
        return CompletableFuture.completedFuture(List.of());
    }

    default CompletableFuture<List<String>> tags() {
        return CompletableFuture.completedFuture(List.of());
    }
}
