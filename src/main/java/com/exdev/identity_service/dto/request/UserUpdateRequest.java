package com.exdev.identity_service.dto.request;

import java.time.LocalDate;
import java.util.List;

import com.exdev.identity_service.validator.DobConstraint;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {
    String password;
    String firstName;
    String lastName;

    @DobConstraint(min = 18, message = "INVALID_DOB")
    LocalDate birthDate;

    List<String> roles;
}
