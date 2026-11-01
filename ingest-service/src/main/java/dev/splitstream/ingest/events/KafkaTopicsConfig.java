package dev.splitstream.ingest.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class KafkaTopicsConfig {

    /** High-volume telemetry. Keyed per runner, so partitions bound race-service's parallelism. */
    @Bean
    NewTopic pingsTopic(Topics topics) {
        return TopicBuilder.name(topics.pings())
                .partitions(topics.pingPartitions())
                .replicas(topics.replicas())
                .build();
    }

    /** Reference data, read as a GlobalKTable. Compacted so it holds the latest state of every race forever. */
    @Bean
    NewTopic racesTopic(Topics topics) {
        return TopicBuilder.name(topics.races())
                .partitions(1)
                .replicas(topics.replicas())
                .compact()
                .build();
    }
}
