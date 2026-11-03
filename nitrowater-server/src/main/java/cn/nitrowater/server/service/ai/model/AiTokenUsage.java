package cn.nitrowater.server.service.ai.model;

/** Provider usage or a marked estimate for one AI turn. */
public record AiTokenUsage(long inputTokens, long outputTokens, boolean estimated) {

    /** @return total billed or estimated tokens */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
