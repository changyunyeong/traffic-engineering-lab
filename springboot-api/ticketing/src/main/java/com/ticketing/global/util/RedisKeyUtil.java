package com.ticketing.global.util;

public class RedisKeyUtil {

    public static String stockKey(Long ticketId) {
        return "ticket:stock:" + ticketId;
    }

    public static String lockKey(Long ticketId) {
        return "ticket:lock:" + ticketId;
    }

    public static String queueKey(Long ticketId) {
        return "queue:ticket:" + ticketId;
    }

    public static String eventCacheKey(Long eventId) {
        return "event:" + eventId;
    }

    public static String ticketCacheKey(Long ticketId) {
        return "ticket:" + ticketId;
    }

    public static String userRecommendationKey(Long userId) {
        return "recommendation:user:" + userId;
    }
}