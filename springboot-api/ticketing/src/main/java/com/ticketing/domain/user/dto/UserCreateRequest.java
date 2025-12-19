package com.ticketing.domain.user.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
public class UserCreateRequest {

    @NotNull(message = "이름은 필수입니다")
    private String name;

    @NotNull(message = "이메일은 필수입니다")
    @Email
    private String email;

    @NotNull(message = "전화번호는 필수입니다")
    @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
    private String phoneNumber;
}
