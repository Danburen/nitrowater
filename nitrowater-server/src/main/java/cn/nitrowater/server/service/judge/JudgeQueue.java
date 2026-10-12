package cn.nitrowater.server.service.judge;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed judging queue (a simple list used as a FIFO).
 *
 * blocking {@code BLPOP}-style call so it idles cheaply.</p>
 */
@Component
public class JudgeQueue {

    /** Redis key holding pending submission ids. */
    static final String QUEUE_KEY = "nitrowater:judge:queue";

    private final StringRedisTemplate redis;

    public JudgeQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param submissionId submission to judge
     */
    public void enqueue(Long submissionId) {
        redis.opsForList().rightPush(QUEUE_KEY, String.valueOf(submissionId));
    }

    /**
     * Blocks up to {@code timeoutSeconds} for the next submission id.
     *
     * @param timeoutSeconds how long to block when the queue is empty
     * @return the next submission id, or {@code null} on timeout
     */
    public Long poll(int timeoutSeconds) {
        String value = redis.opsForList().leftPop(QUEUE_KEY, Duration.ofSeconds(timeoutSeconds));
        return value == null ? null : Long.valueOf(value);
    }
}
