package com.ticketing.domain.ticket.domain;

import com.ticketing.domain.event.domain.Event;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_event", columnList = "event_id")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 255)
    private String name;  // VIP석, R석, S석 등

    @Column(nullable = false)
    private Long stock;  // 재고

    @Column(nullable = false)
    private Long price;  // 가격

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // 비즈니스 메서드
    public void decreaseStock() {
        if (stock <= 0) {
            throw new IllegalStateException("재고가 부족합니다");
        }
        this.stock--;
    }

    public void increaseStock() {
        this.stock++;
    }

    public boolean isAvailable() {
        return stock > 0;
    }
}
