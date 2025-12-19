package com.ticketing.domain.event.dto;

import com.ticketing.global.enums.Category;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
public class EventCreateRequest {

    @NotBlank(message = "이벤트 제목은 필수입니다")
    @Size(max = 255, message = "제목은 255자를 초과할 수 없습니다")
    private String title;

    @Size(max = 2000, message = "설명은 2000자를 초과할 수 없습니다")
    private String description;

    @NotBlank(message = "카테고리는 필수입니다")
    private Category category;

    @NotBlank(message = "장소는 필수입니다")
    private String venue;

    @NotNull(message = "이벤트 날짜는 필수입니다")
    @Future(message = "이벤트 날짜는 미래여야 합니다")
    private LocalDateTime eventDate;

    private String imageUrl;
}
