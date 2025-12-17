package com.ticketing.domain.event.domain;

import com.ticketing.domain.ticket.domain.Ticket;
import com.ticketing.global.enums.Category;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_event_category", columnList = "category"),
        @Index(name = "idx_event_date", columnList = "event_date")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private Category category;  // CONCERT, MUSICAL, SPORTS, ETC

    @Column(length = 255)
    private String venue;

    private LocalDateTime eventDate;

    @Column(length = 500)
    private String imageUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    // 비즈니스 메서드
    public void addTicket(Ticket ticket) {
        tickets.add(ticket);
        ticket.setEvent(this);
    }

    public boolean isUpcoming() {
        return eventDate != null && eventDate.isAfter(LocalDateTime.now());
    }
}