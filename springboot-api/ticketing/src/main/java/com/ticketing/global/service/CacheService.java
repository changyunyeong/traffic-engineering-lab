package com.ticketing.global.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 캐시 저장
     */
    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * 캐시 조회
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 캐시 삭제
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 패턴 매칭으로 캐시 삭제
     */
    public void deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Deleted {} cache keys with pattern: {}", keys.size(), pattern);
        }
    }

    /**
     * TTL 조회
     */
    public Long getTTL(String key) {
        return redisTemplate.getExpire(key);
    }
}