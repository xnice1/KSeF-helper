package com.ksefhelper.security.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisRateLimitStoreIntegrationTest {
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisRateLimitStore store;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        store = new RedisRateLimitStore(new StringRedisTemplate(connectionFactory), "test:rate-limit:");
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @Test
    void sharesAnAtomicWindowThroughRedis() throws Exception {
        assertThat(store.consume("login:user", 2, Duration.ofSeconds(2))).isZero();
        assertThat(store.consume("login:user", 2, Duration.ofSeconds(2))).isZero();
        assertThat(store.consume("login:user", 2, Duration.ofSeconds(2))).isPositive();

        Thread.sleep(2100);

        assertThat(store.consume("login:user", 2, Duration.ofSeconds(2))).isZero();
    }
}
