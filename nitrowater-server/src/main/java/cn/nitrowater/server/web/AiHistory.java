package cn.nitrowater.server.web;

import cn.nitrowater.server.service.ai.model.ChatTurn;
import cn.nitrowater.server.web.dto.AiChatTurn;

import java.util.ArrayList;
import java.util.List;
import cn.nitrowater.lib.utils.StringUtil;

/** Shared conversion of the client-supplied turn list into the service's {@link ChatTurn}. */
public final class AiHistory {

    private AiHistory() {
    }

    /**
     * @param turns turns from the request (may be null)
     * @param maxTurns configured maximum number of user/assistant pairs
     * @return the last {@code maxTurns * 2} non-blank entries
     */
    public static List<ChatTurn> toTurns(List<AiChatTurn> turns, int maxTurns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        int maxEntries = Math.max(0, maxTurns) * 2;
        int from = maxEntries == 0 ? turns.size() : Math.max(0, turns.size() - maxEntries);
        List<ChatTurn> history = new ArrayList<>(turns.size() - from);
        for (int i = from; i < turns.size(); i++) {
            AiChatTurn turn = turns.get(i);
            if (turn != null && StringUtil.isNotBlank(turn.content())) {
                history.add(new ChatTurn(turn.role(), turn.content()));
            }
        }
        return List.copyOf(history);
    }
}
