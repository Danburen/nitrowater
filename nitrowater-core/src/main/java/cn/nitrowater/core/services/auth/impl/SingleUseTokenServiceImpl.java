package cn.nitrowater.core.services.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import cn.nitrowater.lib.common.RedisKeyPrefix;
import cn.nitrowater.lib.common.TokenResult;
import cn.nitrowater.lib.common.cache.RedisKeyBuilder;
import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.core.exception.TokenInvalidOrExpireException;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.services.auth.SingleUseTokenService;

import java.time.Duration;

/**
 * One-time re-authentication token service.
 * <p>Used for sensitive operations (change phone/email, reset password, etc.)
 * to prove the user has completed a fresh SMS/email verification.</p>
 *
 * Redis key: {@code nitrowater:biz:op:re-auth:{scene}:{uuid}}
 * Value: userUid (String)
 * TTL: 5 minutes, consumed on first use ({@link SingleUseTokenService#consumeVerifyToken}).
 */
@Service
@RequiredArgsConstructor
public class SingleUseTokenServiceImpl implements SingleUseTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private final RedisHelperHolder redisHelper;

    @Override
    public TokenResult generateVerifyToken(String scene, Long userUid) {
        String token = StringUtil.noDashRandomUUIDString();
        redisHelper.set(verifyKey(token, scene), String.valueOf(userUid), TOKEN_TTL);
        return new TokenResult(token, TOKEN_TTL.toSeconds());
    }

    @Override
    public Long consumeVerifyToken(String token, String scene) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidOrExpireException();
        }
        String val = redisHelper.getAndDel(verifyKey(token, scene));
        if (val == null) {
            throw new TokenInvalidOrExpireException();
        }
        return Long.parseLong(val);
    }

    private String verifyKey(String token, String scene) {
        return RedisKeyBuilder.build(RedisKeyPrefix.RE_AUTH, scene, token);
    }
}
