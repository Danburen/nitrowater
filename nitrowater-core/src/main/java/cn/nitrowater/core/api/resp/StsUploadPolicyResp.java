package cn.nitrowater.core.api.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import cn.nitrowater.core.api.HttpMethod;

import java.io.Serializable;
import java.time.Instant;

/**
 * Upload response for STS-constrained direct upload.
 */
@Data
@AllArgsConstructor
public class StsUploadPolicyResp implements Serializable {
    private String key;
    private String url;
    private HttpMethod method;
    private long maxContentLength;
    private String allowedContentType;
    private Instant expiresAt;
}

