package com.ksefhelper.security.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis")
public class RedisRateLimitStore implements RateLimitStore {
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if count > tonumber(ARGV[2]) then
              return ttl
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisRateLimitStore(
            StringRedisTemplate redisTemplate,
            @Value("${app.rate-limit.redis-key-prefix:ksef-helper:rate-limit:}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public long consume(String key, int maximum, Duration window) {
        Long retryAfterMillis = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(keyPrefix + key),
                Long.toString(window.toMillis()),
                Integer.toString(maximum)
        );
        if (retryAfterMillis == null || retryAfterMillis <= 0) {
            return 0;
        }
        return Math.max(1, (retryAfterMillis + 999) / 1000);
    }
}
