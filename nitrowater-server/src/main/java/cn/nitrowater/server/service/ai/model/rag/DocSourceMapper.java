package cn.nitrowater.server.service.ai.model.rag;

import cn.nitrowater.server.service.ai.rag.DocChunkBuilder;
import org.springframework.ai.document.Document;

import java.util.Map;
import java.util.Objects;

/**
 * Maps vector-store documents to API citations. Used by the agent tools so every surface exposes
 * the same {@link DocSource} shape.
 */
public final class DocSourceMapper {

    private static final int SNIPPET_CHARS = 180;

    private DocSourceMapper() {
    }

    /**
     * @param index citation number
     * @param document retrieved chunk
     * @return the citation for that chunk
     */
    public static DocSource from(int index, Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new DocSource(
                index,
                "doc",
                text(metadata.get(DocChunkBuilder.META_DOC_TITLE), "未命名文档"),
                text(metadata.get(DocChunkBuilder.META_SECTION), ""),
                text(metadata.get(DocChunkBuilder.META_DOC_PATH), ""),
                text(metadata.get(DocChunkBuilder.META_URL), ""),
                document.getScore() == null ? 0.0 : document.getScore(),
                snippet(document.getText()));
    }

    /**
     * @param text chunk text
     * @return a single-line preview
     */
    public static String snippet(String text) {
        String flat = Objects.requireNonNullElse(text, "").replaceAll("\\s+", " ").trim();
        return flat.length() <= SNIPPET_CHARS ? flat : flat.substring(0, SNIPPET_CHARS) + "…";
    }

    private static String text(Object value, String fallback) {
        String text = value == null ? "" : value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }
}
