package cn.nitrowater.core.web.legal;

import java.time.Instant;

/**
 * One published legal document, as listed by {@code GET /api/public/legal}.
 *
 * <p>The UI renders one link per entry in the order it receives them, so a deployment can publish any
 * number of documents without a code change.</p>
 *
 * @param key       addressable id, i.e. the file name without {@code .txt} ({@code terms.txt} →
 *                  {@code terms}); lowercase letters, digits and hyphens only
 * @param title     built-in label for the shipped documents, else the document's first line
 * @param updatedAt file modification time, i.e. the version date shown to the user
 */
public record LegalDocumentSummary(String key, String title, Instant updatedAt) {
}
