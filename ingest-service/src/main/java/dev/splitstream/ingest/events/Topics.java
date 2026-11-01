package dev.splitstream.ingest.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Topic names are config, not constants, so race-service and ingest-service can be pointed at the same cluster
 * under different environments without a code change.
 */
@ConfigurationProperties("splitstream.topics")
public record Topics(String pings, String races, @DefaultValue("6") int pingPartitions, @DefaultValue("1") int replicas) {
}
