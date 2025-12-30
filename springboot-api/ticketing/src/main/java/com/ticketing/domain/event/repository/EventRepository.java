package com.ticketing.domain.event.repository;

import com.ticketing.domain.event.domain.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    // 카테고리별 조회
    Page<Event> findByCategory(String category, Pageable pageable);

    // 제목 검색 (대소문자 무시)
    Page<Event> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    // 예정된 이벤트 조회 (날짜순)
    @Query("SELECT e FROM Event e WHERE e.eventDate > :now ORDER BY e.eventDate ASC")
    List<Event> findUpcomingEvents(@Param("now") LocalDateTime now);

    // 인기 이벤트 (티켓 예약 많은 순)
    @Query("SELECT e FROM Event e " +
            "LEFT JOIN e.tickets t " +
            "LEFT JOIN Reservation r ON r.ticket = t " +
            "WHERE e.eventDate > :now " +
            "GROUP BY e " +
            "ORDER BY COUNT(r) DESC")
    List<Event> findPopularEvents(@Param("now") LocalDateTime now, Pageable pageable);
}
