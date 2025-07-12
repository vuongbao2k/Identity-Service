package com.exdev.identity_service.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import com.exdev.identity_service.dto.request.UserCreationRequest;
import com.exdev.identity_service.dto.response.UserResponse;
import com.exdev.identity_service.entity.User;
import com.exdev.identity_service.exception.AppException;
import com.exdev.identity_service.repository.UserRepository;

@SpringBootTest
@TestPropertySource("/test.properties")
public class UserServiceTest {
    @Autowired
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    private UserCreationRequest userCreationRequest;
    private UserResponse userResponse;
    private User user;
    private LocalDate birthDate;

    @BeforeEach
    void initData() {
        birthDate = LocalDate.of(1990, 1, 1);
        userCreationRequest = UserCreationRequest.builder()
                .username("peterparker")
                .password("12341234")
                .firstName("Peter")
                .lastName("Parker")
                .birthDate(birthDate)
                .build();
        userResponse = UserResponse.builder()
                .id("5f6ed543dbee")
                .username("peterparker")
                .firstName("Peter")
                .lastName("Parker")
                .birthDate(birthDate)
                .build();

        user = User.builder()
                .id("5f6ed543dbee")
                .username("peterparker")
                .firstName("Peter")
                .lastName("Parker")
                .birthDate(birthDate)
                .build();
    }

    @Test
    void createUser_validRequest_success() {
        // GIVEN
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);

        // WHEN
        var response = userService.createUser(userCreationRequest);

        // THEN
        Assertions.assertThat(response.getId()).isEqualTo("5f6ed543dbee");
        Assertions.assertThat(response.getUsername()).isEqualTo("peterparker");
    }

    @Test
    void createUser_userExisted_fail() {
        // GIVEN
        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        // WHEN
        var exception = assertThrows(AppException.class, () -> userService.createUser(userCreationRequest));

        // THEN
        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1001);
    }

    @Test
    @WithMockUser(username = "peterparker")
    void getMyInfo_validRequest_success() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));

        var response = userService.getMyInfo();

        Assertions.assertThat(response.getId()).isEqualTo("5f6ed543dbee");
        Assertions.assertThat(response.getUsername()).isEqualTo("peterparker");
    }

    @Test
    @WithMockUser(username = "peterparker")
    void getMyInfo_userNotFound_fail() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.ofNullable(null));

        // WHEN
        var exception = assertThrows(AppException.class, () -> userService.getMyInfo());

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1004);
    }
}
