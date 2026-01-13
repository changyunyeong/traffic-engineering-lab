package com.ticketing.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
