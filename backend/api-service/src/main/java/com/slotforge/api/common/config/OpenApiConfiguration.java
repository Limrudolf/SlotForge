package com.slotforge.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI slotForgeOpenApi() {
        return new OpenAPI()
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
