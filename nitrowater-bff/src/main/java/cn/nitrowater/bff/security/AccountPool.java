package cn.nitrowater.bff.security;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ordered set of accounts held by one BFF session, plus the active account id.
 *
 * <p>Stored as a single session attribute so the account list and the active pointer can never
 * drift apart. A null {@code activeUid} with a non-empty list is invalid and is repaired by
 * {@link #remove(String)}.</p>
 */
public class AccountPool implements Serializable {

    /** Session attribute holding the pool. */
    public static final String SESSION_ATTRIBUTE = AccountPool.class.getName() + ".POOL";

    private final List<AccountSlot> accounts = new ArrayList<>();
    private String activeUid;
    private String sessionOwnerUid;
    private String logoutHintIdToken;

    /**
     * @return an immutable snapshot in insertion order
     */
    public List<AccountSlot> accounts() {
        return List.copyOf(accounts);
    }

    /**
     * @return the active account id, or {@code null} when the pool is empty
     */
    public String activeUid() {
        return activeUid;
    }

    public boolean isEmpty() {
        return accounts.isEmpty();
    }

    /**
     * Inserts or replaces an account and makes it active.
     *
     * <p>When the pool is full, the oldest <em>other</em> account is evicted; the account that just
     * signed in is never evicted by its own insertion.</p>
     *
     * @param slot account snapshot
     * @param max  maximum number of accounts to keep
     * @return the ids evicted to make room, in eviction order
     */
    public List<String> upsert(AccountSlot slot, int max) {
        accounts.removeIf(existing -> existing.uid().equals(slot.uid()));
        accounts.add(slot);
        List<String> evicted = new ArrayList<>();
        while (accounts.size() > Math.max(1, max)) {
            AccountSlot victim = accounts.stream()
                    .filter(existing -> !existing.uid().equals(slot.uid()))
                    .findFirst()
                    .orElse(null);
            if (victim == null) {
                break;
            }
            accounts.remove(victim);
            evicted.add(victim.uid());
        }
        activeUid = slot.uid();
        sessionOwnerUid = slot.uid();
        logoutHintIdToken = null;
        return evicted;
    }

    /**
     * @param uid account id
     * @return the stored account, if present
     */
    public Optional<AccountSlot> find(String uid) {
        if (uid == null) {
            return Optional.empty();
        }
        return accounts.stream().filter(account -> account.uid().equals(uid)).findFirst();
    }

    /**
     * Removes an account. If it was active, the first remaining account becomes active.
     *
     * @param uid account id
     * @return whether an account was removed
     */
    public boolean remove(String uid) {
        boolean removed = accounts.removeIf(account -> account.uid().equals(uid));
        if (removed && (activeUid == null || activeUid.equals(uid))) {
            activeUid = accounts.isEmpty() ? null : accounts.get(0).uid();
        }
        if (removed && uid.equals(sessionOwnerUid)) {
            sessionOwnerUid = null;
        }
        return removed;
    }

    /**
     * Moves the active pointer without touching the account list.
     *
     * @param uid account id
     * @return whether the account exists
     */
    public boolean activate(String uid) {
        if (find(uid).isEmpty()) {
            return false;
        }
        activeUid = uid;
        return true;
    }

    /**
     * @return the account that most recently authenticated at the Authorization Server
     */
    public String sessionOwnerUid() {
        return sessionOwnerUid;
    }

    /**
     * Backfills the AS session owner for pools serialized before this field existed.
     *
     * @param uid owner account id
     */
    public void markSessionOwner(String uid) {
        if (sessionOwnerUid == null) {
            sessionOwnerUid = uid;
        }
    }

    /**
     * Stores a fresh {@code id_token} for RP-Initiated Logout after the owning account has been
     * removed from the visible pool.
     *
     * @param idToken fresh id_token minted before the refresh token was revoked
     */
    public void setLogoutHint(String idToken) {
        this.logoutHintIdToken = idToken;
    }

    /**
     * @return the stored logout id_token, or {@code null}
     */
    public String logoutHintIdToken() {
        return logoutHintIdToken;
    }
}
