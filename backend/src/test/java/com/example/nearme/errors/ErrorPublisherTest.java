package com.example.nearme.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrorPublisherTest {

    @Mock StringRedisTemplate redis;
    @Mock StreamOperations<String, Object, Object> stream;

    private ErrorPublisher publisher;

    @BeforeEach
    void setUp() {
        // findAndRegisterModules() picks up jackson-datatype-jsr310 so Instant
        // serializes (matching the Spring-configured ObjectMapper in production).
        publisher = new ErrorPublisher(redis, new ObjectMapper().findAndRegisterModules());
    }

    private ErrorEvent sampleEvent() {
        return new ErrorEvent("evt-1", Instant.parse("2026-06-16T00:00:00Z"),
                "EXCEPTION", 500, "GET", "/api/stations/nearby",
                "java.lang.RuntimeException", "boom", "stack...", "trace-1");
    }

    @Test
    void publishAsyncWritesToStreamAndTrims() {
        when(redis.opsForStream()).thenReturn(stream);

        publisher.publishAsync(sampleEvent());

        verify(stream).add(any());
        verify(stream).trim(eq(ErrorPublisher.STREAM), eq(50_000L), eq(true));
    }

    @Test
    void publishAsyncSwallowsBrokerFailure() {
        when(redis.opsForStream()).thenReturn(stream);
        when(stream.add(any())).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> publisher.publishAsync(sampleEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    void captureAlwaysAttemptsToPublish() {
        // capture() logs locally then dispatches to publishAsync (sync in a unit test)
        lenient().when(redis.opsForStream()).thenReturn(stream);

        assertThatCode(() -> publisher.capture(sampleEvent()))
                .doesNotThrowAnyException();

        verify(stream).add(any());
    }
}
