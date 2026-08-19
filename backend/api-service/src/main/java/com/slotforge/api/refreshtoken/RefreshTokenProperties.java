package com.slotforge.api.refreshtoken;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(
        prefix = "slotforge.security.refresh-token"
)
public record RefreshTokenProperties(

        @NotNull
        Duration ttl,

        @Min(
                value = 32,
                message = "Refresh tokens require at least 32 random bytes"
        )
        int randomBytes
) {
}
