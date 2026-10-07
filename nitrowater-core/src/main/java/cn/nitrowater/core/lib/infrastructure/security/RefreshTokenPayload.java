package cn.nitrowater.core.lib.infrastructure.security;

public record RefreshTokenPayload(long userUid,String deviceId) {
}
