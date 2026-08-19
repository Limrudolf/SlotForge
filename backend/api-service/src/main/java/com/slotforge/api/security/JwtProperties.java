package com.slotforge.api.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "slotforge.security.jwt")
public record JwtProperties(

        @NotBlank
        String issuer,

        @NotNull
        Duration accessTokenTtl,

        @NotBlank
        String secretBase64
) {
}
