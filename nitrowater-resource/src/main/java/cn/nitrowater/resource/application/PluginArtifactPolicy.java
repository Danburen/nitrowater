package cn.nitrowater.resource.application;

import cn.nitrowater.resource.config.ResourceProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Strict allow-list policy for plugin artifacts uploaded from the admin UI.
 *
 * <p>Plugin downloads are executable Java archives, so the gate is deliberately narrow: only
 * {@code .jar} is accepted, the declared file name is reduced to a safe basename, the ZIP magic
 * number is verified before any byte reaches storage, and the size is capped independently from
 * end-user uploads. Everything else is rejected with a {@link ResourceException}.</p>
 */
@Component
public class PluginArtifactPolicy {

    /** Namespace under which every plugin artifact is published on the resource service. */
    public static final String PLUGIN_APP = "nitrowater";

    /** The only accepted extension (case-insensitive). */
    private static final String JAR_EXTENSION = ".jar";

    /** Longest accepted original file name; keeps object keys and headers bounded. */
    private static final int MAX_NAME_LENGTH = 200;

    /** Characters Windows cannot store in a file name (plus the path separators handled earlier). */
    private static final String FORBIDDEN_NAME_CHARS = "<>:\"|?*";

    /** Plugin keys double as the resource group, so they must match the business service's rule. */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    private final ResourceProperties properties;

    public PluginArtifactPolicy(ResourceProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates the plugin key used as the resource group.
     *
     * @param rawKey client-supplied plugin key
     * @return the trimmed key
     * @throws ResourceException when the key is missing or does not match the plugin-key rule
     */
    public String sanitizeKey(String rawKey) {
        String key = rawKey == null ? "" : rawKey.trim();
        if (key.isEmpty() || key.length() > 64 || !KEY_PATTERN.matcher(key).matches()) {
            throw new ResourceException(HttpStatus.BAD_REQUEST, "invalid_plugin_key",
                    "Plugin key must be 1-64 lowercase letters, digits or hyphens, starting with a letter or digit");
        }
        return key;
    }

    /**
     * Reduces an uploaded file name to a safe basename and enforces the jar-only allow-list.
     *
     * @param rawName client-supplied file name (may contain a path)
     * @return the sanitized basename, guaranteed to end with {@code .jar}
     * @throws ResourceException when the name is missing, unsafe or not a jar
     */
    public String sanitizeName(String rawName) {
        if (StringUtil.isBlank(rawName)) {
            throw new ResourceException(HttpStatus.BAD_REQUEST, "invalid_name", "File name is required");
        }
        String name = rawName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ResourceException(HttpStatus.BAD_REQUEST, "invalid_name",
                    "File name must be 1-" + MAX_NAME_LENGTH + " characters");
        }
        if (name.contains("..")) {
            throw new ResourceException(HttpStatus.BAD_REQUEST, "invalid_name", "File name must not contain '..'");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || FORBIDDEN_NAME_CHARS.indexOf(c) >= 0) {
                throw new ResourceException(HttpStatus.BAD_REQUEST, "invalid_name",
                        "File name contains an unsafe character");
            }
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(JAR_EXTENSION)) {
            throw unsupported("Only .jar plugin artifacts are accepted");
        }
        return name;
    }

    /**
     * @param size declared payload length
     * @throws ResourceException when the length is missing, non-positive or above the plugin cap
     */
    public void requireSize(long size) {
        if (size <= 0) {
            throw new ResourceException(HttpStatus.LENGTH_REQUIRED, "length_required",
                    "Content-Length is required and must be positive");
        }
        long max = properties.effectivePluginMaxSizeBytes();
        if (size > max) {
            throw new ResourceException(HttpStatus.CONTENT_TOO_LARGE, "too_large",
                    "Plugin artifact exceeds the maximum size of " + max + " bytes");
        }
    }

    /**
     * Verifies the ZIP magic number shared by every jar (local header, empty archive or spanned).
     *
     * @param head first bytes of the payload (at least 4)
     * @throws ResourceException when the payload is not a ZIP/jar
     */
    public void requireJarMagic(byte[] head) {
        boolean zip = head != null && head.length >= 4
                && head[0] == 'P' && head[1] == 'K'
                && ((head[2] == 3 && head[3] == 4)
                        || (head[2] == 5 && head[3] == 6)
                        || (head[2] == 7 && head[3] == 8));
        if (!zip) {
            throw unsupported("File content is not a valid jar (missing ZIP signature)");
        }
    }

    /** @return the effective plugin artifact size cap in bytes */
    public long maxBytes() {
        return properties.effectivePluginMaxSizeBytes();
    }

    private static ResourceException unsupported(String message) {
        return new ResourceException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_type", message);
    }
}
