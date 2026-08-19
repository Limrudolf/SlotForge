package com.slotforge.api.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.slotforge.api.user.RoleName;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(
            JwtEncoder jwtEncoder,
            JwtProperties properties,
            Clock clock
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issueAccessToken(
            UUID userId,
            Set<RoleName> roles
    ) {
        Instant issuedAt = clock.instant()
                .truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(
                properties.accessTokenTtl()
        );

        var roleClaims = roles.stream()
                .map(RoleName::name)
                .sorted(Comparator.naturalOrder())
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", roleClaims)
                .build();

        String tokenValue = jwtEncoder
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();

        return new IssuedAccessToken(tokenValue, expiresAt);
    }
}
