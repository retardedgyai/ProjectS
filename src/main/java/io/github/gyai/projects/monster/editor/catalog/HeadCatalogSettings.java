package io.github.gyai.projects.monster.editor.catalog;

public record HeadCatalogSettings(
        boolean enabled,
        String provider,
        String apiKey,
        String appId,
        String locale,
        long cacheTtlMillis,
        int cacheMaxEntries,
        int timeoutSeconds,
        int maxRetries
) {
    public HeadCatalogSettings {
        provider = HeadCatalogEntry.bounded(provider, 64);
        apiKey = HeadCatalogEntry.bounded(apiKey, 512);
        appId = HeadCatalogEntry.bounded(appId, 128);
        locale = HeadCatalogEntry.bounded(locale, 16);
        if (locale.isBlank()) locale = "ja";
        cacheTtlMillis = Math.clamp(cacheTtlMillis, 1_000L, 86_400_000L);
        cacheMaxEntries = Math.clamp(cacheMaxEntries, 16, 20_000);
        timeoutSeconds = Math.clamp(timeoutSeconds, 1, 30);
        maxRetries = Math.clamp(maxRetries, 0, 4);
    }

    public static HeadCatalogSettings disabled() {
        return new HeadCatalogSettings(false, "MINECRAFT_HEADS", "", "", "ja",
                3_600_000L, 5_000, 10, 2);
    }

    public String effectiveApiKey() {
        String environment = System.getenv("PROJECTS_MINECRAFT_HEADS_API_KEY");
        return environment == null || environment.isBlank()
                ? apiKey : HeadCatalogEntry.bounded(environment, 512);
    }

    @Override
    public String toString() {
        return "HeadCatalogSettings[enabled=" + enabled + ", provider=" + provider
                + ", apiKey=<redacted>, appIdConfigured=" + !appId.isBlank()
                + ", locale=" + locale + "]";
    }

}
