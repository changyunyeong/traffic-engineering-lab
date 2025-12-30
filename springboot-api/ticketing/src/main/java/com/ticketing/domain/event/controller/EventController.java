package com.ticketing.domain.event.controller;

import com.ticketing.domain.event.dto.EventCreateRequest;
import com.ticketing.domain.event.dto.EventResponse;
import com.ticketing.domain.event.service.EventService;
import com.ticketing.global.dto.ApiResponse;
import com.ticketing.global.dto.PageResponse;
import com.ticketing.global.enums.Category;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Event", description = "이벤트 API")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Operation(summary = "이벤트 생성", description = "새로운 이벤트를 생성합니다")
    @PostMapping
    public ApiResponse<EventResponse> createEvent(
            @Valid @RequestBody EventCreateRequest request) {

        EventResponse response = eventService.createEvent(request);
        return ApiResponse.success("이벤트가 생성되었습니다", response);
    }

    @Operation(summary = "이벤트 조회", description = "ID로 이벤트를 조회합니다")
    @GetMapping("/{id}")
    public ApiResponse<EventResponse> getEvent(@PathVariable Long id) {
        EventResponse response = eventService.getEvent(id);
        return ApiResponse.success(response);
    }

    @Operation(summary = "전체 이벤트 조회", description = "모든 이벤트를 페이징하여 조회합니다")
    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> getAllEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "eventDate") String sortBy) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).ascending());
        Page<EventResponse> events = eventService.getAllEvents(pageable);

        PageResponse<EventResponse> pageResponse = PageResponse.<EventResponse>builder()
                .content(events.getContent())
                .page(events.getNumber())
                .size(events.getSize())
                .totalElements(events.getTotalElements())
                .totalPages(events.getTotalPages())
                .last(events.isLast())
                .build();

        return ApiResponse.success(pageResponse);
    }

    @Operation(summary = "카테고리별 이벤트 조회")
    @GetMapping("/category/{category}")
    public ApiResponse<PageResponse<EventResponse>> getEventsByCategory(
            @PathVariable Category category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<EventResponse> events = eventService.getEventsByCategory(category, pageable);

        PageResponse<EventResponse> pageResponse = PageResponse.<EventResponse>builder()
                .content(events.getContent())
                .page(events.getNumber())
                .size(events.getSize())
                .totalElements(events.getTotalElements())
                .totalPages(events.getTotalPages())
                .last(events.isLast())
                .build();

        return ApiResponse.success(pageResponse);
    }

    @Operation(summary = "예정된 이벤트 조회", description = "미래의 이벤트만 조회합니다")
    @GetMapping("/upcoming")
    public ApiResponse<List<EventResponse>> getUpcomingEvents() {
        List<EventResponse> events = eventService.getUpcomingEvents();
        return ApiResponse.success(events);
    }

    @Operation(summary = "이벤트 검색", description = "제목으로 이벤트를 검색합니다")
    @GetMapping("/search")
    public ApiResponse<PageResponse<EventResponse>> searchEvents(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<EventResponse> events = eventService.searchEvents(keyword, pageable);

        PageResponse<EventResponse> pageResponse = PageResponse.<EventResponse>builder()
                .content(events.getContent())
                .page(events.getNumber())
                .size(events.getSize())
                .totalElements(events.getTotalElements())
                .totalPages(events.getTotalPages())
                .last(events.isLast())
                .build();

        return ApiResponse.success(pageResponse);
    }
}