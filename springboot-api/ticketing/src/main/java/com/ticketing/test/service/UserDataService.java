package com.ticketing.test.service;

import com.ticketing.domain.user.entity.User;
import com.ticketing.domain.user.repository.UserRepository;
import com.ticketing.global.snowflake.Snowflake;
import com.ticketing.test.dto.data.DataInitRequest;
import com.ticketing.test.dto.InitProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataService {

    private final UserRepository userRepository;
    private final Snowflake snowflake;

    public InitProgress generateUsers(DataInitRequest request, InitProgress progress) {

        if (request.getClearExisting()) {
            userRepository.deleteAll();
        }

        int threadCount = request.getThreadCount();
        int usersPerThread = request.getCount() / threadCount;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int threadId = 0; threadId < threadCount; threadId++) {
                final int id = threadId;
                executorService.submit(() -> {
                    try {
                        insertUsers(id, usersPerThread, request.getBatchSize(), progress);
                    } catch (Exception e) {
                        log.error("스레드 {} 오류", id, e);
                        progress.incrementError(usersPerThread);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            progress.complete();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            progress.fail();
            log.error("사용자 생성 중단됨", e);
        } finally {
            executorService.shutdown();
        }

        return progress;
    }

    @Transactional
    public void insertUsers(int threadId, int totalCount, int batchSize, InitProgress progress) {
        int batchCount = totalCount / batchSize;

        for (int batch = 0; batch < batchCount; batch++) {
            Instant batchStart = Instant.now();

            List<User> users = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                long userId = snowflake.nextId();
                User user = User.create(
                        userId,
                        String.format("user_%d_%d_%d@test.com", threadId, batch, i),
                        String.format("TestUser_%d_%d", threadId, batch * batchSize + i),
                        String.format("010-%04d-%04d", threadId, (batch * batchSize + i) % 10000)
                );
                users.add(user);
            }

            try {
                userRepository.saveAll(users);
                progress.incrementCompleted(batchSize);
                progress.addTime(java.time.Duration.between(batchStart, Instant.now()).toMillis());
            } catch (Exception e) {
                log.error("배치 저장 실패: thread={}, batch={}", threadId, batch, e);
                progress.incrementError(batchSize);
            }
        }
    }
}