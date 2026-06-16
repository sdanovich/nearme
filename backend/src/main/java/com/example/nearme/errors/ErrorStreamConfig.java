package com.example.nearme.errors;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.UUID;

/** Creates the consumer group and starts the error-stream listener. */
@Configuration
public class ErrorStreamConfig {

    private static final Logger log = LoggerFactory.getLogger(ErrorStreamConfig.class);

    @Value("${HOSTNAME:nearme-backend}")
    private String hostname;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> errorStreamContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redis,
            ErrorStreamConsumer consumer) {

        ensureGroup(redis);

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(2))
                .build();

        container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.receive(
                Consumer.from(ErrorStreamConsumer.GROUP, hostname + "-" + UUID.randomUUID()),
                StreamOffset.create(ErrorPublisher.STREAM, ReadOffset.lastConsumed()),
                consumer);
        container.start();
        log.info("Error stream consumer started on {}", ErrorPublisher.STREAM);
        return container;
    }

    /** Create the group with MKSTREAM so it works even before the first event. */
    private void ensureGroup(StringRedisTemplate redis) {
        try {
            redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) conn ->
                    conn.streamCommands().xGroupCreate(
                            ErrorPublisher.STREAM.getBytes(),
                            ErrorStreamConsumer.GROUP,
                            ReadOffset.from("0"),
                            true));
        } catch (Exception e) {
            log.debug("error-stream group create skipped: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (container != null) container.stop();
    }
}
