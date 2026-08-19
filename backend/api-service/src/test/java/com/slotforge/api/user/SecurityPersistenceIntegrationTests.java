package com.slotforge.api.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.TestcontainersConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SecurityPersistenceIntegrationTests {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void migrationSeedsAllSupportedRoles() {
        assertTrue(
                roleRepository.findByName(RoleName.CUSTOMER).isPresent()
        );
        assertTrue(
                roleRepository.findByName(RoleName.ORGANIZER).isPresent()
        );
        assertTrue(
                roleRepository.findByName(RoleName.ADMIN).isPresent()
        );
    }

    @Test
    void userCanBePersistedAndLoadedWithAssignedRole() {
        Role customerRole = roleRepository
                .findByName(RoleName.CUSTOMER)
                .orElseThrow();

        UserAccount user = new UserAccount(
                "customer@example.com",
                "temporary-test-hash"
        );
        user.assignRole(customerRole);

        UserAccount savedUser =
                userAccountRepository.saveAndFlush(user);

        UserAccount loadedUser = userAccountRepository
                .findByEmail("customer@example.com")
                .orElseThrow();

        assertEquals(savedUser.getId(), loadedUser.getId());
        assertEquals(UserStatus.ACTIVE, loadedUser.getStatus());
        assertEquals(1, loadedUser.getRoles().size());
        assertEquals(
                RoleName.CUSTOMER,
                loadedUser.getRoles().iterator().next().getName()
        );
    }

    @Test
    void passwordEncoderHashesAndVerifiesPassword() {
        String rawPassword = "Correct-Horse-Battery-Staple-42!";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertFalse(encodedPassword.equals(rawPassword));
        assertTrue(encodedPassword.startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
        assertFalse(
                passwordEncoder.matches(
                        "incorrect-password",
                        encodedPassword
                )
        );
    }
}
