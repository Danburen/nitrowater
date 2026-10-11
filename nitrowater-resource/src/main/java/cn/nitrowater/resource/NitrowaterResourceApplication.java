package cn.nitrowater.resource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Unified resource-management service.
 *
 * <p>Owns resource metadata ({@code nitrowater_resource} DB) and the pluggable storage backends
 * (MinIO / local filesystem), exposing presigned upload/download plus authorization. All
 * applications route their file I/O through this service.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NitrowaterResourceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NitrowaterResourceApplication.class, args);
    }
}
