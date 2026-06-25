package com.ksefhelper.security.ratelimit;

import java.time.Duration;

public interface RateLimitStore {
    long consume(String key, int maximum, Duration window);
}
