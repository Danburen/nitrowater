package cn.nitrowater.account.admin.dto;

/**
 * Result of resetting a confidential application's secret.
 *
 * <p>The plaintext is shown once here and never retrievable again.</p>
 *
 * @param clientSecret the newly generated plaintext secret
 */
public record ClientSecretVO(String clientSecret) {
}
