package cn.nitrowater.account.service;

import cn.nitrowater.account.infra.ClientLegalLink;

/**
 * A legal link as supplied by the caller (admin console or the first-party seeder).
 *
 * <p>Separate from {@link ClientLegalLink} so callers never have to fabricate an entity id just
 * to describe what they want stored.</p>
 *
 * @param title label shown on the login page
 * @param url   absolute URL of the application's own document
 */
public record ClientLegalLinkInput(String title, String url) {
}
