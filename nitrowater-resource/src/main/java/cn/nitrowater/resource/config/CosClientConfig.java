package cn.nitrowater.resource.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Tencent COS client used by {@code CosFileStorage}.
 *
 * <p>The whole COS backend is optional: the bean (and the storage implementation) is only created
 * when {@code app.resource.cos.secret-id} is configured, so S3/LOCAL-only deployments are unaffected.
 * Presigned URLs use the bucket's public {@code *.myqcloud.com} endpoint, which is directly
 * browser-reachable.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.resource.cos", name = "secret-id")
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
