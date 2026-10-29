package cn.nitrowater.account.web.dto.response;

/**
 * Where the browser should go after a successful JSON (code) login.
 *
 * @param redirectUrl same-origin path, an absolute fallback configured by the server, or a saved
 *                    OIDC authorization URL
 */
public record LoginRedirectVo(String redirectUrl) {
}
