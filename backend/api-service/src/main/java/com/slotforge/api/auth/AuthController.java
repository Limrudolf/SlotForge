package com.slotforge.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(
        name = "Authentication",
        description = "Registration and authentication operations"
)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register a customer account",
            description = """
                    Creates an active account with the CUSTOMER role.
                    Roles cannot be selected by the client.
                    """
    )
    public RegistrationResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in",
            description = """
                    Verifies account credentials and returns a short-lived
                    bearer access token.
                    """
    )
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh authentication tokens",
            description = """
                    Rotates a valid single-use refresh token and returns a
                    new access-token and refresh-token pair.
                    """
    )
    public RefreshResponse refresh(
            @Valid @RequestBody RefreshRequest request
    ) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Log out",
            description = """
                    Revokes the refresh-token family represented by the
                    supplied token. Already-invalid tokens are accepted
                    idempotently.
                    """
    )
    public void logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request);
    }
}
