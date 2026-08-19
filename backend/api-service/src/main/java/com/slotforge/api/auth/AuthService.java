package com.slotforge.api.auth;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.refreshtoken.IssuedRefreshToken;
import com.slotforge.api.refreshtoken.RefreshedTokenPair;
import com.slotforge.api.refreshtoken.RefreshTokenService;
import com.slotforge.api.security.IssuedAccessToken;
import com.slotforge.api.security.JwtService;
import com.slotforge.api.user.Role;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.RoleRepository;
import com.slotforge.api.user.UserAccount;
import com.slotforge.api.user.UserAccountRepository;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final String dummyPasswordHash;

    public AuthService(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.dummyPasswordHash = passwordEncoder.encode(
                "slotforge-dummy-password"
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        UserAccount user = userAccountRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        if (user == null) {
            passwordEncoder.matches(
                    request.password(),
                    dummyPasswordHash
            );
            throw new InvalidCredentialsException();
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        );

        if (!passwordMatches || !user.isActive()) {
            throw new InvalidCredentialsException();
        }

        Set<RoleName> roles = user.getRoles()
                .stream()
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());

        IssuedAccessToken accessToken =
                jwtService.issueAccessToken(user.getId(), roles);

        IssuedRefreshToken refreshToken =
                refreshTokenService.issueNewFamily(user);

        return LoginResponse.from(accessToken, refreshToken);
    }

    public RefreshResponse refresh(RefreshRequest request) {
        RefreshedTokenPair tokens = refreshTokenService.rotate(
                request.refreshToken()
        );

        return RefreshResponse.from(tokens);
    }

    public void logout(LogoutRequest request) {
        refreshTokenService.logout(request.refreshToken());
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        Role customerRole = roleRepository
                .findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException(
                        "Required CUSTOMER role is not configured"
                ));

        String passwordHash =
                passwordEncoder.encode(request.password());

        UserAccount user = new UserAccount(
                normalizedEmail,
                passwordHash
        );
        user.assignRole(customerRole);

        try {
            UserAccount savedUser =
                    userAccountRepository.saveAndFlush(user);

            return RegistrationResponse.from(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
