package io.github.gyai.projects.monster.editor.catalog;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class HeadThumbnailSecurity {
    public static final int MAX_BYTES = 1_048_576;
    public static final int MAX_URL_BYTES = 512;
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "minecraft-heads.com", "www.minecraft-heads.com",
            "textures.minecraft.net");

    private HeadThumbnailSecurity() { }

    public static boolean safeUri(String raw) {
        if (raw == null || raw.isBlank()
                || raw.getBytes(StandardCharsets.UTF_8).length > MAX_URL_BYTES
                || raw.chars().anyMatch(Character::isISOControl)) return false;
        try {
            URI uri = URI.create(raw.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 || uri.getFragment() != null) return false;
            String host = uri.getHost();
            if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                return false;
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) return false;
            }
            return true;
        } catch (IllegalArgumentException | SecurityException | UnknownHostException exception) {
            return false;
        }
    }

    public static boolean validContentType(String contentType) {
        if (contentType == null) return false;
        String type = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return type.equals("image/png") || type.equals("image/jpeg")
                || type.equals("image/webp");
    }

    public static boolean validSize(long bytes) {
        return bytes > 0 && bytes <= MAX_BYTES;
    }
}
