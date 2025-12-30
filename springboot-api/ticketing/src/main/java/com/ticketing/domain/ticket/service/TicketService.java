package com.ticketing.domain.ticket.service;

import com.ticketing.domain.event.domain.Event;
import com.ticketing.domain.event.repository.EventRepository;
import com.ticketing.domain.ticket.domain.Ticket;
import com.ticketing.domain.ticket.dto.TicketCreateRequest;
import com.ticketing.domain.ticket.dto.TicketResponse;
import com.ticketing.domain.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STOCK_KEY_PREFIX = "ticket:stock:";

    /**
     * 티켓 생성
     */
    @Transactional
    @CacheEvict(value = "events", key = "#request.eventId")
    public TicketResponse createTicket(TicketCreateRequest request) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다"));

        Ticket ticket = Ticket.builder()
                .event(event)
                .name(request.getName())
                .stock(request.getStock())
                .price(request.getPrice())
                .build();

        ticket = ticketRepository.save(ticket);

        // Redis에 재고 캐싱
        String stockKey = STOCK_KEY_PREFIX + ticket.getId();
        redisTemplate.opsForValue().set(stockKey, ticket.getStock(), Duration.ofMinutes(30));

        log.info("Ticket created: id={}, name={}, stock={}",
                ticket.getId(), ticket.getName(), ticket.getStock());

        return convertToResponse(ticket);
    }

    /**
     * 티켓 조회
     */
    @Cacheable(value = "tickets", key = "#id")
    public TicketResponse getTicket(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("티켓을 찾을 수 없습니다: " + id));

        return convertToResponse(ticket);
    }

    /**
     * 이벤트별 티켓 조회
     */
    public List<TicketResponse> getTicketsByEvent(Long eventId) {
        return ticketRepository.findByEventId(eventId).stream()
                .map(this::convertToResponse)
                .toList();
    }

    /**
     * 예약 가능한 티켓만 조회
     */
    public List<TicketResponse> getAvailableTickets(Long eventId) {
        return ticketRepository.findAvailableTicketsByEventId(eventId).stream()
                .map(this::convertToResponse)
                .toList();
    }

    /**
     * 재고 복구 (예약 취소 시)
     */
    @Transactional
    public void restoreStock(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("티켓을 찾을 수 없습니다"));

        ticket.increaseStock();
        ticketRepository.save(ticket);

        // Redis 재고도 증가
        String stockKey = STOCK_KEY_PREFIX + ticketId;
        redisTemplate.opsForValue().increment(stockKey);

        log.info("Stock restored: ticketId={}, newStock={}", ticketId, ticket.getStock());
    }

    /**
     * Entity -> Response 변환
     */
    private TicketResponse convertToResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .eventId(ticket.getEvent().getId())
                .eventTitle(ticket.getEvent().getTitle())
                .name(ticket.getName())
                .stock(ticket.getStock())
                .price(ticket.getPrice())
                .available(ticket.isAvailable())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}
