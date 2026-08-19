package com.slotforge.api.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, UUID> {

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findWithRolesById(UUID id);
}
