package cn.nitrowater.core.infrastructure.session;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The global revocation epoch shared by the session holders (BFF and account).
 *
 * <p>A session remembers the epoch it was created under and is rejected as soon as the current
 * epoch differs. Rotating this value is therefore how an operator "signs everybody out" at
 * runtime, without a redeploy. That is exactly why it lives in Redis instead of an environment
 * variable: an env knob can only be flipped by restarting, and a careless restart would invalidate
 * every 30-day session.</p>
 *
 * <p>The key is a cross-service contract (BFF and account must read the same value) and has no
 * TTL on purpose: expiring it would resurrect sessions that were meant to be dead. A missing key
 * means nobody has rotated the epoch in this Redis generation, so {@link #current()} reports
 * {@link #INITIAL}.</p>
 */
@Component
public class SessionEpochStore {

    /** Redis key holding the current epoch (a UUID after the first rotation). */
    public static final String KEY = "nitrowater:auth:session-epoch";

    /** Value used while no rotation has happened yet. */
    private static final String INITIAL = "0";

    private final StringRedisTemplate redis;

    public SessionEpochStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return the epoch every new session must be stamped with */
    public String current() {
        String epoch = this.redis.opsForValue().get(KEY);
        return (epoch != null) ? epoch : INITIAL;
    }

    /** @return the new epoch, which no previously created session matches any more */
    public String rotate() {
        String epoch = UUID.randomUUID().toString();
        this.redis.opsForValue().set(KEY, epoch);
        return epoch;
    }
}
