package cn.nitrowater.server.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Raised when the authenticated user has no daily token budget left.
 *
 * <p>It is a {@link ResponseStatusException} with status 429, so any endpoint maps it to a
 * rate-limit response even without a dedicated handler. {@link cn.nitrowater.server.web.AiExceptionHandler}
 * adds the machine-readable {@link #CODE} to the JSON body, and the streaming endpoints repeat the
 * same code in their {@code error} event, which is what the web client uses to switch the composer
 * into its exhausted state.</p>
 */
public class AiQuotaExceededException extends ResponseStatusException {

    /** Stable code shared by the JSON error body and the SSE error payload (per-user budget). */
    public static final String CODE = "token_quota";

    /** Stable code for the platform-wide daily ceiling (breaker). */
    public static final String GLOBAL_CODE = "global_quota";

    private final String code;
    private final long limit;
    private final long used;

    /**
     * @param reason user-facing explanation, sent verbatim to the client
     */
    public AiQuotaExceededException(String reason) {
        this(CODE, reason, 0L, 0L);
    }

    private AiQuotaExceededException(String code, String reason, long limit, long used) {
        super(HttpStatus.TOO_MANY_REQUESTS, reason);
        this.code = code;
        this.limit = limit;
        this.used = used;
    }

    /**
     * @param limit platform-wide daily ceiling
     * @param used tokens consumed today, including in-flight reservations
     * @return a rejection carrying {@link #GLOBAL_CODE}
     */
    public static AiQuotaExceededException global(long limit, long used) {
        return new AiQuotaExceededException(GLOBAL_CODE, "全站今日 AI 额度已用完，请明天再试", limit, used);
    }

    /** @return the stable machine-readable code ({@link #CODE} or {@link #GLOBAL_CODE}) */
    public String code() {
        return code;
    }

    /** @return the ceiling that was hit, or {@code 0} for the per-user budget */
    public long limit() {
        return limit;
    }

    /** @return tokens consumed when the rejection was raised, or {@code 0} */
    public long used() {
        return used;
    }
}
