package com.ticketing.test.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataInitRequest {

    // @Builder만 사용하면 필드에 기본값을 할당해도 빌더를 통해 객체를 생성할 때 그 값이 무시됨
    // @Builder.Default를 사용하여 기본값을 유지

    @Builder.Default
    private Integer count = 1000;  // 생성할 개수

    @Builder.Default
    private Integer batchSize = 100;  // 배치 크기

    @Builder.Default
    private Integer threadCount = 5;  // 스레드 개수

    @Builder.Default
    private Boolean clearExisting = false;  // 기존 데이터 삭제 여부
}