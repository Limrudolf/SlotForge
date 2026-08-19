package com.slotforge.api.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.UserAccount;
import com.slotforge.api.user.UserAccountRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class AuthRegistrationIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registrationCreatesNormalizedCustomerAccount()
            throws Exception {

        HttpResponse<String> response = register(
                """
                {
                  "email": "  New.Customer@Example.COM  ",
                  "password": "Strong-Test-Password-42!"
                }
                """
        );

        assertEquals(201, response.statusCode());

        UserAccount user = userAccountRepository
                .findByEmail("new.customer@example.com")
                .orElseThrow();

        assertEquals(
                "new.customer@example.com",
                user.getEmail()
        );
        assertTrue(user.isActive());
        assertTrue(
                passwordEncoder.matches(
                        "Strong-Test-Password-42!",
                        user.getPasswordHash()
                )
        );
        assertFalse(
                user.getPasswordHash().contains(
                        "Strong-Test-Password-42!"
                )
        );

        assertEquals(1, user.getRoles().size());
        assertEquals(
                RoleName.CUSTOMER,
                user.getRoles().iterator().next().getName()
        );

        assertTrue(
                response.body().contains(
                        "\"email\":\"new.customer@example.com\""
                )
        );
        assertTrue(
                response.body().contains("\"CUSTOMER\"")
        );
        assertFalse(response.body().contains("passwordHash"));
        assertFalse(
                response.body().contains(
                        "Strong-Test-Password-42!"
                )
        );
    }

    @Test
    void duplicateNormalizedEmailReturnsConflict()
            throws Exception {

        String firstRequest = """
                {
                  "email": "duplicate@example.com",
                  "password": "Strong-Test-Password-42!"
                }
                """;

        HttpResponse<String> firstResponse =
                register(firstRequest);

        HttpResponse<String> duplicateResponse = register(
                """
                {
                  "email": "  DUPLICATE@EXAMPLE.COM ",
                  "password": "Another-Strong-Password-43!"
                }
                """
        );

        assertEquals(201, firstResponse.statusCode());
        assertEquals(409, duplicateResponse.statusCode());
        assertTrue(
                duplicateResponse.body().contains("\"status\":409")
        );
        assertTrue(
                duplicateResponse.body().contains(
                        "\"message\":\"An account cannot be created"
                                + " with that email\""
                )
        );
    }

    @Test
    void invalidRegistrationReturnsValidationErrors()
            throws Exception {

        HttpResponse<String> response = register(
                """
                {
                  "email": "not-an-email",
                  "password": "short"
                }
                """
        );

        assertEquals(400, response.statusCode());
        assertTrue(
                response.body().contains(
                        "\"message\":\"Request validation failed\""
                )
        );
        assertTrue(response.body().contains("\"field\":\"email\""));
        assertTrue(
                response.body().contains("\"field\":\"password\"")
        );
    }

    private HttpResponse<String> register(String body)
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:"
                                + port
                                + "/api/v1/auth/register"
                ))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }
}
