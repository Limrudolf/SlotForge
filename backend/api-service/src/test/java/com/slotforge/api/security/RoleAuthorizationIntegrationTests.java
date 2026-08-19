package com.slotforge.api.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.slotforge.api.SecurityTestTokenFactory;
import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.RoleRepository;
import com.slotforge.api.user.UserAccountRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class RoleAuthorizationIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void anonymousMutationReturnsUnauthorized() throws Exception {
        HttpResponse<String> response = createEvent(null);

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"status\":401"));
    }

    @Test
    void customerMutationReturnsForbidden() throws Exception {
        String token = issueToken(RoleName.CUSTOMER);

        HttpResponse<String> response = createEvent(token);

        assertEquals(403, response.statusCode());
        assertTrue(response.body().contains("\"status\":403"));
        assertTrue(response.body().contains(
                "You do not have permission to access this resource"
        ));
    }

    @Test
    void organizerMutationIsAuthorized() throws Exception {
        String token = issueToken(RoleName.ORGANIZER);

        assertEquals(201, createEvent(token).statusCode());
    }

    @Test
    void adminMutationIsAuthorized() throws Exception {
        String token = issueToken(RoleName.ADMIN);

        assertEquals(201, createEvent(token).statusCode());
    }

    @Test
    void eventReadsRemainPublic() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/events"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
    }

    private String issueToken(RoleName roleName) {
        return SecurityTestTokenFactory.issueToken(
                roleName,
                userAccountRepository,
                roleRepository,
                jwtService
        );
    }

    private HttpResponse<String> createEvent(String accessToken)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/events"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        """
                        {
                          "name": "RBAC Test Event"
                        }
                        """
                ));

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

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
