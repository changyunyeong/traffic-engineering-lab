package com.ticketing.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequest {
    
    @JsonProperty("user_id")
    private Long userId;

    @Builder.Default
    private Integer limit = 10;
}
