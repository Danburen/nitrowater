package cn.nitrowater.core.application.auth.impl;

import cn.nitrowater.core.application.auth.DeviceService;
import cn.nitrowater.lib.utils.codec.HashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * Salted device-identifier derivation for the token {@code did} claim.
 *
 * <p>The same fingerprint on the same account always maps to the same {@code did}, so downstreams
 * can recognise "the same device" without ever seeing the raw fingerprint.</p>
 */
@Service
public class DeviceServiceImpl implements DeviceService {

    private final String deviceHashSalt;

    DeviceServiceImpl(@Value("${device.salt}") String salt) {
        this.deviceHashSalt = Base64.getEncoder().encodeToString(salt.getBytes());
    }

    @Override
    public String computeDid(long userUid, String dfp) {
        return HashUtil.hashWithSalt(dfp + userUid, deviceHashSalt);
    }
}
