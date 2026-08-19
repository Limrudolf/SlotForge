package com.slotforge.api.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.refreshtoken.RefreshToken;
import com.slotforge.api.refreshtoken.RefreshTokenRepository;
import com.slotforge.api.refreshtoken.RefreshTokenRevocationReason;
import com.slotforge.api.user.UserAccount;
import com.slotforge.api.user.UserAccountRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class AuthRefreshIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    void validRefreshTokenRotatesAndLinksReplacement()
            throws Exception {

        String email = "rotation@example.com";
        String password = "Rotation-Password-42!";
        register(email, password);

        String originalRawToken = loginForRefreshToken(
                email,
                password
        );

        HttpResponse<String> response = refresh(originalRawToken);

        assertEquals(200, response.statusCode());

        JsonNode body = objectMapper.readTree(response.body());
        String replacementRawToken =
                body.get("refreshToken").asText();

        assertTrue(body.hasNonNull("accessToken"));
        assertTrue(replacementRawToken.startsWith("rfr_"));

        RefreshToken original = refreshTokenRepository
                .findByTokenHash(sha256(originalRawToken))
                .orElseThrow();

        RefreshToken replacement = refreshTokenRepository
                .findByTokenHash(sha256(replacementRawToken))
                .orElseThrow();

        assertEquals(
                RefreshTokenRevocationReason.ROTATED,
                original.getRevocationReason()
        );
        assertNotNull(original.getLastUsedAt());
        assertEquals(
                replacement.getId(),
                original.getReplacedByToken().getId()
        );
        assertEquals(
                original.getFamilyId(),
                replacement.getFamilyId()
        );
    }

    @Test
    void reuseOfRotatedTokenRevokesReplacementFamily()
            throws Exception {

        String email = "reuse@example.com";
        String password = "Reuse-Password-42!";
        register(email, password);

        String original = loginForRefreshToken(email, password);
        HttpResponse<String> firstRotation = refresh(original);
        assertEquals(200, firstRotation.statusCode());

        String replacement = objectMapper
                .readTree(firstRotation.body())
                .get("refreshToken")
                .asText();

        HttpResponse<String> replayResponse = refresh(original);
        assertGenericUnauthorized(replayResponse);

        RefreshToken revokedReplacement = refreshTokenRepository
                .findByTokenHash(sha256(replacement))
                .orElseThrow();

        assertEquals(
                RefreshTokenRevocationReason.REUSE_DETECTED,
                revokedReplacement.getRevocationReason()
        );

        assertGenericUnauthorized(refresh(replacement));
    }

    @Test
    void concurrentUseCannotCreateTwoActiveReplacements()
            throws Exception {

        String email = "concurrent.refresh@example.com";
        String password = "Concurrent-Refresh-Password-42!";
        register(email, password);

        String original = loginForRefreshToken(email, password);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            Future<HttpResponse<String>> first = executor.submit(() -> {
                start.await();
                return refresh(original);
            });

            Future<HttpResponse<String>> second = executor.submit(() -> {
                start.await();
                return refresh(original);
            });

            start.countDown();

            List<Integer> statuses = List.of(
                    first.get().statusCode(),
                    second.get().statusCode()
            );

            assertEquals(1, statuses.stream()
                    .filter(status -> status == 200)
                    .count());
            assertEquals(1, statuses.stream()
                    .filter(status -> status == 401)
                    .count());
        }

        UserAccount user = userAccountRepository
                .findByEmail(email)
                .orElseThrow();

        assertTrue(
                refreshTokenRepository
                        .findAllByUserIdAndRevokedAtIsNull(user.getId())
                        .isEmpty()
        );
    }

    @Test
    void unknownRefreshTokenReturnsGenericUnauthorizedResponse()
            throws Exception {

        assertGenericUnauthorized(
                refresh("rfr_unknown-token-value")
        );
    }

    private void assertGenericUnauthorized(
            HttpResponse<String> response
    ) {
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains(
                "\"message\":\"Refresh token is invalid or expired\""
        ));
    }

    private void register(String email, String password)
            throws Exception {
        HttpResponse<String> response = postJson(
                "/api/v1/auth/register",
                credentials(email, password)
        );
        assertEquals(201, response.statusCode());
    }

    private String loginForRefreshToken(
            String email,
            String password
    ) throws Exception {
        HttpResponse<String> response = postJson(
                "/api/v1/auth/login",
                credentials(email, password)
        );
        assertEquals(200, response.statusCode());
        return objectMapper.readTree(response.body())
                .get("refreshToken")
                .asText();
    }

    private HttpResponse<String> refresh(String refreshToken)
            throws Exception {
        return postJson(
                "/api/v1/auth/refresh",
                """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken)
        );
    }

    private String credentials(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private HttpResponse<String> postJson(
            String path,
            String body
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(
                value.getBytes(StandardCharsets.UTF_8)
        ));
    }
}
