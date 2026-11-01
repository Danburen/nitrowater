package cn.nitrowater.account.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReAuthInfoResp {
    private String maskedPhone;
    private String maskedEmail;
    private java.util.List<String> availableChannels;
}
