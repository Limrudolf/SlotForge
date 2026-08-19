package com.slotforge.api.auth;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.refreshtoken.RefreshToken;
import com.slotforge.api.refreshtoken.RefreshTokenRepository;
import com.slotforge.api.user.UserAccount;
import com.slotforge.api.user.UserAccountRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class AuthLoginIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void registeredCustomerCanLoginAndReceiveAccessToken()
            throws Exception {

        register(
                "login.customer@example.com",
                "Strong-Login-Password-42!"
        );

        HttpResponse<String> response = login(
                "  LOGIN.CUSTOMER@EXAMPLE.COM ",
                "Strong-Login-Password-42!"
        );

        assertEquals(200, response.statusCode());

        JsonNode responseBody =
                objectMapper.readTree(response.body());

        assertEquals(
                "Bearer",
                responseBody.get("tokenType").asText()
        );
        assertTrue(responseBody.hasNonNull("accessToken"));
        assertTrue(
                responseBody.hasNonNull("accessTokenExpiresAt")
        );

        UserAccount user = userAccountRepository
                .findByEmail("login.customer@example.com")
                .orElseThrow();

        assertTrue(responseBody.hasNonNull("refreshToken"));
        assertTrue(
                responseBody.hasNonNull("refreshTokenExpiresAt")
        );

        String rawRefreshToken =
                responseBody.get("refreshToken").asText();

        assertTrue(rawRefreshToken.startsWith("rfr_"));

        List<RefreshToken> storedTokens =
                refreshTokenRepository
                        .findAllByUserIdAndRevokedAtIsNull(
                                user.getId()
                        );

        assertEquals(1, storedTokens.size());

        RefreshToken storedToken = storedTokens.getFirst();

        assertFalse(
                storedToken.getTokenHash().equals(rawRefreshToken)
        );
        assertEquals(
                sha256(rawRefreshToken),
                storedToken.getTokenHash()
        );

        Jwt token = jwtDecoder.decode(
                responseBody.get("accessToken").asText()
        );

        assertEquals(
                user.getId().toString(),
                token.getSubject()
        );
        assertTrue(
                token.getClaimAsStringList("roles")
                        .contains("CUSTOMER")
        );
    }

    @Test
    void incorrectPasswordReturnsGenericUnauthorizedResponse()
            throws Exception {

        register(
                "wrong.password@example.com",
                "Correct-Password-42!"
        );

        HttpResponse<String> response = login(
                "wrong.password@example.com",
                "Incorrect-Password-43!"
        );

        assertGenericUnauthorized(response);
    }

    @Test
    void unknownEmailReturnsSameGenericUnauthorizedResponse()
            throws Exception {

        HttpResponse<String> response = login(
                "unknown.account@example.com",
                "Some-Password-42!"
        );

        assertGenericUnauthorized(response);
    }

    private void assertGenericUnauthorized(
            HttpResponse<String> response
    ) {
        assertEquals(401, response.statusCode());
        assertTrue(
                response.body().contains(
                        "\"message\":\"Email or password is incorrect\""
                )
        );
        assertFalse(response.body().contains("Unknown email"));
        assertFalse(response.body().contains("disabled"));
    }

    private void register(String email, String password)
            throws Exception {

        HttpResponse<String> response = sendJson(
                "/api/v1/auth/register",
                """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password)
        );

        assertEquals(201, response.statusCode());
    }

    private HttpResponse<String> login(
            String email,
            String password
    ) throws Exception {

        return sendJson(
                "/api/v1/auth/login",
                """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password)
        );
    }

    private HttpResponse<String> sendJson(
            String path,
            String body
    ) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + path
                ))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        byte[] hashed = digest.digest(
                value.getBytes(StandardCharsets.UTF_8)
        );

        return HexFormat.of().formatHex(hashed);
    }
}
