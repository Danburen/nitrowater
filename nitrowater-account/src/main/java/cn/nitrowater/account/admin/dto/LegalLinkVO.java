package cn.nitrowater.account.admin.dto;

/**
 * One legal link as returned by the admin API.
 *
 * @param title label shown on the login page
 * @param url   absolute URL of the application's own document
 */
public record LegalLinkVO(String title, String url) {
}
