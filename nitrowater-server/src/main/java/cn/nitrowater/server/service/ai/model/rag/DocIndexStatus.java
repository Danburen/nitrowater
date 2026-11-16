package cn.nitrowater.server.service.ai.model.rag;

/**
 * Relationship between one markdown file on disk and the stored vector index.
 *
 * <p>The inventory is a join of two sources: the filesystem (what the corpus mount currently holds)
 * and the {@code ai_doc_index} table (what was embedded at the last successful index run). This
 * enum names the four possible outcomes, which is what an administrator needs to decide whether a
 * re-index is required.</p>
 */
public enum DocIndexStatus {

    /** On disk and indexed with an identical content hash. */
    INDEXED,
    /** On disk and indexed, but the markdown changed after the last index run. */
    STALE,
    /** On disk but never indexed (new file, or an index run that has not happened yet). */
    MISSING,
    /** Indexed but no longer on disk (deleted or renamed); its vectors will disappear on re-index. */
    ORPHAN;

    /**
     * @param value case-insensitive status name
     * @return the matching status, or {@code null} when the value is blank or unknown
     */
    public static DocIndexStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DocIndexStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }
}
