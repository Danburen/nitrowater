package cn.nitrowater.resource.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Tencent COS client used by {@code CosFileStorage}.
 *
 * <p>The whole COS backend is optional and only registered when the credentials are actually
 * configured. The condition tests the values, not just the presence of the keys: compose injects
 * {@code COS_SECRET_ID} as an empty string when it is unset, and {@code @ConditionalOnProperty}
 * would treat that as "configured" and wire a client with empty credentials.</p>
 */
@Configuration
@ConditionalOnExpression("'${app.resource.cos.secret-id:}' != ''"
        + " and '${app.resource.cos.secret-key:}' != ''"
        + " and '${app.resource.cos.bucket:}' != ''")
public class CosClientConfig {

    /**
     * @param properties resource settings (COS credentials/bucket/region)
     * @return a COS client; shut down with the application context
     */
    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient(ResourceProperties properties) {
        ResourceProperties.Cos cfg = properties.cos();
        COSCredentials credentials = new BasicCOSCredentials(cfg.secretId(), cfg.secretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(cfg.region()));
        return new COSClient(credentials, clientConfig);
    }
}
