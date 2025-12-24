package com.ticketing.domain.recommendation.entity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRecommendation {
    @JsonProperty("event_id")
    private Long eventId;
    private String title;
    private Double score;
    private String reason;
}
