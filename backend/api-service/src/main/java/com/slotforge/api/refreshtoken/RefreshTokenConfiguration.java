package com.slotforge.api.refreshtoken;

import java.security.SecureRandom;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenConfiguration {

    @Bean
    SecureRandom refreshTokenSecureRandom() {
        return new SecureRandom();
    }
}
