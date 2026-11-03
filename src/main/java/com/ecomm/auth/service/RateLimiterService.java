package com.ecomm.auth.service;

import com.ecomm.auth.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String LOGIN_ATTEMPT_PREFIX = "auth:ratelimit:login:";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final RedisTemplate<String, Object> redisTemplate;

    public RateLimiterService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkLoginRateLimit(String key) {
        try {
            String redisKey = LOGIN_ATTEMPT_PREFIX + key;
            Object attemptsObj = redisTemplate.opsForValue().get(redisKey);
            if (attemptsObj != null) {
                int attempts = Integer.parseInt(attemptsObj.toString());
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    Long ttl = redisTemplate.getExpire(redisKey);
                    long minutesLeft = (ttl != null && ttl > 0) ? (ttl / 60) + 1 : 15;
                    log.warn("Rate limit exceeded for key: {}. Locked out for ~{} minutes", key, minutesLeft);
                    throw new RateLimitExceededException(
                        "Too many failed login attempts. Account temporarily locked for " + minutesLeft + " minutes."
                    );
                }
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error reading rate limit from Redis: {}", e.getMessage());
        }
    }

    public void recordFailedAttempt(String key) {
        try {
            String redisKey = LOGIN_ATTEMPT_PREFIX + key;
            Long current = redisTemplate.opsForValue().increment(redisKey);
            if (current != null && current == 1) {
                redisTemplate.expire(redisKey, LOCKOUT_DURATION);
            }
            log.debug("Recorded failed attempt #{} for key: {}", current, key);
        } catch (Exception e) {
            log.error("Failed to record failed login attempt in Redis: {}", e.getMessage());
        }
    }

    public void clearLoginAttempts(String key) {
        try {
            String redisKey = LOGIN_ATTEMPT_PREFIX + key;
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.error("Failed to clear login attempts in Redis: {}", e.getMessage());
        }
    }
}

