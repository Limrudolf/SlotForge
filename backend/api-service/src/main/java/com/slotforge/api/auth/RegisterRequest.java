package com.slotforge.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(
                max = 320,
                message = "Email must not exceed 320 characters"
        )
        String email,

        @NotBlank(message = "Password is required")
        @Size(
                min = 12,
                max = 128,
                message = "Password must contain between 12 and 128 characters"
        )
        String password
) {

    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
