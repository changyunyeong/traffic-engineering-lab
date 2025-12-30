package com.ticketing.test.service;

import com.ticketing.domain.event.repository.EventRepository;
import com.ticketing.test.dto.data.DataInitRequest;
import com.ticketing.test.dto.data.DataInitResponse;
import com.ticketing.test.dto.InitProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataService {

    private final UserDataService userDataService;
    private final EventDataService eventDataService;
    private final TicketDataService ticketDataService;
    private final ReservationDataService reservationDataService;
    private final EventRepository eventRepository;

    // 진행 상황을 저장하는 맵
    private final Map<String, InitProgress> progressMap = new ConcurrentHashMap<>();

    public DataInitResponse initializeUsers(DataInitRequest request) {
        String taskId = UUID.randomUUID().toString();
        InitProgress progress = new InitProgress(taskId, "USER", request.getCount());
        progressMap.put(taskId, progress);

        CompletableFuture.runAsync(() -> {
            userDataService.generateUsers(request, progress);
        });

        return progress.toResponse();
    }

    public DataInitResponse initializeEvents(DataInitRequest request) {
        String taskId = UUID.randomUUID().toString();
        InitProgress progress = new InitProgress(taskId, "EVENT", request.getCount());
        progressMap.put(taskId, progress);

        CompletableFuture.runAsync(() -> {
            eventDataService.generateEvents(request, progress);
        });

        return progress.toResponse();
    }

    public DataInitResponse initializeTickets(DataInitRequest request) {
        String taskId = UUID.randomUUID().toString();
        long eventCount = eventRepository.count();
        InitProgress progress = new InitProgress(taskId, "TICKET", (int) (eventCount * 5));
        progressMap.put(taskId, progress);

        CompletableFuture.runAsync(() -> {
            ticketDataService.generateTickets(request, progress);
        });

        return progress.toResponse();
    }

    public DataInitResponse initializeReservations(DataInitRequest request) {
        String taskId = UUID.randomUUID().toString();
        InitProgress progress = new InitProgress(taskId, "RESERVATION", request.getCount());
        progressMap.put(taskId, progress);

        CompletableFuture.runAsync(() -> {
            reservationDataService.generateReservations(request, progress);
        });

        return progress.toResponse();
    }

    public DataInitResponse initializeAll(DataInitRequest request) {
        String taskId = UUID.randomUUID().toString();
        int totalCount = request.getCount() * 4; // 대략적인 총 개수
        InitProgress progress = new InitProgress(taskId, "ALL", totalCount);
        progressMap.put(taskId, progress);

        CompletableFuture.runAsync(() -> {
            try {
                log.info("전체 초기화 시작");

                // 순차적으로 실행
                userDataService.generateUsers(request, progress);
                eventDataService.generateEvents(request, progress);
                ticketDataService.generateTickets(request, progress);
                reservationDataService.generateReservations(request, progress);

                progress.complete();
                log.info("전체 초기화 완료");
            } catch (Exception e) {
                progress.fail();
                log.error("전체 초기화 실패", e);
            }
        });

        return progress.toResponse();
    }

    public DataInitResponse getProgress(String taskId) {
        InitProgress progress = progressMap.get(taskId);
        if (progress == null) {
            return DataInitResponse.builder()
                    .taskId(taskId)
                    .status("NOT_FOUND")
                    .message("작업을 찾을 수 없습니다")
                    .build();
        }
        return progress.toResponse();
    }

    public void cleanupProgress(String taskId) {
        progressMap.remove(taskId);
    }
}