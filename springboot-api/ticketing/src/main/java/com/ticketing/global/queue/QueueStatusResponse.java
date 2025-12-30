package com.ticketing.global.queue;

import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueStatusResponse {

    private String token;
    private Long position;  // 대기 순번
    private Long totalWaiting;  // 전체 대기자 수
    private Integer estimatedWaitTimeSeconds;  // 예상 대기 시간
    private LocalDateTime enteredAt;
}
