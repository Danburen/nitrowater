package cn.nitrowater.core.application.auth;

import jakarta.servlet.http.HttpSession;

public interface CaptchaService {
    /**
     * Render a fresh challenge and bind it to the caller's HTTP session.
     *
     * @param session session that owns the challenge; {@code null} means the challenge cannot be
     *                verified later, so it is only rendered (image still returned)
     * @return uuid of the stored challenge plus the rendered image
     */
    LineCaptchaResult generateCaptcha(HttpSession session);

    /**
     * Verify the code the user typed against the challenge bound to this session.
     *
     * <p>One challenge, one attempt: the challenge is consumed whether or not the code matched.</p>
     *
     * @param session session that asked for the image
     * @param code    user input
     * @return {@code true} only for the code of the challenge currently bound to the session
     */
    boolean verifyCode(HttpSession session, String code);
}
