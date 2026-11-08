package cn.nitrowater.server.service.ai.model;

/**
 * Result of a memory save attempt.
 *
 * @param status {@code saved}, {@code updated} or {@code rejected}
 * @param memory saved or updated entry, or {@code null} when rejected
 * @param reason rejection reason, or {@code null}
 */
public record MemorySaveResult(String status, MemoryView memory, String reason) {

    /**
     * @param memory stored entry
     * @return a {@code saved} result
     */
    public static MemorySaveResult saved(MemoryView memory) {
        return new MemorySaveResult("saved", memory, null);
    }

    /**
     * @param memory updated entry
     * @return an {@code updated} result
     */
    public static MemorySaveResult updated(MemoryView memory) {
        return new MemorySaveResult("updated", memory, null);
    }

    /**
     * @param reason why the entry was refused
     * @return a {@code rejected} result
     */
    public static MemorySaveResult rejected(String reason) {
        return new MemorySaveResult("rejected", null, reason);
    }
}
