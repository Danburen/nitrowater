package cn.nitrowater.core.application.auth.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import cn.nitrowater.lib.common.cache.RedisKeyBuilder;
import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.application.auth.CaptchaService;
import cn.nitrowater.core.application.auth.LineCaptchaResult;

import java.time.Duration;

import static cn.nitrowater.lib.common.RedisKeyPrefix.VERIFY;

@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {
    /**
     * Session attribute holding the uuid of the challenge currently shown to this session.
     *
     * <p>The session - not the {@code CAPTCHA_KEY} cookie - is the single source of truth: a browser
     * may abort the image request, keep a stale cookie from an earlier challenge, or drop the cookie
     * entirely, and none of that may silently swap the challenge the user is looking at.</p>
     */
    public static final String SESSION_CAPTCHA_KEY = CaptchaServiceImpl.class.getName() + ".uuid";

    /** Ambiguous glyphs (0/O, 1/l/I, i in lowercase) are left out so a correct reading is not a typo. */
    private static final String CAPTCHA_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int CAPTCHA_LENGTH = 5;
    private static final int CAPTCHA_WIDTH = 140;
    private static final int CAPTCHA_HEIGHT = 44;
    private static final int CAPTCHA_LINE_COUNT = 4;

    private final RedisHelperHolder redisHelper;

    // -- Redis key builders --
    private static String captchaKey(String uuid) {
        return RedisKeyBuilder.build(VERIFY, "captcha", uuid);
    }

    @Override
    public LineCaptchaResult generateCaptcha(HttpSession session) {
        LineCaptcha lineCaptcha = generateLineCaptcha();
        String uuid = StringUtil.noDashRandomUUIDString();
        String code = lineCaptcha.getCode();
        redisHelper.set(captchaKey(uuid), code, Duration.ofMinutes(2));
        if (session != null) {
            String previous = currentUuid(session);
            // The replaced challenge is unreachable now; drop its Redis entry instead of leaving an
            // orphan key alive for the rest of its TTL.
            if (previous != null && !previous.equals(uuid)) {
                redisHelper.del(captchaKey(previous));
            }
            session.setAttribute(SESSION_CAPTCHA_KEY, uuid);
        }
        return new LineCaptchaResult(uuid, lineCaptcha);
    }

    private LineCaptcha generateLineCaptcha() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(
                CAPTCHA_WIDTH, CAPTCHA_HEIGHT, CAPTCHA_LENGTH, CAPTCHA_LINE_COUNT);
        // The factory above already drew a code from the default (ambiguous) alphabet, so swap the
        // generator and regenerate before anyone can read it.
        captcha.setGenerator(new RandomGenerator(CAPTCHA_CHARS, CAPTCHA_LENGTH));
        captcha.createCode();
        return captcha;
    }

    @Override
    public boolean verifyCode(HttpSession session, String code) {
        String uuid = session == null ? null : currentUuid(session);
        // No bound challenge (never requested, or already consumed) is a miss, not an exception.
        if (uuid == null || code == null) {
            return false;
        }
        // One challenge, one attempt: burn the binding and the Redis key either way, so a wrong guess
        // cannot be retried against the same image.
        session.removeAttribute(SESSION_CAPTCHA_KEY);
        String key = captchaKey(uuid);
        boolean verified = redisHelper.validateAndRemove(key, code);
        if (!verified) {
            redisHelper.del(key);
        }
        return verified;
    }

    private static String currentUuid(HttpSession session) {
        Object value = session.getAttribute(SESSION_CAPTCHA_KEY);
        return value instanceof String uuid ? uuid : null;
    }
}
