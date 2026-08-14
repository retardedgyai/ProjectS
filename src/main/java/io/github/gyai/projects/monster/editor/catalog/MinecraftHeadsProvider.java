package io.github.gyai.projects.monster.editor.catalog;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Safe API-v2 provider boundary. The official OpenAPI document is currently
 * unavailable, so endpoint construction intentionally remains disabled until
 * a verified specification and registered app UUID are supplied.
 */
public final class MinecraftHeadsProvider implements HeadCatalogProvider {
    public static final String ID = "minecraft_heads";
    private final HeadCatalogSettings settings;

    public MinecraftHeadsProvider(HeadCatalogSettings settings) {
        this.settings = settings == null ? HeadCatalogSettings.disabled() : settings;
    }

    @Override public String id() { return ID; }

    @Override
    public boolean enabled() {
        return settings.enabled() && ID.equalsIgnoreCase(settings.provider())
                && !settings.appId().isBlank()
                && !settings.effectiveApiKey().isBlank() && endpointVerified();
    }

    @Override
    public String statusMessage() {
        if (!settings.enabled()) return "Head Catalogが無効です";
        if (!ID.equalsIgnoreCase(settings.provider())) return "未対応のHead Catalog Providerです";
        if (settings.appId().isBlank()) return "Minecraft-Heads App UUIDが設定されていません";
        if (settings.effectiveApiKey().isBlank()) return "Minecraft-Heads API Keyが設定されていません";
        return "公式API v2仕様を確認できないため外部取得を停止しています";
    }

    @Override
    public Set<HeadCatalogCapability> capabilities() {
        if (!enabled()) return Set.of();
        return Set.of(HeadCatalogCapability.SEARCH_HEADS,
                HeadCatalogCapability.LIST_CATEGORIES,
                HeadCatalogCapability.BASIC_METADATA);
    }

    @Override
    public CompletableFuture<HeadCatalogPage> search(HeadCatalogQuery query) {
        return CompletableFuture.completedFuture(HeadCatalogPage.empty(query, statusMessage()));
    }

    @Override
    public CompletableFuture<HeadCatalogEntry> detail(String entryId) {
        return CompletableFuture.failedFuture(new HeadCatalogException(statusMessage()));
    }

    private static boolean endpointVerified() {
        return false;
    }
}
