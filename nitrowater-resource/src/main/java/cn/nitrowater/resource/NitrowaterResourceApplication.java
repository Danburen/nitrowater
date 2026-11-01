package cn.nitrowater.resource;

import org.springframework.boot.SpringApplication;
import cn.nitrowater.core.web.ApiResponseAdvice;
import cn.nitrowater.core.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Unified resource-management service.
 *
 * <p>Owns resource metadata ({@code nitrowater_resource} DB) and the pluggable storage backends
 * (MinIO / local filesystem), exposing presigned upload/download plus authorization. All
 * applications route their file I/O through this service.</p>
 */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@Import({ApiResponseAdvice.class, GlobalExceptionHandler.class})
@ConfigurationPropertiesScan
@EnableScheduling
public class NitrowaterResourceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NitrowaterResourceApplication.class, args);
    }
}
