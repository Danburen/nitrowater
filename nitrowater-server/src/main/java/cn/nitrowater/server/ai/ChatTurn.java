package cn.nitrowater.server.ai;

/**
 * One previous conversation turn supplied by the browser.
 *
 * <p>History is stateless on the server: the client keeps the transcript and re-sends the last few
 * turns, which keeps the chat endpoint horizontally scalable and avoids another Redis data model.</p>
 *
 * @param role {@code user} or {@code assistant}
 * @param content turn text (blank turns are ignored)
 */
public record ChatTurn(String role, String content) {
}
