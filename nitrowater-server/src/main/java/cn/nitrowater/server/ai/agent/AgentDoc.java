package cn.nitrowater.server.ai.agent;

import cn.nitrowater.server.ai.DocSource;
import org.springframework.ai.document.Document;

/**
 * A retrieved chunk with its stable citation number for the current agent turn.
 *
 * @param index citation number assigned by {@link AgentTrace}
 * @param document the retrieved chunk ({@code null} for web results)
 * @param source the citation exposed to the UI
 */
public record AgentDoc(int index, Document document, DocSource source) {
}
