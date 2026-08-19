package com.slotforge.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class OpenApiIntegrationTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void openApiDocumentContainsSprintOneContract()
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:"
                                + port
                                + "/v3/api-docs"
                ))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient
                .newHttpClient()
                .send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        assertEquals(200, response.statusCode());

        String document = response.body();

        assertTrue(document.contains("\"title\":\"SlotForge API\""));
        assertTrue(document.contains("\"version\":\"v1\""));

        assertPathExists(document, "/api/v1/venues");
        assertPathExists(document, "/api/v1/events");
        assertPathExists(document, "/api/v1/events/{eventId}");
        assertPathExists(
                document,
                "/api/v1/events/{eventId}/sessions"
        );
        assertPathExists(document, "/api/v1/sessions/{sessionId}");
        assertPathExists(
                document,
                "/api/v1/sessions/{sessionId}/availability"
        );
        assertPathExists(document, "/api/v1/auth/login");
        assertPathExists(document, "/api/v1/me");
        assertPathExists(document, "/api/v1/admin/audit-logs");

        assertTrue(document.contains("\"summary\":\"Create an event\""));
        assertTrue(document.contains(
                "\"summary\":\"Create an event session\""
        ));
        assertTrue(document.contains(
                "\"summary\":\"Get session availability\""
        ));
        assertTrue(document.contains("\"201\""));
        assertTrue(document.contains("\"400\""));
        assertTrue(document.contains("\"404\""));
        assertTrue(document.contains("\"409\""));
        assertTrue(document.contains("\"ApiError\""));

        JsonNode root = objectMapper.readTree(document);
        JsonNode scheme = root.at(
                "/components/securitySchemes/bearerAuth"
        );
        assertEquals("http", scheme.path("type").asText());
        assertEquals("bearer", scheme.path("scheme").asText());
        assertEquals("JWT", scheme.path("bearerFormat").asText());

        assertBearerProtected(root, "/api/v1/events", "post");
        assertBearerProtected(
                root,
                "/api/v1/events/{eventId}",
                "patch"
        );
        assertBearerProtected(root, "/api/v1/me", "get");
        assertBearerProtected(
                root,
                "/api/v1/admin/audit-logs",
                "get"
        );
        assertTrue(root.at("/paths/~1api~1v1~1events/get/security")
                .isMissingNode());
        assertTrue(root.at("/paths/~1api~1v1~1auth~1login/post/security")
                .isMissingNode());
    }

    private void assertPathExists(String document, String path) {
        assertTrue(
                document.contains("\"" + path + "\""),
                () -> "OpenAPI path is missing: " + path
        );
    }

    private void assertBearerProtected(
            JsonNode root,
            String path,
            String method
    ) {
        String pointerPath = path.replace("~", "~0").replace("/", "~1");
        JsonNode security = root.at(
                "/paths/" + pointerPath + "/" + method + "/security"
        );
        assertTrue(security.isArray() && !security.isEmpty());
        assertTrue(security.get(0).has("bearerAuth"));
    }
}
