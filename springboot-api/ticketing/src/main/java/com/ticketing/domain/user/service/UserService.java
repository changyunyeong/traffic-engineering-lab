package com.ticketing.domain.user.service;

import com.ticketing.domain.user.dto.UserCreateRequest;
import com.ticketing.domain.user.dto.UserResponse;
import com.ticketing.domain.user.entity.User;
import com.ticketing.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse joinUser(UserCreateRequest request) {

        User joinUser = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .build();

        userRepository.save(joinUser);

        return UserResponse.builder()
                .id(joinUser.getId())
                .createdAt(joinUser.getCreatedAt())
                .build();
    }
}
