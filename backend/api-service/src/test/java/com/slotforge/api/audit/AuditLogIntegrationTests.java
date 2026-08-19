package com.slotforge.api.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.slotforge.api.SecurityTestTokenFactory;
import com.slotforge.api.SecurityTestTokenFactory.TestIdentity;
import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.availability.BookingSlotRepository;
import com.slotforge.api.event.Event;
import com.slotforge.api.event.EventRepository;
import com.slotforge.api.security.CorrelationIdFilter;
import com.slotforge.api.security.JwtService;
import com.slotforge.api.session.EventSessionRepository;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.RoleRepository;
import com.slotforge.api.user.UserAccountRepository;
import com.slotforge.api.venue.Venue;
import com.slotforge.api.venue.VenueRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuditLogIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private BookingSlotRepository bookingSlotRepository;
    @Autowired private EventSessionRepository eventSessionRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JwtService jwtService;

    private TestIdentity organizer;

    @BeforeEach
    void clearDatabase() {
        auditLogRepository.deleteAll();
        bookingSlotRepository.deleteAll();
        eventSessionRepository.deleteAll();
        eventRepository.deleteAll();
        venueRepository.deleteAll();
        organizer = identity(RoleName.ORGANIZER);
    }

    @Test
    void eventCreationRecordsActorAndPropagatesCorrelationId()
            throws Exception {
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> response = send(
                "POST",
                "/api/v1/events",
                """
                { "name": "Audited Event" }
                """,
                organizer.accessToken(),
                correlationId
        );

        assertEquals(201, response.statusCode());
        assertEquals(
                correlationId.toString(),
                response.headers()
                        .firstValue(CorrelationIdFilter.HEADER_NAME)
                        .orElseThrow()
        );

        AuditLog auditLog = auditLogRepository.findAll().getFirst();
        Event event = eventRepository.findAll().getFirst();
        assertEquals(organizer.user().getId(), auditLog.getActorUserId());
        assertEquals(AuditAction.EVENT_CREATED, auditLog.getAction());
        assertEquals(AuditEntityType.EVENT, auditLog.getEntityType());
        assertEquals(event.getId(), auditLog.getEntityId());
        assertEquals(correlationId, auditLog.getCorrelationId());
        assertEquals("Audited Event", auditLog.getDetails().get("name"));
    }

    @Test
    void sessionCreationRecordsOneBusinessAuditEntry() throws Exception {
        Event event = eventRepository.saveAndFlush(
                new Event("Owned Event", null, organizer.user())
        );
        Venue venue = venueRepository.saveAndFlush(new Venue(
                "Audited Venue", "1 Main Street", null,
                "Stockholm", null, "111 11", "SE"
        ));

        HttpResponse<String> response = send(
                "POST",
                "/api/v1/events/" + event.getId() + "/sessions",
                """
                {
                  "venueId": "%s",
                  "startTime": "2026-10-10T19:00:00+02:00",
                  "endTime": "2026-10-10T22:00:00+02:00",
                  "displayTimezone": "Europe/Stockholm",
                  "totalCapacity": 500
                }
                """.formatted(venue.getId()),
                organizer.accessToken(),
                null
        );

        assertEquals(201, response.statusCode());
        assertEquals(1, auditLogRepository.count());
        AuditLog auditLog = auditLogRepository.findAll().getFirst();
        assertEquals(AuditAction.EVENT_SESSION_CREATED, auditLog.getAction());
        assertEquals(
                eventSessionRepository.findAll().getFirst().getId(),
                auditLog.getEntityId()
        );
        assertEquals("500", auditLog.getDetails()
                .get("totalCapacity").toString());
    }

    @Test
    void rejectedOwnershipMutationCreatesNoAuditEntry()
            throws Exception {
        Event event = eventRepository.saveAndFlush(
                new Event("Owned Event", null, organizer.user())
        );
        TestIdentity otherOrganizer = identity(RoleName.ORGANIZER);

        HttpResponse<String> response = send(
                "PATCH",
                "/api/v1/events/" + event.getId(),
                "{ \"name\": \"Forbidden\" }",
                otherOrganizer.accessToken(),
                null
        );

        assertEquals(403, response.statusCode());
        assertEquals(0, auditLogRepository.count());
    }

    @Test
    void onlyAdminCanReadAuditLogs() throws Exception {
        send(
                "POST",
                "/api/v1/events",
                "{ \"name\": \"Visible to Admin\" }",
                organizer.accessToken(),
                null
        );
        TestIdentity admin = identity(RoleName.ADMIN);

        HttpResponse<String> organizerResponse = get(
                "/api/v1/admin/audit-logs",
                organizer.accessToken()
        );
        HttpResponse<String> adminResponse = get(
                "/api/v1/admin/audit-logs",
                admin.accessToken()
        );

        assertEquals(403, organizerResponse.statusCode());
        assertEquals(200, adminResponse.statusCode());
        assertTrue(adminResponse.body().contains("EVENT_CREATED"));
        assertTrue(adminResponse.body().contains("Visible to Admin"));
    }

    @Test
    void auditFailureRollsBackTheBusinessMutation() throws Exception {
        Event event = eventRepository.saveAndFlush(
                new Event("Original Name", null, organizer.user())
        );
        TestIdentity admin = identity(RoleName.ADMIN);
        userAccountRepository.deleteById(admin.user().getId());
        userAccountRepository.flush();

        HttpResponse<String> response = send(
                "PATCH",
                "/api/v1/events/" + event.getId(),
                "{ \"name\": \"Must Roll Back\" }",
                admin.accessToken(),
                null
        );

        assertEquals(409, response.statusCode());
        assertEquals(
                "Original Name",
                eventRepository.findById(event.getId()).orElseThrow().getName()
        );
        assertEquals(0, auditLogRepository.count());
    }

    private TestIdentity identity(RoleName roleName) {
        return SecurityTestTokenFactory.createIdentity(
                roleName,
                userAccountRepository,
                roleRepository,
                jwtService
        );
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String body,
            String accessToken,
            UUID correlationId
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (correlationId != null) {
            request.header(
                    CorrelationIdFilter.HEADER_NAME,
                    correlationId.toString()
            );
        }
        return httpClient.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> get(String path, String accessToken)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
