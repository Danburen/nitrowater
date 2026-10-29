package cn.nitrowater.bff.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the pool's bounded-growth contract: an insertion at capacity must report the evicted
 * account so its server-side token set can be revoked and removed.
 */
class AccountPoolTest {

    @Test
    void evictsOldestOtherAccountAndReturnsItsUid() {
        AccountPool pool = new AccountPool();
        pool.upsert(slot("a"), 2);
        pool.upsert(slot("b"), 2);

        List<String> evicted = pool.upsert(slot("c"), 2);

        assertThat(evicted).containsExactly("a");
        assertThat(pool.accounts()).extracting(AccountSlot::uid).containsExactly("b", "c");
        assertThat(pool.activeUid()).isEqualTo("c");
    }

    @Test
    void replacingSameAccountDoesNotEvict() {
        AccountPool pool = new AccountPool();
        pool.upsert(slot("a"), 2);
        pool.upsert(slot("b"), 2);

        List<String> evicted = pool.upsert(slot("a"), 2);

        assertThat(evicted).isEmpty();
        assertThat(pool.accounts()).extracting(AccountSlot::uid).containsExactly("b", "a");
        assertThat(pool.activeUid()).isEqualTo("a");
    }

    @Test
    void upsertMarksLatestAccountAsSessionOwnerAndClearsStaleLogoutHint() {
        AccountPool pool = new AccountPool();
        pool.upsert(slot("a"), 2);
        pool.setLogoutHint("stale-a");

        pool.upsert(slot("b"), 2);

        assertThat(pool.sessionOwnerUid()).isEqualTo("b");
        assertThat(pool.logoutHintIdToken()).isNull();
    }

    @Test
    void removingSessionOwnerClearsOwnerButKeepsLogoutHint() {
        AccountPool pool = new AccountPool();
        pool.upsert(slot("a"), 2);
        pool.upsert(slot("b"), 2);
        pool.setLogoutHint("fresh-b");

        pool.remove("b");

        assertThat(pool.sessionOwnerUid()).isNull();
        assertThat(pool.logoutHintIdToken()).isEqualTo("fresh-b");
        assertThat(pool.activeUid()).isEqualTo("a");
    }

    @Test
    void markSessionOwnerBackfillsOnlyWhenUnset() {
        AccountPool pool = new AccountPool();
        pool.upsert(slot("a"), 2);
        pool.upsert(slot("b"), 2);
        pool.remove("b");

        pool.markSessionOwner("legacy");
        pool.markSessionOwner("other");

        assertThat(pool.sessionOwnerUid()).isEqualTo("legacy");
    }

    @Test
    void removingNonOwnerKeepsSessionOwner() {
        AccountPool pool = new AccountPool();
        pool.upsert(slot("a"), 2);
        pool.upsert(slot("b"), 2);

        pool.remove("a");

        assertThat(pool.sessionOwnerUid()).isEqualTo("b");
        assertThat(pool.activeUid()).isEqualTo("b");
    }

    private static AccountSlot slot(String uid) {
        return new AccountSlot(uid, uid, uid, List.of(), List.of("ROLE_USER"), null, null, null,
                Map.of("sub", uid), "issuer", null, null, 0L);
    }
}
