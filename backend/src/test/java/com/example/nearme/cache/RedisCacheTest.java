package com.example.nearme.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;

    private RedisCache cache;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
        cache = new RedisCache(redis);
    }

    @Test
    void getReturnsValueWhenPresent() {
        when(ops.get("k")).thenReturn("v");
        assertThat(cache.get("k")).contains("v");
    }

    @Test
    void getReturnsEmptyWhenMissing() {
        when(ops.get("k")).thenReturn(null);
        assertThat(cache.get("k")).isEmpty();
    }

    @Test
    void getSwallowsRedisFailureAsMiss() {
        when(ops.get("k")).thenThrow(new RuntimeException("redis down"));
        assertThat(cache.get("k")).isEmpty();
    }

    @Test
    void putDelegatesWithTtl() {
        Duration ttl = Duration.ofMinutes(10);
        cache.put("k", "v", ttl);
        verify(ops).set("k", "v", ttl);
    }

    @Test
    void putSwallowsRedisFailure() {
        // void method: stub to throw via doThrow(...).when(...)
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(ops).set("k", "v", Duration.ofMinutes(1));
        assertThatCode(() -> cache.put("k", "v", Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void existsIsTrueOnlyWhenValuePresent() {
        when(ops.get("present")).thenReturn("x");
        when(ops.get("absent")).thenReturn(null);
        assertThat(cache.exists("present")).isTrue();
        assertThat(cache.exists("absent")).isFalse();
    }
}
