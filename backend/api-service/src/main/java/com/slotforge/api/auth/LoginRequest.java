package com.slotforge.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(
                max = 320,
                message = "Email must not exceed 320 characters"
        )
        String email,

        @NotBlank(message = "Password is required")
        @Size(
                max = 128,
                message = "Password must not exceed 128 characters"
        )
        String password
) {

    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
