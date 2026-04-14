package com.qingluo.link.model.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateProfileRequestTest {

    @Test
    @DisplayName("Should_HoldAllEditableFields")
    void Should_HoldAllEditableFields() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("newNick");
        request.setEmail("new@test.com");
        request.setPhone("13800138000");
        request.setAvatarUrl("https://example.com/avatar.png");

        assertThat(request.getNickname()).isEqualTo("newNick");
        assertThat(request.getEmail()).isEqualTo("new@test.com");
        assertThat(request.getPhone()).isEqualTo("13800138000");
        assertThat(request.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    @DisplayName("Should_AllFieldsNullable")
    void Should_AllFieldsNullable() {
        UpdateProfileRequest request = new UpdateProfileRequest();

        assertThat(request.getNickname()).isNull();
        assertThat(request.getEmail()).isNull();
        assertThat(request.getPhone()).isNull();
        assertThat(request.getAvatarUrl()).isNull();
    }
}
