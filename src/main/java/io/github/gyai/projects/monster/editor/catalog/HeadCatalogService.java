package io.github.gyai.projects.monster.editor.catalog;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class HeadCatalogService {
    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final Pattern ENTRY_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private final HeadCatalogProvider provider;
    private final HeadCatalogCache<HeadCatalogPage> pages;
    private final HeadCatalogCache<HeadCatalogEntry> details;
    private final HeadCatalogRateLimiter rateLimiter;
    private final int timeoutSeconds;
    private final int maximumRetries;
    private final Map<String, CompletableFuture<?>> inFlight = new ConcurrentHashMap<>();

    public HeadCatalogService(HeadCatalogProvider provider, HeadCatalogSettings settings) {
        this.provider = Objects.requireNonNull(provider);
        HeadCatalogSettings safe = settings == null ? HeadCatalogSettings.disabled() : settings;
        pages = new HeadCatalogCache<>(safe.cacheTtlMillis(), safe.cacheMaxEntries());
        details = new HeadCatalogCache<>(safe.cacheTtlMillis(),
                Math.max(16, safe.cacheMaxEntries() / 4));
        rateLimiter = new HeadCatalogRateLimiter(20, Duration.ofMinutes(1).toMillis());
        timeoutSeconds = safe.timeoutSeconds();
        maximumRetries = safe.maxRetries();
    }

    public CompletableFuture<HeadCatalogPage> search(String subject, HeadCatalogQuery query) {
        HeadCatalogQuery safeQuery = safeQuery(query);
        if (!provider.enabled()) {
            return CompletableFuture.completedFuture(
                    HeadCatalogPage.empty(safeQuery, statusMessage()));
        }
        if (!hasCapability(HeadCatalogCapability.SEARCH_HEADS)) {
            return CompletableFuture.completedFuture(HeadCatalogPage.empty(
                    safeQuery, "現在のProviderはHead検索に対応していません"));
        }
        if (!rateLimiter.acquire(safeSubject(subject))) {
            return CompletableFuture.completedFuture(
                    HeadCatalogPage.empty(safeQuery, "リクエストが制限されています"));
        }
        String key = provider.id() + ":page:" + safeQuery.cacheKey();
        var cached = pages.get(key);
        if (cached.isPresent()) return CompletableFuture.completedFuture(cached.get());
        return deduplicate(key, () -> retry(() -> provider.search(safeQuery), 0)
                .thenApply(HeadCatalogService::sanitizePage)
                .thenApply(page -> {
                    pages.put(key, page);
                    return page;
                }));
    }

    public CompletableFuture<HeadCatalogEntry> detail(String subject, String entryId) {
        String id = validateEntryId(entryId);
        if (!provider.enabled()) return CompletableFuture.failedFuture(
                new HeadCatalogException(statusMessage()));
        if (!rateLimiter.acquire(safeSubject(subject))) return CompletableFuture.failedFuture(
                new HeadCatalogException("リクエストが制限されています"));
        String key = provider.id() + ":detail:" + id;
        var cached = details.get(key);
        if (cached.isPresent()) return CompletableFuture.completedFuture(cached.get());
        return deduplicate(key, () -> retry(() -> provider.detail(id), 0)
                .thenApply(entry -> sanitizeDetail(entry, id))
                .thenApply(entry -> {
                    details.put(key, entry);
                    return entry;
                }));
    }

    public boolean canImport() {
        return provider.enabled() && hasCapability(HeadCatalogCapability.TEXTURE_VALUE);
    }

    public String importDisabledReason() {
        if (!provider.enabled()) return statusMessage();
        if (!hasCapability(HeadCatalogCapability.TEXTURE_VALUE)) {
            return "現在のライセンスではImportに必要なTexture Valueを取得できません";
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> deduplicate(
            String key, Supplier<CompletableFuture<T>> supplier
    ) {
        CompletableFuture<?> shared;
        try {
            shared = inFlight.computeIfAbsent(key, ignored -> {
                try {
                    CompletableFuture<T> supplied = supplier.get();
                    return supplied == null ? CompletableFuture.failedFuture(
                            new HeadCatalogException("Provider応答が空です")) : supplied;
                } catch (RuntimeException exception) {
                    return CompletableFuture.failedFuture(
                            new HeadCatalogException("Providerへ接続できません"));
                }
            });
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(
                    new HeadCatalogException("Providerへ接続できません"));
        }
        CompletableFuture<T> future = (CompletableFuture<T>) shared;
        future.whenComplete((value, error) -> inFlight.remove(key, future));
        return future;
    }

    private <T> CompletableFuture<T> retry(
            Supplier<CompletableFuture<T>> supplier, int attempt
    ) {
        CompletableFuture<T> timed;
        try {
            timed = supplier.get().orTimeout(timeoutSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            timed = CompletableFuture.failedFuture(exception);
        }
        return timed.handle((value, error) -> {
            if (error == null) return CompletableFuture.completedFuture(value);
            if (attempt >= maximumRetries) return CompletableFuture.<T>failedFuture(
                    new HeadCatalogException("Providerへ接続できません"));
            return retry(supplier, attempt + 1);
        }).thenCompose(value -> value);
    }

    private static String validateEntryId(String value) {
        String id = value == null ? "" : value.strip();
        if (!ENTRY_ID.matcher(id).matches()) {
            throw new HeadCatalogException("Provider Entry IDが不正です");
        }
        return id;
    }

    private static String safeSubject(String subject) {
        String value = HeadCatalogEntry.bounded(subject, 128);
        return value.isBlank() ? "unknown" : value;
    }

    private boolean hasCapability(HeadCatalogCapability capability) {
        var capabilities = provider.capabilities();
        return capabilities != null && capabilities.contains(capability);
    }

    private String statusMessage() {
        String status = HeadCatalogEntry.bounded(provider.statusMessage(), 256);
        return status.isBlank() ? "Head Catalogを利用できません" : status;
    }

    private static HeadCatalogQuery safeQuery(HeadCatalogQuery query) {
        return query == null ? HeadCatalogQuery.of("", "", 0, DEFAULT_PAGE_SIZE) : query;
    }

    private static HeadCatalogPage sanitizePage(HeadCatalogPage page) {
        if (page == null) throw new HeadCatalogException("Provider応答が空です");
        return new HeadCatalogPage(page.entries(), page.page(), page.pageSize(),
                page.total(), page.hasNext(), page.warnings());
    }

    private HeadCatalogEntry sanitizeDetail(HeadCatalogEntry entry, String expectedId) {
        if (entry == null || !expectedId.equals(entry.entryId())) {
            throw new HeadCatalogException("Provider応答のEntry IDが不正です");
        }
        HeadCatalogEntry safe = new HeadCatalogEntry(
                entry.providerId(), entry.entryId(), entry.displayName(), entry.category(),
                entry.tags(), entry.thumbnailUrl(), entry.textureValue(), entry.imported());
        return hasCapability(HeadCatalogCapability.TEXTURE_VALUE)
                ? safe : safe.withoutTexture();
    }
}
