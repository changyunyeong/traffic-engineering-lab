package com.ticketing.test.dto.test;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyTestResponse {
    
    private String pattern;  // 생성된 패턴 유형
    private Integer count;  // 생성된 예약 개수
    private String message;  // 결과 메시지
    private Long suspiciousUserId;  // 의심스러운 사용자 ID (해당하는 경우)
    private String suspiciousIp;  // 의심스러운 IP (해당하는 경우)
}