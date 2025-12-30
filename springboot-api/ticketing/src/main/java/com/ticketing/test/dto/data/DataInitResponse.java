package com.ticketing.test.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataInitResponse {

    private String taskId;  // 작업 ID
    private String status;  // RUNNING, COMPLETED, FAILED
    private String dataType;  // USER, EVENT, TICKET, RESERVATION
    private Integer targetCount;  // 목표 개수
    private Integer completedCount;  // 완료 개수
    private Integer errorCount;  // 에러 개수
    private Double progressPercent;  // 진행률
    private Long elapsedSeconds;  // 경과 시간
    private Long estimatedSecondsRemaining;  // 예상 남은 시간
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String message;
}