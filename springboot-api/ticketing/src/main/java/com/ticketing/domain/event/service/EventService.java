package com.ticketing.domain.event.service;

import com.ticketing.domain.event.dto.EventCreateRequest;
import com.ticketing.domain.event.dto.EventResponse;
import com.ticketing.domain.event.entity.Event;
import com.ticketing.domain.event.repository.EventRepository;
import com.ticketing.domain.ticket.repository.TicketRepository;
import com.ticketing.global.dto.PageResponse;
import com.ticketing.global.enums.Category;
import com.ticketing.global.exception.domain.event.EventNotFoundException;
import com.ticketing.global.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final Snowflake snowflake;

    /**
     * 이벤트 생성
     */
    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {

        Event event = Event.builder()
                .id(snowflake.nextId())
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .venue(request.getVenue())
                .eventDate(request.getEventDate())
                .imageUrl(request.getImageUrl())
                .build();

        event = eventRepository.save(event);
//        log.info("Event created: id={}, title={}", event.getId(), event.getTitle());

        return convertToResponse(event);
    }

    /**
     * 이벤트 조회
     */
    @Cacheable(value = "events", key = "#id")
    public EventResponse getEvent(Long id) {

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        return convertToResponse(event);
    }

    /**
     * 전체 이벤트 조회
     */
    public PageResponse<EventResponse> getAllEvents(Integer page) {

        PageRequest pageRequest = PageRequest.of(page, 5, Sort.by("eventDate").ascending());

        Page<Object[]> results = eventRepository.findAllWithTotalStock(pageRequest);
        Page<EventResponse> events = results.map(row -> {
            Event event = (Event) row[0];
            Long totalStock = ((Number) row[1]).longValue();
            return convertToResponse(event, totalStock);
        });

        return PageResponse.<EventResponse>builder()
                .content(events.getContent())
                .page(events.getNumber())
                .size(events.getSize())
                .totalElements(events.getTotalElements())
                .totalPages(events.getTotalPages())
                .last(events.isLast())
                .build();
    }

    /**
     * 카테고리별 조회
     */
    public PageResponse<EventResponse> getEventsByCategory(Category category, Integer page) {

        PageRequest pageRequest = PageRequest.of(page, 5, Sort.by("eventDate").ascending());

        Page<Object[]> results = eventRepository.findByCategoryWithTotalStock(category, pageRequest);
        Page<EventResponse> events = results.map(row -> {
            Event event = (Event) row[0];
            Long totalStock = ((Number) row[1]).longValue();
            return convertToResponse(event, totalStock);
        });

        return PageResponse.<EventResponse>builder()
                .content(events.getContent())
                .page(events.getNumber())
                .size(events.getSize())
                .totalElements(events.getTotalElements())
                .totalPages(events.getTotalPages())
                .last(events.isLast())
                .build();
    }

    /**
     * 이벤트 검색
     */
    public PageResponse<EventResponse> searchEvents(String keyword, Integer page) {

        PageRequest pageRequest = PageRequest.of(page, 5, Sort.by("eventDate").ascending());

        Page<Object[]> results = eventRepository.findByTitleContainingWithTotalStock(keyword, pageRequest);
        Page<EventResponse> events = results.map(row -> {
            Event event = (Event) row[0];
            Long totalStock = ((Number) row[1]).longValue();
            return convertToResponse(event, totalStock);
        });

        return PageResponse.<EventResponse>builder()
                .content(events.getContent())
                .page(events.getNumber())
                .size(events.getSize())
                .totalElements(events.getTotalElements())
                .totalPages(events.getTotalPages())
                .last(events.isLast())
                .build();
    }

    /**
     * 예정된 이벤트 조회
     */
    public List<EventResponse> getUpcomingEvents() {

        return eventRepository.findUpcomingEventsWithTotalStock(LocalDateTime.now()).stream()
                .map(row -> {
                    Event event = (Event) row[0];
                    Long totalStock = ((Number) row[1]).longValue();
                    return convertToResponse(event, totalStock);
                })
                .toList();
    }

    /**
     * Entity -> Response 변환 (단건 조회용 - N+1 발생)
     */
    private EventResponse convertToResponse(Event event) {
        Long totalStock = ticketRepository.getTotalStockByEventId(event.getId());
        return convertToResponse(event, totalStock);
    }

    /**
     * Entity -> Response 변환 (목록 조회용 - N+1 해결)
     */
    private EventResponse convertToResponse(Event event, Long totalStock) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .category(event.getCategory())
                .venue(event.getVenue())
                .eventDate(event.getEventDate())
                .imageUrl(event.getImageUrl())
                .totalStock(totalStock)
                .createdAt(event.getCreatedAt())
                .build();
    }
}
