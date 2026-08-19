package com.slotforge.api.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.user.RoleName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JwtServiceIntegrationTests {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void issuedTokenContainsTrustedIdentityAndRoles() {
        UUID userId = UUID.randomUUID();

        Instant beforeIssue = Instant.now();

        IssuedAccessToken issuedToken =
                jwtService.issueAccessToken(
                        userId,
                        Set.of(
                                RoleName.ORGANIZER,
                                RoleName.CUSTOMER
                        )
                );

        Jwt decoded = jwtDecoder.decode(issuedToken.value());

        assertEquals(userId.toString(), decoded.getSubject());
        assertEquals(
                jwtProperties.issuer(),
                decoded.getIssuer().toString()
        );
        assertEquals(
                Set.of("CUSTOMER", "ORGANIZER"),
                Set.copyOf(
                        decoded.getClaimAsStringList("roles")
                )
        );
        assertFalse(
                decoded.getIssuedAt().isBefore(
                        beforeIssue.minusSeconds(1)
                )
        );
        assertEquals(
                issuedToken.expiresAt(),
                decoded.getExpiresAt()
        );
        assertEquals(
                jwtProperties.accessTokenTtl(),
                Duration.between(
                        decoded.getIssuedAt(),
                        decoded.getExpiresAt()
                )
        );
        assertTrue(decoded.getId() != null);
    }

    @Test
    void separatelyIssuedTokensReceiveDifferentIdentifiers() {
        UUID userId = UUID.randomUUID();

        Jwt first = jwtDecoder.decode(
                jwtService.issueAccessToken(
                        userId,
                        Set.of(RoleName.CUSTOMER)
                ).value()
        );

        Jwt second = jwtDecoder.decode(
                jwtService.issueAccessToken(
                        userId,
                        Set.of(RoleName.CUSTOMER)
                ).value()
        );

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void tokenCannotBeVerifiedWithDifferentSecret() {
        IssuedAccessToken issuedToken =
                jwtService.issueAccessToken(
                        UUID.randomUUID(),
                        Set.of(RoleName.CUSTOMER)
                );

        byte[] differentSecret = Base64.getDecoder().decode(
                "QW5vdGhlci1sb2NhbC1zZWNyZXQta2V5LXdpdGgtMzItYnl0ZXMh"
        );

        SecretKey differentKey = new SecretKeySpec(
                differentSecret,
                "HmacSHA256"
        );

        JwtDecoder wrongDecoder = NimbusJwtDecoder
                .withSecretKey(differentKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        assertThrows(
                JwtException.class,
                () -> wrongDecoder.decode(issuedToken.value())
        );
    }
}
