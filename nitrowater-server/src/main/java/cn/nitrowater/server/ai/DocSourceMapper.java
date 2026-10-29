package cn.nitrowater.server.ai;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps vector-store documents to API citations. Shared by the plain RAG path and the agent tools
 * so both surfaces expose the same {@link DocSource} shape.
 */
public final class DocSourceMapper {

    private static final int SNIPPET_CHARS = 180;

    private DocSourceMapper() {
    }

    /**
     * @param documents retrieved chunks, in relevance order
     * @return citations numbered from 1
     */
    public static List<DocSource> from(List<Document> documents) {
        List<DocSource> sources = new ArrayList<>(documents.size());
        int index = 1;
        for (Document document : documents) {
            sources.add(from(index++, document));
        }
        return List.copyOf(sources);
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
