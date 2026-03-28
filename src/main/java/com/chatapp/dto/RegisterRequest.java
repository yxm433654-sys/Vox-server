package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(max = 64)
    private String username;

    @NotBlank
    @Size(min = 6, max = 128)
    private String password;

    @Size(max = 255)
    private String avatarUrl;
}
