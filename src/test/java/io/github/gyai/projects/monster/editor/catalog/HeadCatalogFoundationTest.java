package io.github.gyai.projects.monster.editor.catalog;

import io.github.gyai.projects.monster.editor.HeadDefinitionRepository;
import io.github.gyai.projects.monster.editor.HeadDefinitionValidator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class HeadCatalogFoundationTest {
    private HeadCatalogFoundationTest() { }

    public static void main(String[] args) throws Exception {
        disabledAndSecretSafety();
        unknownProviderFailsClosed();
        queryPageAndCapabilities();
        cacheRateRetryAndTimeout();
        providerResponseSafety();
        importAndLocalFallback();
        thumbnailSecurity();
    }

    private static void disabledAndSecretSafety() {
        HeadCatalogSettings disabled = HeadCatalogSettings.disabled();
        MinecraftHeadsProvider provider = new MinecraftHeadsProvider(disabled);
        assert !provider.enabled();
        assert provider.statusMessage().contains("無効");
        HeadCatalogSettings missingApp = new HeadCatalogSettings(true,
                "MINECRAFT_HEADS", "super-secret", "", "ja", 60_000, 20, 1, 0);
        provider = new MinecraftHeadsProvider(missingApp);
        assert !provider.enabled();
        assert provider.statusMessage().contains("App UUID");
        HeadCatalogSettings missingKey = new HeadCatalogSettings(true,
                "MINECRAFT_HEADS", "", "app-uuid", "ja", 60_000, 20, 1, 0);
        provider = new MinecraftHeadsProvider(missingKey);
        assert !provider.enabled();
        assert provider.statusMessage().contains("API Key");
        assert !missingApp.toString().contains("super-secret");
        assert !missingKey.toString().contains("app-uuid");
        HeadCatalogPage disabledPage = new HeadCatalogService(provider, missingApp)
                .search("tester", HeadCatalogQuery.of("", "", 0, 24)).join();
        assert disabledPage.entries().isEmpty();
    }

    private static void unknownProviderFailsClosed() {
        HeadCatalogSettings unknown = new HeadCatalogSettings(true,
                "UNSUPPORTED_PROVIDER", "secret", "app", "ja", 60_000, 20, 1, 0);
        HeadCatalogProvider provider = new MinecraftHeadsProvider(unknown);
        assert !provider.enabled();
        assert provider.capabilities().isEmpty();
        assert provider.statusMessage().contains("未対応");
        HeadCatalogService service = new HeadCatalogService(provider, HeadCatalogSettings.disabled());
        assert service.search("tester", HeadCatalogQuery.of("", "", 0, 10))
                .join().entries().isEmpty();
        try {
            service.detail("tester", "entry").join();
            assert false;
        } catch (RuntimeException expected) {
            assert expected.getCause() == null || expected.getCause().getMessage().contains("未対応");
        }
    }

    private static void queryPageAndCapabilities() {
        HeadCatalogQuery query = HeadCatalogQuery.of("  Pirate   Head\n", "BAD!", -5, 999);
        assert query.text().equals("Pirate Head");
        assert query.category().isEmpty();
        assert query.page() == 0 && query.pageSize() == 48;
        assert HeadCatalogQuery.of("界".repeat(100), "", 0, 10).text()
                .getBytes(StandardCharsets.UTF_8).length <= HeadCatalogQuery.MAX_QUERY_LENGTH;
        HeadCatalogEntry textured = entry("one", texture());
        HeadCatalogPage page = new HeadCatalogPage(List.of(textured), 0, 24,
                1, false, List.of());
        assert page.entries().getFirst().textureValue().isEmpty();
        HeadCatalogEntry bounded = new HeadCatalogEntry("provider", "id",
                "x".repeat(1_000), "category", java.util.Collections.nCopies(100, "tag"),
                "https://127.0.0.1/private.png", "x".repeat(20_000), false);
        assert bounded.displayName().length() <= 128;
        assert bounded.tags().size() <= 16;
        assert bounded.thumbnailUrl().isBlank() && bounded.textureValue().isBlank();
        HeadCatalogPage capped = new HeadCatalogPage(
                java.util.Collections.nCopies(100, textured), 0, 4, 100, true,
                java.util.Collections.nCopies(20, "warning"));
        assert capped.entries().size() == 4 && capped.warnings().size() == 8;
        assert new HeadCatalogPage(java.util.Arrays.asList(null, textured), 0, 4,
                1, false, List.of()).entries().size() == 1;
        assert HeadCatalogPage.empty(null, "warning").pageSize() == 24;
        assert HeadCatalogQuery.of("x", "animals", 1, 10)
                .category().equals("animals");
        FakeProvider provider = new FakeProvider(Set.of(
                HeadCatalogCapability.SEARCH_HEADS,
                HeadCatalogCapability.BASIC_METADATA));
        HeadCatalogService service = new HeadCatalogService(provider,
                settings(5_000, 10, 1, 0));
        assert !service.canImport();
        assert service.importDisabledReason().contains("Texture Value");
        provider.capabilities = Set.of(HeadCatalogCapability.SEARCH_HEADS,
                HeadCatalogCapability.TEXTURE_VALUE);
        assert service.canImport();
    }

    private static void cacheRateRetryAndTimeout() {
        MutableClock clock = new MutableClock();
        HeadCatalogCache<String> cache = new HeadCatalogCache<>(100, 2, clock);
        cache.put("a", "one");
        assert cache.get("a").orElseThrow().equals("one");
        clock.millis += 101;
        assert cache.get("a").isEmpty();
        assert cache.size() == 0;
        cache.put(null, "ignored");
        assert cache.get(null).isEmpty();
        cache.put("a", "one"); cache.put("b", "two"); cache.get("a");
        cache.put("c", "three");
        assert cache.size() == 2 && cache.get("b").isEmpty();

        HeadCatalogRateLimiter limiter = new HeadCatalogRateLimiter(2, 1_000, clock);
        assert limiter.acquire("player") && limiter.acquire("player");
        assert !limiter.acquire("player");
        clock.millis += 1_001;
        assert limiter.acquire("player");

        FakeProvider retry = new FakeProvider(Set.of(HeadCatalogCapability.SEARCH_HEADS));
        retry.failures = 2;
        HeadCatalogService retryService = new HeadCatalogService(retry,
                settings(5_000, 10, 1, 2));
        assert retryService.search("retry", HeadCatalogQuery.of("x", "", 0, 10))
                .join().entries().size() == 1;
        assert retry.calls.get() == 3;

        FakeProvider timeout = new FakeProvider(Set.of(HeadCatalogCapability.SEARCH_HEADS));
        timeout.neverComplete = true;
        long start = System.nanoTime();
        try {
            new HeadCatalogService(timeout, settings(5_000, 10, 1, 0))
                    .search("timeout", HeadCatalogQuery.of("x", "", 0, 10)).join();
            assert false;
        } catch (RuntimeException expected) {
            assert (System.nanoTime() - start) / 1_000_000 >= 900;
        }
    }

    private static void providerResponseSafety() {
        FakeProvider noTexture = new FakeProvider(Set.of(
                HeadCatalogCapability.SEARCH_HEADS));
        HeadCatalogService service = new HeadCatalogService(noTexture,
                settings(5_000, 10, 1, 0));
        assert service.search("subject", null).join().pageSize() == 24;
        assert service.detail("subject", "one").join().textureValue().isEmpty();
        noTexture.wrongDetailId = true;
        try {
            service.detail("subject", "two").join();
            assert false;
        } catch (RuntimeException expected) {
            assert expected.getCause() == null
                    || expected.getCause().getMessage().contains("Entry ID");
        }

        FakeProvider empty = new FakeProvider(Set.of(HeadCatalogCapability.SEARCH_HEADS));
        empty.emptyPage = true;
        try {
            new HeadCatalogService(empty, settings(5_000, 10, 1, 0))
                    .search("subject", HeadCatalogQuery.of("", "", 0, 10)).join();
            assert false;
        } catch (RuntimeException expected) {
            assert expected.getCause() == null
                    || expected.getCause().getMessage().contains("空");
        }
    }

    private static void importAndLocalFallback() throws Exception {
        var directory = Files.createTempDirectory("projects-head-catalog-");
        try {
            HeadDefinitionRepository repository = new HeadDefinitionRepository(directory,
                    new HeadDefinitionValidator(id -> false), message -> { });
            repository.reload();
            HeadCatalogImportService importer = new HeadCatalogImportService(repository);
            assert HeadCatalogImportService.generateId("Minecraft Heads", "AbC 12")
                    .equals("minecraft_heads_abc_12");
            var imported = importer.importEntry(entry("abc", texture()), "");
            assert imported.success();
            assert repository.get(imported.definition().id()) != null;
            var duplicateTags = importer.importEntry(new HeadCatalogEntry(
                    "minecraft_heads", "duplicate", "Duplicate", "pirate",
                    List.of("pirate"), "", texture(), false), "dedupe");
            assert duplicateTags.success();
            assert duplicateTags.definition().tags().equals(List.of("pirate"));
            assert duplicateTags.definition().sourceNote()
                    .equals("provider=minecraft_heads; entry=duplicate");
            assert !importer.importEntry(entry("abc", texture()),
                    imported.definition().id()).success();
            assert !importer.importEntry(entry("bad", "not-base64"), "bad").success();

            FakeProvider failing = new FakeProvider(Set.of(HeadCatalogCapability.SEARCH_HEADS));
            failing.failures = 99;
            try {
                new HeadCatalogService(failing, settings(5_000, 10, 1, 0))
                        .search("failure", HeadCatalogQuery.of("", "", 0, 10)).join();
            } catch (RuntimeException ignored) { }
            assert repository.get(imported.definition().id()) != null;
        } finally {
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void thumbnailSecurity() {
        assert !HeadThumbnailSecurity.safeUri("http://minecraft-heads.com/a.png");
        assert !HeadThumbnailSecurity.safeUri("file:///etc/passwd");
        assert !HeadThumbnailSecurity.safeUri("https://localhost/a.png");
        assert !HeadThumbnailSecurity.safeUri("https://127.0.0.1/a.png");
        assert !HeadThumbnailSecurity.safeUri("https://192.168.1.2/a.png");
        assert !HeadThumbnailSecurity.safeUri("https://textures.minecraft.net/".repeat(20));
        assert !HeadThumbnailSecurity.validContentType("text/html");
        assert HeadThumbnailSecurity.validContentType("image/png; charset=binary");
        assert !HeadThumbnailSecurity.validSize(HeadThumbnailSecurity.MAX_BYTES + 1L);
        assert HeadThumbnailSecurity.validSize(32_000);
    }

    private static HeadCatalogSettings settings(long ttl, int max, int timeout, int retries) {
        return new HeadCatalogSettings(true, "FAKE", "secret", "app", "ja",
                ttl, max, timeout, retries);
    }

    private static HeadCatalogEntry entry(String id, String texture) {
        return new HeadCatalogEntry("minecraft_heads", id, "Pirate", "people",
                List.of("pirate"), "", texture, false);
    }

    private static String texture() {
        String json = "{\"textures\":{\"SKIN\":{\"url\":"
                + "\"https://textures.minecraft.net/texture/abc123\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static final class FakeProvider implements HeadCatalogProvider {
        private Set<HeadCatalogCapability> capabilities;
        private final AtomicInteger calls = new AtomicInteger();
        private int failures;
        private boolean neverComplete;
        private boolean wrongDetailId;
        private boolean emptyPage;
        private FakeProvider(Set<HeadCatalogCapability> capabilities) {
            this.capabilities = capabilities;
        }
        @Override public String id() { return "fake"; }
        @Override public boolean enabled() { return true; }
        @Override public String statusMessage() { return ""; }
        @Override public Set<HeadCatalogCapability> capabilities() { return capabilities; }
        @Override public CompletableFuture<HeadCatalogPage> search(HeadCatalogQuery query) {
            calls.incrementAndGet();
            if (neverComplete) return new CompletableFuture<>();
            if (emptyPage) return CompletableFuture.completedFuture(null);
            if (failures-- > 0) return CompletableFuture.failedFuture(
                    new IllegalStateException("provider details must not leak"));
            return CompletableFuture.completedFuture(new HeadCatalogPage(
                    List.of(entry("one", texture())), query.page(), query.pageSize(),
                    1, false, List.of()));
        }
        @Override public CompletableFuture<HeadCatalogEntry> detail(String entryId) {
            return CompletableFuture.completedFuture(entry(
                    wrongDetailId ? "wrong" : entryId, texture()));
        }
    }

    private static final class MutableClock extends Clock {
        private long millis = 1_000;
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
