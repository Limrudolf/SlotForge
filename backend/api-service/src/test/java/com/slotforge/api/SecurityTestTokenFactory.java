package com.slotforge.api;

import java.util.Set;
import java.util.UUID;

import com.slotforge.api.security.JwtService;
import com.slotforge.api.user.Role;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.RoleRepository;
import com.slotforge.api.user.UserAccount;
import com.slotforge.api.user.UserAccountRepository;

public final class SecurityTestTokenFactory {

    private SecurityTestTokenFactory() {
    }

    public static String issueToken(
            RoleName roleName,
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            JwtService jwtService
    ) {
        return createIdentity(
                roleName,
                userAccountRepository,
                roleRepository,
                jwtService
        ).accessToken();
    }

    public static TestIdentity createIdentity(
            RoleName roleName,
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            JwtService jwtService
    ) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow();

        UserAccount user = new UserAccount(
                roleName.name().toLowerCase()
                        + "."
                        + UUID.randomUUID()
                        + "@example.com",
                "{bcrypt}test-only-hash"
        );
        user.assignRole(role);
        userAccountRepository.saveAndFlush(user);

        String accessToken = jwtService.issueAccessToken(
                user.getId(),
                Set.of(roleName)
        ).value();

        return new TestIdentity(user, accessToken);
    }

    public record TestIdentity(
            UserAccount user,
            String accessToken
    ) {
    }
}
