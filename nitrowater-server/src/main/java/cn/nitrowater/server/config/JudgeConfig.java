package cn.nitrowater.server.config;

import cn.nitrowater.server.config.properties.JudgeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables the {@code app.judge.*} judging configuration. */
@Configuration
@EnableConfigurationProperties(JudgeProperties.class)
public class JudgeConfig {
}
