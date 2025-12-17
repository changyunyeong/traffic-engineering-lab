package com.ticketing.global.queue;

import com.ticketing.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Queue", description = "대기열 API")
@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @Operation(summary = "대기열 진입", description = "티켓 예약 대기열에 진입합니다")
    @PostMapping("/tickets/{ticketId}")
    public ApiResponse<QueueStatusResponse> enterQueue(
            @PathVariable Long ticketId,
            @RequestParam Long userId) {

        QueueStatusResponse response = queueService.enterQueue(ticketId, userId);
        return ApiResponse.success("대기열에 등록되었습니다", response);
    }

    @Operation(summary = "대기 상태 조회", description = "현재 대기 순번을 조회합니다")
    @GetMapping("/tickets/{ticketId}/status")
    public ApiResponse<QueueStatusResponse> getQueueStatus(
            @PathVariable Long ticketId,
            @RequestParam String token) {

        QueueStatusResponse response = queueService.getQueueStatus(ticketId, token);
        return ApiResponse.success(response);
    }

    @Operation(summary = "대기열 크기 조회", description = "전체 대기자 수를 조회합니다")
    @GetMapping("/tickets/{ticketId}/size")
    public ApiResponse<Long> getQueueSize(@PathVariable Long ticketId) {
        Long size = queueService.getQueueSize(ticketId);
        return ApiResponse.success(size);
    }

    @Operation(summary = "대기열 이탈", description = "대기열에서 제거합니다")
    @DeleteMapping("/tickets/{ticketId}")
    public ApiResponse<Void> removeFromQueue(
            @PathVariable Long ticketId,
            @RequestParam String token) {

        queueService.removeFromQueue(ticketId, token);
        return ApiResponse.success("대기열에서 제거되었습니다", null);
    }
}