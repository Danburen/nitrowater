package cn.nitrowater.resource.service;

import cn.nitrowater.lib.common.io.FileExtension;
import cn.nitrowater.lib.common.io.ResourceType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates uploads by extension and by content magic number, and resolves the media type the
 * service stores / serves.
 *
 * <p>The extension → category allow-list is this module's upload <em>policy</em> (it is deliberately
 * narrower than {@link FileExtension}: only the file kinds the platform accepts). The extension →
 * MIME mapping is not duplicated here: it comes from the shared {@link FileExtension} table, with a
 * small override map for the two kinds that table does not carry (SVG, JAR). Keeping one MIME table
 * in {@code nitrowater-lib} means a file served by the resource service and a file named by another
 * module can never disagree on its media type.</p>
 *
 * <p>Magic-number sniffing is a small built-in signature table (PNG/JPEG/GIF/BMP/WEBP/PDF/ZIP/MP4)
 * rather than a heavy detector; unknown signatures are skipped rather than rejected, so the
 * extension + declared MIME checks remain the primary gate.</p>
 */
@Component
public class FileTypeValidator {

    private static final Map<ResourceType, Set<String>> ALLOWED_EXTENSIONS = Map.of(
            ResourceType.IMAGE, Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg"),
            ResourceType.VIDEO, Set.of("mp4", "webm", "mov"),
            ResourceType.AUDIO, Set.of("mp3", "wav", "ogg"),
            ResourceType.TEXT, Set.of("txt", "md", "csv", "json", "log"),
            ResourceType.DOCUMENT, Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"),
            ResourceType.ARCHIVE, Set.of("zip", "rar", "7z", "tar", "gz", "jar"));

    /**
     * Media types the shared {@link FileExtension} table does not define.
     *
     * <p>{@code svg} is intentionally upload-only (it is served as an attachment, see
     * {@link #isInlineSafeMimeType(String)}); {@code jar} is the plugin-artifact type, whose exact
     * media type is part of the download contract.</p>
     */
    private static final Map<String, String> EXTRA_MIME_BY_EXTENSION = Map.of(
            "svg", "image/svg+xml",
            "jar", "application/java-archive");

    /** Media types that must never be rendered inline: they can execute script in the browser. */
    private static final Set<String> INLINE_UNSAFE_MIME_TYPES = Set.of(
            "image/svg+xml", "text/html", "application/xhtml+xml",
            "text/xml", "application/xml", "application/javascript", "text/javascript");

    /**
     * @param fileName file name (extension is read from it)
     * @return the lower-cased extension, or empty when there is none
     */
    public Optional<String> extensionOf(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(fileName.substring(dot + 1).toLowerCase());
    }

    /**
     * @return the resource type whose collection allows the given extension, or empty
     */
    public Optional<ResourceType> typeByExtension(String fileName) {
        return extensionOf(fileName).flatMap(ext -> ALLOWED_EXTENSIONS.entrySet().stream()
                .filter(e -> e.getValue().contains(ext))
                .map(Map.Entry::getKey)
                .findFirst());
    }

    /**
     * Resolves the media type to store / serve, <em>from the extension</em> rather than from the
     * client-declared content type.
     *
     * @param fileName file name being uploaded
     * @return the mapped media type, or empty when the extension is unknown
     */
    public Optional<String> mimeTypeByExtension(String fileName) {
        return extensionOf(fileName).map(FileTypeValidator::mimeOf);
    }

    /**
     * @param mimeType a media type string (parameters such as {@code ;charset=...} are ignored)
     * @return whether the media type is safe to serve inline, i.e. it cannot execute script
     */
    public static boolean isInlineSafeMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        int semi = mimeType.indexOf(';');
        String base = (semi >= 0 ? mimeType.substring(0, semi) : mimeType).trim().toLowerCase();
        return !INLINE_UNSAFE_MIME_TYPES.contains(base);
    }

    /**
     * Sniffs the content magic number.
     *
     * @param head first bytes of the content (at least 12 recommended)
     * @return the detected category, or empty when the signature is unknown
     */
    public Optional<ResourceType> detectByMagic(byte[] head) {
        if (head == null || head.length < 4) {
            return Optional.empty();
        }
        if (startsWith(head, 0x89, 0x50, 0x4E, 0x47)) {
            return Optional.of(ResourceType.IMAGE);          // PNG
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(ResourceType.IMAGE);          // JPEG
        }
        if (startsWith(head, 0x47, 0x49, 0x46, 0x38)) {
            return Optional.of(ResourceType.IMAGE);          // GIF8
        }
        if (startsWith(head, 0x42, 0x4D)) {
            return Optional.of(ResourceType.IMAGE);          // BMP
        }
        if (head.length >= 12 && startsWith(head, 0x52, 0x49, 0x46, 0x46)
                && head[8] == 0x57 && head[9] == 0x45 && head[10] == 0x42 && head[11] == 0x50) {
            return Optional.of(ResourceType.IMAGE);          // WEBP
        }
        if (startsWith(head, 0x25, 0x50, 0x44, 0x46)) {
            return Optional.of(ResourceType.DOCUMENT);       // %PDF
        }
        if (startsWith(head, 0x50, 0x4B, 0x03, 0x04)) {
            return Optional.of(ResourceType.ARCHIVE);        // ZIP (also docx/xlsx/pptx)
        }
        if (head.length >= 12 && head[4] == 0x66 && head[5] == 0x74 && head[6] == 0x79 && head[7] == 0x70) {
            return Optional.of(ResourceType.VIDEO);          // ISO-BMFF (...ftyp)
        }
        return Optional.empty();
    }

    /** @return the media type for an extension: the module override first, else the shared table */
    private static String mimeOf(String extension) {
        String extra = EXTRA_MIME_BY_EXTENSION.get(extension);
        return extra != null ? extra : FileExtension.fromExt(extension).getMimeType();
    }

    private static boolean startsWith(byte[] head, int... signature) {
        if (head.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
