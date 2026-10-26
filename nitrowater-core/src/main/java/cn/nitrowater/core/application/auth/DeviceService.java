package cn.nitrowater.core.application.auth;

/**
 * Derives the device identifier carried by the token {@code did} claim.
 *
 * <p>Only the derivation is left: the Phase-1 token shim owned the per-device Redis registry
 * (device sets, last-active timestamps, zombie sweeping) and went away with it. OIDC sessions are
 * tracked by the authorization server, and revocation works at account level.</p>
 */
public interface DeviceService {

    /**
     * @param userUid account the device belongs to
     * @param dfp     browser fingerprint supplied at login
     * @return a stable, salted device identifier
     */
    String computeDid(long userUid, String dfp);
}
