package cn.nitrowater.account.admin.dto;

/**
 * Result of registering or updating an application.
 *
 * <p>{@code clientSecret} is the <em>only</em> time a plaintext secret is exposed. It is non-null
 * only when this operation minted a new secret: on create for a confidential client, or on update
 * when a public client was switched to confidential. It is {@code null} otherwise (public client,
 * or an update that preserved the existing secret).</p>
 *
 * @param client       the persisted application view
 * @param clientSecret the newly generated plaintext secret, or {@code null} when none was minted
 */
public record ClientRegistrationVO(ClientVO client, String clientSecret) {
}
