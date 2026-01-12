package com.ticketing.domain.recommendation.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
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
