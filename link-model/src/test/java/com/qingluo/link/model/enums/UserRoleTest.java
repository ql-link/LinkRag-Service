package com.qingluo.link.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRoleTest {

    @Test
    @DisplayName("Should_ReturnAdmin_When_InputIsAdminString")
    void Should_ReturnAdmin_When_InputIsAdminString() {
        assertThat(UserRole.of("ADMIN")).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("Should_ReturnUser_When_InputIsUserString")
    void Should_ReturnUser_When_InputIsUserString() {
        assertThat(UserRole.of("USER")).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("Should_ReturnUser_When_InputIsNull")
    void Should_ReturnUser_When_InputIsNull() {
        assertThat(UserRole.of(null)).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("Should_ThrowIllegalArgumentException_When_InputIsInvalidRole")
    void Should_ThrowIllegalArgumentException_When_InputIsInvalidRole() {
        assertThatThrownBy(() -> UserRole.of("SUPERADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUPERADMIN");
    }
}
