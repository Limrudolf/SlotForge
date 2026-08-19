package com.slotforge.api.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slotforge.security.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        Duration maxAge
) {
    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : List.copyOf(allowedOrigins);
        maxAge = maxAge == null ? Duration.ofHours(1) : maxAge;
    }
}
