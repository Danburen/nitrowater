package cn.nitrowater.server.ai;

import java.util.List;

/**
 * A complete (non-streaming) answer.
 *
 * @param answer answer text
 * @param sources citations referenced by the answer
 * @param grounded whether the answer is based on retrieved documentation (false for the fixed
 *        "not found" reply, which is produced without calling the model)
 * @param usage provider-reported usage or a marked fallback estimate
 */
public record ChatAnswer(String answer, List<DocSource> sources, boolean grounded, AiTokenUsage usage) {
}
