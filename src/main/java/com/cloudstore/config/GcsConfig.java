package com.cloudstore.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * GCS client configuration.
 * Credentials are NEVER committed to Git. They are supplied either:
 *  - via GOOGLE_APPLICATION_CREDENTIALS env var pointing to a mounted service-account JSON, or
 *  - via app.gcs.credentials-location pointing to a classpath/file resource injected at deploy time
 *    (e.g. a Docker secret or Kubernetes secret volume), or
 *  - via Application Default Credentials when running on GCP infrastructure.
 */
@Configuration
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {

    @Bean
    public Storage storage(GcsProperties properties, ResourceLoader resourceLoader) throws IOException {
        StorageOptions.Builder builder = StorageOptions.newBuilder();

        String location = properties.credentialsLocation();
        if (location != null && !location.isBlank()) {
            Resource resource = resourceLoader.getResource(location);
            try (InputStream is = resource.getInputStream()) {
                builder.setCredentials(GoogleCredentials.fromStream(is));
            }
        }
        // If no explicit location is configured, the client falls back to
        // GOOGLE_APPLICATION_CREDENTIALS / Application Default Credentials.
        return builder.build().getService();
    }
}
