package cn.nitrowater.resource.application;

import cn.nitrowater.resource.domain.ResourceType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates uploads by extension and by content magic number.
 *
 * <p>R0 implements a small built-in signature table (PNG/JPEG/GIF/WEBP/PDF/ZIP/MP4) rather than
 * pulling in a heavy detector; unknown signatures are skipped rather than rejected, so the
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
            ResourceType.ARCHIVE, Set.of("zip", "rar", "7z", "tar", "gz"));

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
     * @param type resource category the caller expects
     * @param fileName file name being uploaded
     * @return whether the extension is allowed for the category
     */
    public boolean isExtensionAllowed(ResourceType type, String fileName) {
        Set<String> allowed = ALLOWED_EXTENSIONS.get(type);
        if (allowed == null) {
            return false;
        }
        return extensionOf(fileName).map(allowed::contains).orElse(false);
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
