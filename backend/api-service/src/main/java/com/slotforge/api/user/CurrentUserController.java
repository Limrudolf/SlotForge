package com.slotforge.api.user;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import static com.slotforge.api.common.config.OpenApiConfiguration.BEARER_AUTH;

@RestController
@RequestMapping("/api/v1/me")
@Tag(
        name = "Current User",
        description = "Operations for the authenticated account"
)
public class CurrentUserController {

    private final CurrentUserService currentUserService;

    public CurrentUserController(
            CurrentUserService currentUserService
    ) {
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Get the authenticated account",
            description = """
                    Returns current account information using the user UUID
                    established by the bearer access token.
                    """
    )
    public CurrentUserResponse getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return currentUserService.getCurrentUser(userId);
    }
}
