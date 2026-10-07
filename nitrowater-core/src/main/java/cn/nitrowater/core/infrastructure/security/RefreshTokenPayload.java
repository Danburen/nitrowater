package cn.nitrowater.core.infrastructure.security;

public record RefreshTokenPayload(long userUid,String deviceId) {
}
