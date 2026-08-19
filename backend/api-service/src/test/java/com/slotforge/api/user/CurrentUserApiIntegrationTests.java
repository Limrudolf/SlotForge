package com.slotforge.api.user;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.slotforge.api.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class CurrentUserApiIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validAccessTokenReturnsCurrentAccount()
            throws Exception {

        register();

        String accessToken = loginAndGetAccessToken();

        HttpResponse<String> response = getMe(accessToken);

        assertEquals(200, response.statusCode());

        JsonNode body = objectMapper.readTree(response.body());

        assertEquals(
                "current.user@example.com",
                body.get("email").asText()
        );
        assertEquals("ACTIVE", body.get("status").asText());
        assertTrue(
                response.body().contains("\"CUSTOMER\"")
        );
        assertTrue(body.hasNonNull("id"));
        assertTrue(body.hasNonNull("createdAt"));
    }

    @Test
    void missingAccessTokenReturnsStandardUnauthorizedError()
            throws Exception {

        HttpResponse<String> response = getMe(null);

        assertEquals(401, response.statusCode());
        assertTrue(
                response.body().contains("\"status\":401")
        );
        assertTrue(
                response.body().contains(
                        "\"message\":\"Authentication is required"
                                + " or the access token is invalid\""
                )
        );
        assertTrue(
                response.body().contains(
                        "\"path\":\"/api/v1/me\""
                )
        );
    }

    @Test
    void malformedAccessTokenReturnsStandardUnauthorizedError()
            throws Exception {

        HttpResponse<String> response =
                getMe("not-a-valid-jwt");

        assertEquals(401, response.statusCode());
        assertTrue(
                response.body().contains("\"status\":401")
        );
        assertTrue(
                response.body().contains(
                        "\"message\":\"Authentication is required"
                                + " or the access token is invalid\""
                )
        );
    }

    private void register() throws Exception {
        HttpResponse<String> response = postJson(
                "/api/v1/auth/register",
                """
                {
                  "email": "current.user@example.com",
                  "password": "Current-User-Password-42!"
                }
                """
        );

        assertEquals(201, response.statusCode());
    }

    private String loginAndGetAccessToken() throws Exception {
        HttpResponse<String> response = postJson(
                "/api/v1/auth/login",
                """
                {
                  "email": "current.user@example.com",
                  "password": "Current-User-Password-42!"
                }
                """
        );

        assertEquals(200, response.statusCode());

        return objectMapper
                .readTree(response.body())
                .get("accessToken")
                .asText();
    }

    private HttpResponse<String> getMe(String accessToken)
            throws Exception {

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/me"))
                .GET();

        if (accessToken != null) {
            request.header(
                    "Authorization",
                    "Bearer " + accessToken
            );
        }

        return httpClient.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> postJson(
            String path,
            String body
    ) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private URI uri(String path) {
        return URI.create(
                "http://localhost:" + port + path
        );
    }
}
