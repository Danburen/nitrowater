package cn.nitrowater.account.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One legal document link supplied by the admin console when creating or updating an application.
 *
 * <p>The list is ordered: the login page renders the links in the same order. The document itself
 * is hosted by the application, so only its title and absolute URL are stored.</p>
 *
 * @param title label shown on the login page (e.g. {@code 用户协议})
 * @param url   absolute http(s) URL of the application's own document
 */
public record LegalLinkReq(
        @NotBlank(message = "legal link title is required")
        @Size(max = 64, message = "legal link title must be at most 64 characters")
        String title,

        @NotBlank(message = "legal link url is required")
        @Size(max = 500, message = "legal link url must be at most 500 characters")
        @Pattern(regexp = "^https?://\\S+$", message = "legal link url must be an http(s) URL")
        String url) {
}
