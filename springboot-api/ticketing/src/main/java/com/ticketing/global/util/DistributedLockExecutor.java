package com.ticketing.global.util;

import com.ticketing.global.exception.domain.LockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockExecutor {

    private final RedissonClient redissonClient;

    /**
     * 분산 락으로 보호된 작업 실행
     *
     * @param lockKey 락 키
     * @param waitTime 대기 시간 (초)
     * @param leaseTime 락 보유 시간 (초)
     * @param supplier 실행할 작업
     * @return 작업 결과
     */
    public <T> T executeWithLock(
            String lockKey,
            long waitTime,
            long leaseTime,
            Supplier<T> supplier) {

        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean available = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

            if (!available) {
                log.warn("Failed to acquire lock: {}", lockKey);
                throw new LockAcquisitionException();
            }

            log.debug("Lock acquired: {}", lockKey);
            return supplier.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: {}", lockKey);
            }
        }
    }

    /**
     * void 반환 작업용
     */
    public void executeWithLock(
            String lockKey,
            long waitTime,
            long leaseTime,
            Runnable runnable) {

        executeWithLock(lockKey, waitTime, leaseTime, () -> {
            runnable.run();
            return null;
        });
    }
}
