package com.ticketing.test.dto.test;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyTestRequest {
    
    @Builder.Default
    private Integer count = 100;  // 생성할 예약 개수
    
    private String pattern;  // NORMAL, NIGHT_TIME, BULK, HIGH_PRICE, SAME_IP, MIXED
}