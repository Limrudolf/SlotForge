package com.slotforge.api.refreshtoken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.security.IssuedAccessToken;
import com.slotforge.api.security.JwtService;
import com.slotforge.api.user.Role;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.UserAccount;

@Service
public class RefreshTokenService {

    private static final String TOKEN_PREFIX = "rfr_";

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final JwtService jwtService;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenProperties properties,
            SecureRandom secureRandom,
            Clock clock,
            JwtService jwtService
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.jwtService = jwtService;
    }

    @Transactional
    public IssuedRefreshToken issueNewFamily(UserAccount user) {
        return createToken(user, UUID.randomUUID()).issuedToken();
    }

    @Transactional(
            noRollbackFor = InvalidRefreshTokenException.class
    )
    public RefreshedTokenPair rotate(String rawToken) {
        String tokenHash = hash(rawToken);

        RefreshToken currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = clock.instant()
                .truncatedTo(ChronoUnit.SECONDS);

        if (currentToken.isRevoked()) {
            if (currentToken.getRevocationReason()
                    == RefreshTokenRevocationReason.ROTATED) {
                revokeActiveFamily(
                        currentToken.getFamilyId(),
                        now,
                        RefreshTokenRevocationReason.REUSE_DETECTED
                );
                refreshTokenRepository.flush();
            }

            throw new InvalidRefreshTokenException();
        }

        if (currentToken.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        UserAccount user = currentToken.getUser();

        if (!user.isActive()) {
            revokeActiveFamily(
                    currentToken.getFamilyId(),
                    now,
                    RefreshTokenRevocationReason.ACCOUNT_DISABLED
            );
            refreshTokenRepository.flush();
            throw new InvalidRefreshTokenException();
        }

        PersistedIssuedRefreshToken replacement = createToken(
                user,
                currentToken.getFamilyId()
        );

        currentToken.markUsed(now);
        currentToken.revoke(
                now,
                RefreshTokenRevocationReason.ROTATED
        );
        currentToken.replaceWith(replacement.entity());
        refreshTokenRepository.flush();

        Set<RoleName> roles = user.getRoles()
                .stream()
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());

        IssuedAccessToken accessToken =
                jwtService.issueAccessToken(user.getId(), roles);

        return new RefreshedTokenPair(
                accessToken,
                replacement.issuedToken()
        );
    }

    @Transactional
    public void logout(String rawToken) {
        String tokenHash = hash(rawToken);

        RefreshToken presentedToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElse(null);

        if (presentedToken == null) {
            return;
        }

        Instant now = clock.instant()
                .truncatedTo(ChronoUnit.SECONDS);

        revokeActiveFamily(
                presentedToken.getFamilyId(),
                now,
                RefreshTokenRevocationReason.LOGOUT
        );

        refreshTokenRepository.flush();
    }

    private PersistedIssuedRefreshToken createToken(
            UserAccount user,
            UUID familyId
    ) {
        Instant issuedAt = clock.instant()
                .truncatedTo(ChronoUnit.SECONDS);

        Instant expiresAt = issuedAt.plus(properties.ttl());

        String rawToken = generateRawToken();
        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                user,
                familyId,
                tokenHash,
                expiresAt
        );

        refreshTokenRepository.saveAndFlush(refreshToken);

        IssuedRefreshToken issuedToken = new IssuedRefreshToken(
                rawToken,
                expiresAt
        );

        return new PersistedIssuedRefreshToken(
                refreshToken,
                issuedToken
        );
    }

    private void revokeActiveFamily(
            UUID familyId,
            Instant revokedAt,
            RefreshTokenRevocationReason reason
    ) {
        refreshTokenRepository
                .findAllByFamilyIdAndRevokedAtIsNull(familyId)
                .forEach(token -> token.revoke(revokedAt, reason));
    }

    String hash(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashed = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private String generateRawToken() {
        byte[] randomBytes =
                new byte[properties.randomBytes()];

        secureRandom.nextBytes(randomBytes);

        return TOKEN_PREFIX
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(randomBytes);
    }

    private record PersistedIssuedRefreshToken(
            RefreshToken entity,
            IssuedRefreshToken issuedToken
    ) {
    }
}
