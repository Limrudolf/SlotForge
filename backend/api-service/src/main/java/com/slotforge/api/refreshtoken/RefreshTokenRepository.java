package com.slotforge.api.refreshtoken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID> {

    @EntityGraph(attributePaths = {
            "user",
            "user.roles",
            "replacedByToken"
    })
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select refreshToken
            from RefreshToken refreshToken
            where refreshToken.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    List<RefreshToken> findAllByFamilyIdAndRevokedAtIsNull(
            UUID familyId
    );

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(
            UUID userId
    );
}
