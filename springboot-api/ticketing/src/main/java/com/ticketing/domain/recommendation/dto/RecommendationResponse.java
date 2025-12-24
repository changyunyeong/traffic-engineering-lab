package com.ticketing.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticketing.domain.recommendation.entity.EventRecommendation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {
    
    @JsonProperty("user_id")
    private Long userId;

    @Builder.Default
    private List<EventRecommendation> recommendations = new ArrayList<>();

    @JsonProperty("generated_at")
    private LocalDateTime generatedAt;
}