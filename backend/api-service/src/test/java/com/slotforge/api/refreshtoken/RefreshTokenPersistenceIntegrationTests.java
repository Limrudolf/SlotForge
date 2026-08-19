package com.slotforge.api.refreshtoken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.user.Role;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.RoleRepository;
import com.slotforge.api.user.UserAccount;
import com.slotforge.api.user.UserAccountRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RefreshTokenPersistenceIntegrationTests {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void refreshTokenCanBePersistedAndLoadedWithUser() {
        UserAccount user = createCustomer(
                "refresh.persistence@example.com"
        );

        UUID familyId = UUID.randomUUID();
        Instant expiresAt = Instant.now()
                .plus(30, ChronoUnit.DAYS);

        RefreshToken refreshToken = new RefreshToken(
                user,
                familyId,
                "a".repeat(64),
                expiresAt
        );

        refreshTokenRepository.saveAndFlush(refreshToken);

        RefreshToken loaded = refreshTokenRepository
                .findByTokenHash("a".repeat(64))
                .orElseThrow();

        assertNotNull(loaded.getId());
        assertEquals(user.getId(), loaded.getUser().getId());
        assertEquals(familyId, loaded.getFamilyId());
        assertEquals(expiresAt, loaded.getExpiresAt());
        assertFalse(loaded.isRevoked());
        assertFalse(loaded.isExpired(Instant.now()));
        assertTrue(
                loaded.getUser().getRoles()
                        .stream()
                        .anyMatch(role ->
                                role.getName() == RoleName.CUSTOMER
                        )
        );
    }

    @Test
    void revocationPersistsTimestampAndReasonTogether() {
        UserAccount user = createCustomer(
                "refresh.revocation@example.com"
        );

        RefreshToken refreshToken = new RefreshToken(
                user,
                UUID.randomUUID(),
                "b".repeat(64),
                Instant.now().plus(30, ChronoUnit.DAYS)
        );

        refreshTokenRepository.saveAndFlush(refreshToken);

        Instant revokedAt = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);

        refreshToken.revoke(
                revokedAt,
                RefreshTokenRevocationReason.LOGOUT
        );
        refreshTokenRepository.flush();

        RefreshToken loaded = refreshTokenRepository
                .findById(refreshToken.getId())
                .orElseThrow();

        assertTrue(loaded.isRevoked());
        assertEquals(revokedAt, loaded.getRevokedAt());
        assertEquals(
                RefreshTokenRevocationReason.LOGOUT,
                loaded.getRevocationReason()
        );
    }

    private UserAccount createCustomer(String email) {
        Role customerRole = roleRepository
                .findByName(RoleName.CUSTOMER)
                .orElseThrow();

        UserAccount user = new UserAccount(
                email,
                "{bcrypt}test-only-hash"
        );
        user.assignRole(customerRole);

        return userAccountRepository.saveAndFlush(user);
    }
}
