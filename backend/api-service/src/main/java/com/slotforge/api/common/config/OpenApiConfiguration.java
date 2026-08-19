package com.slotforge.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfiguration {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI slotForgeOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(
                                        "JWT access token returned by login"
                                )
                ))
                .info(new Info()
                        .title("SlotForge API")
                        .version("v1")
                        .description("""
                                API-first event booking backend.

                                Event-session timestamps are returned as UTC
                                instants. The intended IANA display timezone
                                is stored separately.
                                """));
    }
}
