package com.ticketing.domain.event.service;

import com.ticketing.domain.event.domain.Event;
import com.ticketing.domain.event.dto.EventCreateRequest;
import com.ticketing.domain.event.dto.EventResponse;
import com.ticketing.domain.event.repository.EventRepository;
import com.ticketing.domain.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * 이벤트 생성
     */
    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {
        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .venue(request.getVenue())
                .eventDate(request.getEventDate())
                .imageUrl(request.getImageUrl())
                .build();

        event = eventRepository.save(event);
        log.info("Event created: id={}, title={}", event.getId(), event.getTitle());

        return convertToResponse(event);
    }

    /**
     * 이벤트 조회 (캐싱)
     */
    @Cacheable(value = "events", key = "#id")
    public EventResponse getEvent(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + id));

        return convertToResponse(event);
    }

    /**
     * 전체 이벤트 조회 (페이징)
     */
    public Page<EventResponse> getAllEvents(Pageable pageable) {
        return eventRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    /**
     * 카테고리별 조회
     */
    public Page<EventResponse> getEventsByCategory(String category, Pageable pageable) {
        return eventRepository.findByCategory(category, pageable)
                .map(this::convertToResponse);
    }

    /**
     * 예정된 이벤트 조회
     */
    public List<EventResponse> getUpcomingEvents() {
        return eventRepository.findUpcomingEvents(LocalDateTime.now()).stream()
                .map(this::convertToResponse)
                .toList();
    }

    /**
     * 이벤트 검색
     */
    public Page<EventResponse> searchEvents(String keyword, Pageable pageable) {
        return eventRepository.findByTitleContainingIgnoreCase(keyword, pageable)
                .map(this::convertToResponse);
    }

    /**
     * Entity -> Response 변환
     */
    private EventResponse convertToResponse(Event event) {
        Long totalStock = ticketRepository.getTotalStockByEventId(event.getId());

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
