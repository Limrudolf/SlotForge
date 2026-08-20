package com.slotforge.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.slotforge.api.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CorsIntegrationTests {

    private static final String ALLOWED_ORIGIN =
            "http://localhost:5173";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void allowedOriginPreflightSucceedsWithoutAuthentication()
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/events"))
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header(
                        "Access-Control-Request-Headers",
                        "authorization,content-type,idempotency-key,"
                                + "x-correlation-id"
                )
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request);

        assertEquals(200, response.statusCode());
        assertEquals(
                ALLOWED_ORIGIN,
                response.headers()
                        .firstValue("Access-Control-Allow-Origin")
                        .orElseThrow()
        );
        assertFalse(response.headers()
                .firstValue("Access-Control-Allow-Credentials")
                .isPresent());
    }

    @Test
    void disallowedOriginPreflightIsRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/events"))
                .header("Origin", "https://attacker.example")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request);

        assertEquals(403, response.statusCode());
        assertFalse(response.headers()
                .firstValue("Access-Control-Allow-Origin")
                .isPresent());
    }

    @Test
    void corsApprovalDoesNotBypassAuthentication() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/events"))
                .header("Origin", ALLOWED_ORIGIN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{ \"name\": \"Unauthenticated\" }"
                ))
                .build();

        HttpResponse<String> response = send(request);

        assertEquals(401, response.statusCode());
        assertEquals(
                ALLOWED_ORIGIN,
                response.headers()
                        .firstValue("Access-Control-Allow-Origin")
                        .orElseThrow()
        );
    }

    private HttpResponse<String> send(HttpRequest request)
            throws Exception {
        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
