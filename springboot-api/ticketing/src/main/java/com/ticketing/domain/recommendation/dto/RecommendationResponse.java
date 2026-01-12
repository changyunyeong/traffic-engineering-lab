package com.ticketing.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
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