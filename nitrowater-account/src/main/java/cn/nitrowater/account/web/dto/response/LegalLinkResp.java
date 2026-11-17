package cn.nitrowater.account.web.dto.response;

/**
 * One legal document link rendered by the hosted auth pages.
 *
 * @param title label shown on the page (e.g. {@code 用户协议})
 * @param url   absolute URL on the application's own host
 */
public record LegalLinkResp(String title, String url) {
}
