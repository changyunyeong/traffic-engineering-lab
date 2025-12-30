package com.ticketing.domain.user.entity;

import com.ticketing.domain.reservation.entity.Reservation;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 100)
    private String name;

    @Column(length = 20)
    private String phoneNumber;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Reservation> reservations = new ArrayList<>();

    public static User create(Long id, String email, String name, String phone_number) {
        User user = new User();
        user.id = id;
        user.email = email;
        user.name = name;
        user.phoneNumber = phone_number;
        user.createdAt = LocalDateTime.now();
        return user;
    }
}
