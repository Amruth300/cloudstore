package com.cloudstore.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String FILE_UPLOADED = "file.uploaded";
    public static final String FILE_DELETED = "file.deleted";
    public static final String FILE_SHARED = "file.shared";
    public static final String FILE_VERSION_CREATED = "file.version.created";

    @Bean
    public NewTopic fileUploadedTopic() {
        return TopicBuilder.name(FILE_UPLOADED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic fileDeletedTopic() {
        return TopicBuilder.name(FILE_DELETED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic fileSharedTopic() {
        return TopicBuilder.name(FILE_SHARED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic fileVersionCreatedTopic() {
        return TopicBuilder.name(FILE_VERSION_CREATED).partitions(3).replicas(1).build();
    }
}
