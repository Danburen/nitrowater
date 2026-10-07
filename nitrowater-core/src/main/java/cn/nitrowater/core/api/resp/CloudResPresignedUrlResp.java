package cn.nitrowater.core.api.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class CloudResPresignedUrlResp {
    private String url;
    private Instant expireAt;
}
