package cn.nitrowater.account.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import cn.nitrowater.core.api.req.auth.DeviceInfo;

/**
 * Quick-login request: a phone number or email plus the code sent to it.
 *
 * <p>The channel is not supplied by the client. It is derived from the identifier's shape so a
 * phone code can never be replayed against an email address (or vice versa).</p>
 */
@Data
public class LoginCodeReq {
    @NotBlank(message = "{valid.need_target}")
    private String identifier;

    @NotBlank(message = "{valid.sms.empty}")
    private String code;

    /** Top-level fingerprint used by the hosted login form. */
    private String deviceFp;

    /** Optional structured device payload; {@link #deviceFp} wins when both are present. */
    private DeviceInfo deviceInfo;
}
