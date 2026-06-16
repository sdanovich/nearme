package com.example.nearme.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Thin string key/value cache over Redis with per-key TTL, shared across all
 * backend instances and surviving restarts.
 *
 * Resilient by design: every operation swallows Redis failures and behaves as a
 * cache miss, so a Redis outage degrades performance (more upstream calls) but
 * never breaks a request — matching the error-pipeline discipline.
 */
@Component
public class RedisCache {

    private static final Logger log = LoggerFactory.getLogger(RedisCache.class);

    private final StringRedisTemplate redis;

    public RedisCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (Exception e) {
            log.debug("redis get failed for {}: {}", key, e.toString());
            return Optional.empty();
        }
    }

    public void put(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.debug("redis put failed for {}: {}", key, e.toString());
        }
    }

    /** True if the key currently exists (treats Redis errors as "absent"). */
    public boolean exists(String key) {
        return get(key).isPresent();
    }
}
