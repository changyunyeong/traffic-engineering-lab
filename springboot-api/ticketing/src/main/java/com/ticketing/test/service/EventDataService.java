package com.ticketing.test.service;

import com.ticketing.domain.event.entity.Event;
import com.ticketing.domain.event.repository.EventRepository;
import com.ticketing.global.enums.Category;
import com.ticketing.global.snowflake.Snowflake;
import com.ticketing.test.dto.data.DataInitRequest;
import com.ticketing.test.dto.InitProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventDataService {

    private final EventRepository eventRepository;
    private final Snowflake snowflake;
    private final Random random = new Random();

    // 더미 데이터
    private static final String[] CONCERT_TITLES = {
            "BTS 월드투어", "블랙핑크 콘서트", "아이유 전국투어", "세븐틴 콘서트"
    };

    private static final String[] MUSICAL_TITLES = {
            "레미제라블", "오페라의 유령", "시카고", "위키드"
    };

    private static final String[] VENUES = {
            "고척 스카이돔", "KSPO DOME", "블루스퀘어", "예술의전당"
    };

    private static final String[] SPORT_TITLES = {
            "KBO 리그", "K리그", "프로농구", "배구 챔피언십"
    };

    public InitProgress generateEvents(DataInitRequest request, InitProgress progress) {

        if (request.getClearExisting()) {
            eventRepository.deleteAll();
        }

        int threadCount = request.getThreadCount();
        int eventsPerThread = request.getCount() / threadCount;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int threadId = 0; threadId < threadCount; threadId++) {
                final int id = threadId;
                executorService.submit(() -> {
                    try {
                        insertEvents(id, eventsPerThread, request.getBatchSize(), progress);
                    } catch (Exception e) {
                        log.error("스레드 {} 오류", id, e);
                        progress.incrementError(eventsPerThread);
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
            log.error("이벤트 생성 중단됨", e);
        } finally {
            executorService.shutdown();
        }

        return progress;
    }

    @Transactional
    public void insertEvents(int threadId, int totalCount, int batchSize, InitProgress progress) {
        int batchCount = totalCount / batchSize;

        for (int batch = 0; batch < batchCount; batch++) {
            Instant batchStart = Instant.now();

            List<Event> events = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                Category category = getRandomCategory();

                Event event = Event.builder()
                        .id(snowflake.nextId())
                        .title(getRandomTitle(category) + " #" + (threadId * totalCount + batch * batchSize + i))
                        .description("테스트 이벤트 설명")
                        .category(category)
                        .venue(VENUES[random.nextInt(VENUES.length)])
                        .eventDate(LocalDateTime.now().plusDays(random.nextInt(365)))
                        .imageUrl("https://example.com/test.jpg")
                        .build();

                events.add(event);
            }

            try {
                eventRepository.saveAll(events);
                progress.incrementCompleted(batchSize);
                progress.addTime(java.time.Duration.between(batchStart, Instant.now()).toMillis());
            } catch (Exception e) {
                log.error("배치 저장 실패", e);
                progress.incrementError(batchSize);
            }
        }
    }

    private Category getRandomCategory() {
        Category[] categories = Category.values();
        return categories[random.nextInt(categories.length)];
    }

    private String getRandomTitle(Category category) {
        return switch (category) {
            case CONCERT -> CONCERT_TITLES[random.nextInt(CONCERT_TITLES.length)];
            case MUSICAL -> MUSICAL_TITLES[random.nextInt(MUSICAL_TITLES.length)];
            case SPORTS -> SPORT_TITLES[random.nextInt(SPORT_TITLES.length)];
            default -> "특별 이벤트";
        };
    }
}