package cn.nitrowater.server.web.dto;

/**
 * One previous turn sent by the browser so follow-up questions keep their context.
 *
 * @param role {@code user} or {@code assistant}
 * @param content turn text
 */
public record AiChatTurn(String role, String content) {
}
