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

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class AuthLogoutIntegrationTests {

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
    void logoutRevokesRefreshFamilyAndPreventsRefresh()
            throws Exception {

        String email = "logout@example.com";
        String password = "Logout-Password-42!";
        register(email, password);

        String refreshToken = loginForRefreshToken(email, password);

        HttpResponse<String> logoutResponse = logout(refreshToken);

        assertEquals(204, logoutResponse.statusCode());
        assertTrue(logoutResponse.body().isEmpty());

        RefreshToken storedToken = refreshTokenRepository
                .findByTokenHash(sha256(refreshToken))
                .orElseThrow();

        assertEquals(
                RefreshTokenRevocationReason.LOGOUT,
                storedToken.getRevocationReason()
        );

        assertEquals(401, refresh(refreshToken).statusCode());
    }

    @Test
    void repeatedAndUnknownLogoutRemainSuccessful()
            throws Exception {

        String email = "repeated.logout@example.com";
        String password = "Repeated-Logout-Password-42!";
        register(email, password);

        String refreshToken = loginForRefreshToken(email, password);

        assertEquals(204, logout(refreshToken).statusCode());
        assertEquals(204, logout(refreshToken).statusCode());
        assertEquals(
                204,
                logout("rfr_unknown-token").statusCode()
        );
    }

    @Test
    void concurrentRefreshAndLogoutLeaveNoActiveSession()
            throws Exception {

        String email = "refresh.logout.race@example.com";
        String password = "Refresh-Logout-Race-42!";
        register(email, password);

        String refreshToken = loginForRefreshToken(email, password);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            Future<HttpResponse<String>> refreshFuture =
                    executor.submit(() -> {
                        start.await();
                        return refresh(refreshToken);
                    });

            Future<HttpResponse<String>> logoutFuture =
                    executor.submit(() -> {
                        start.await();
                        return logout(refreshToken);
                    });

            start.countDown();

            int refreshStatus = refreshFuture.get().statusCode();
            int logoutStatus = logoutFuture.get().statusCode();

            assertTrue(List.of(200, 401).contains(refreshStatus));
            assertEquals(204, logoutStatus);
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
                tokenBody(refreshToken)
        );
    }

    private HttpResponse<String> logout(String refreshToken)
            throws Exception {
        return postJson(
                "/api/v1/auth/logout",
                tokenBody(refreshToken)
        );
    }

    private String tokenBody(String refreshToken) {
        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);
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
