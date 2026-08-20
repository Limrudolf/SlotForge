package com.slotforge.api.session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.SecurityTestTokenFactory;
import com.slotforge.api.SecurityTestTokenFactory.TestIdentity;
import com.slotforge.api.availability.BookingSlot;
import com.slotforge.api.availability.BookingSlotRepository;
import com.slotforge.api.event.Event;
import com.slotforge.api.event.EventRepository;
import com.slotforge.api.security.JwtService;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.RoleRepository;
import com.slotforge.api.user.UserAccountRepository;
import com.slotforge.api.venue.Venue;
import com.slotforge.api.venue.VenueRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class SessionApiIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private BookingSlotRepository bookingSlotRepository;

    @Autowired
    private EventSessionRepository eventSessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private TestIdentity organizerIdentity;

    @BeforeEach
    void clearDatabase() {
        bookingSlotRepository.deleteAll();
        eventSessionRepository.deleteAll();
        eventRepository.deleteAll();
        venueRepository.deleteAll();
        organizerIdentity = SecurityTestTokenFactory.createIdentity(
                RoleName.ORGANIZER,
                userAccountRepository,
                roleRepository,
                jwtService
        );
    }

    @Test
    void createSessionStoresUtcAndInitializesAvailability()
            throws Exception {

        TestFixtures fixtures = createFixtures();

        HttpResponse<String> createResponse = createSession(
                fixtures,
                "2026-10-10T19:00:00+02:00",
                "2026-10-10T22:00:00+02:00",
                500
        );

        assertEquals(201, createResponse.statusCode());

        List<EventSession> sessions = eventSessionRepository.findAll();
        assertEquals(1, sessions.size());

        EventSession session = sessions.getFirst();

        assertEquals(
                Instant.parse("2026-10-10T17:00:00Z"),
                session.getStartTimeUtc()
        );
        assertEquals(
                Instant.parse("2026-10-10T20:00:00Z"),
                session.getEndTimeUtc()
        );
        assertEquals(
                "Europe/Stockholm",
                session.getDisplayTimezone()
        );
        assertEquals(10_000L, session.getUnitPriceMinor());
        assertEquals(
                java.util.Currency.getInstance("SEK"),
                session.getCurrency()
        );
        assertEquals(
                EventSessionStatus.SCHEDULED,
                session.getStatus()
        );

        BookingSlot bookingSlot = bookingSlotRepository
                .findByEventSession_Id(session.getId())
                .orElseThrow();

        assertEquals(500, bookingSlot.getTotalCapacity());
        assertEquals(500, bookingSlot.getRemainingCapacity());

        assertEquals(
                "/api/v1/sessions/" + session.getId(),
                createResponse.headers()
                        .firstValue("Location")
                        .orElseThrow()
        );

        assertTrue(createResponse.body().contains(
                "\"startTimeUtc\":\"2026-10-10T17:00:00Z\""
        ));
        assertTrue(createResponse.body().contains(
                "\"endTimeUtc\":\"2026-10-10T20:00:00Z\""
        ));
        assertTrue(createResponse.body().contains(
                "\"displayTimezone\":\"Europe/Stockholm\""
        ));
        assertTrue(createResponse.body().contains(
                "\"unitPriceMinor\":10000"
        ));
        assertTrue(createResponse.body().contains(
                "\"currency\":\"SEK\""
        ));

        HttpResponse<String> availabilityResponse = sendGet(
                "/api/v1/sessions/"
                        + session.getId()
                        + "/availability"
        );

        assertEquals(200, availabilityResponse.statusCode());
        assertTrue(availabilityResponse.body().contains(
                "\"sessionId\":\"" + session.getId() + "\""
        ));
        assertTrue(availabilityResponse.body().contains(
                "\"totalCapacity\":500"
        ));
        assertTrue(availabilityResponse.body().contains(
                "\"remainingCapacity\":500"
        ));
    }

    @Test
    void anotherOrganizerCannotCreateSessionForEvent() throws Exception {
        TestFixtures fixtures = createFixtures();
        TestIdentity otherOrganizer = SecurityTestTokenFactory.createIdentity(
                RoleName.ORGANIZER,
                userAccountRepository,
                roleRepository,
                jwtService
        );

        HttpResponse<String> response = sendJson(
                "POST",
                sessionCreationPath(fixtures.event().getId()),
                sessionJson(
                        fixtures.venue().getId(),
                        "2026-10-10T19:00:00+02:00",
                        "2026-10-10T22:00:00+02:00",
                        "Europe/Stockholm",
                        500
                ),
                otherOrganizer.accessToken()
        );

        assertEquals(403, response.statusCode());
        assertEquals(0, eventSessionRepository.count());
        assertEquals(0, bookingSlotRepository.count());
    }

    @Test
    void adminCanCreateSessionForAnotherOrganizersEvent()
            throws Exception {
        TestFixtures fixtures = createFixtures();
        TestIdentity admin = SecurityTestTokenFactory.createIdentity(
                RoleName.ADMIN,
                userAccountRepository,
                roleRepository,
                jwtService
        );

        HttpResponse<String> response = sendJson(
                "POST",
                sessionCreationPath(fixtures.event().getId()),
                sessionJson(
                        fixtures.venue().getId(),
                        "2026-10-10T19:00:00+02:00",
                        "2026-10-10T22:00:00+02:00",
                        "Europe/Stockholm",
                        500
                ),
                admin.accessToken()
        );

        assertEquals(201, response.statusCode());
        assertEquals(1, eventSessionRepository.count());
        assertEquals(1, bookingSlotRepository.count());
    }

    @Test
    void getAndListSessionsReturnChronologicalPaginatedResults()
            throws Exception {

        TestFixtures fixtures = createFixtures();

        assertEquals(
                201,
                createSession(
                        fixtures,
                        "2026-12-20T19:00:00+01:00",
                        "2026-12-20T21:00:00+01:00",
                        200
                ).statusCode()
        );
        assertEquals(
                201,
                createSession(
                        fixtures,
                        "2026-11-20T19:00:00+01:00",
                        "2026-11-20T21:00:00+01:00",
                        100
                ).statusCode()
        );

        List<EventSession> sessions = eventSessionRepository
                .findAll()
                .stream()
                .sorted(Comparator.comparing(
                        EventSession::getStartTimeUtc
                ))
                .toList();

        EventSession earlierSession = sessions.get(0);
        EventSession laterSession = sessions.get(1);

        HttpResponse<String> firstPage = sendGet(
                "/api/v1/events/"
                        + fixtures.event().getId()
                        + "/sessions?page=0&size=1"
        );

        assertEquals(200, firstPage.statusCode());
        assertTrue(firstPage.body().contains(
                "\"id\":\"" + earlierSession.getId() + "\""
        ));
        assertFalse(firstPage.body().contains(
                "\"id\":\"" + laterSession.getId() + "\""
        ));
        assertTrue(firstPage.body().contains("\"totalElements\":2"));
        assertTrue(firstPage.body().contains("\"totalPages\":2"));
        assertTrue(firstPage.body().contains("\"first\":true"));
        assertTrue(firstPage.body().contains("\"last\":false"));

        HttpResponse<String> secondPage = sendGet(
                "/api/v1/events/"
                        + fixtures.event().getId()
                        + "/sessions?page=1&size=1"
        );

        assertEquals(200, secondPage.statusCode());
        assertTrue(secondPage.body().contains(
                "\"id\":\"" + laterSession.getId() + "\""
        ));
        assertTrue(secondPage.body().contains("\"last\":true"));

        HttpResponse<String> getResponse = sendGet(
                "/api/v1/sessions/" + earlierSession.getId()
        );

        assertEquals(200, getResponse.statusCode());
        assertTrue(getResponse.body().contains(
                "\"id\":\"" + earlierSession.getId() + "\""
        ));
        assertTrue(getResponse.body().contains(
                "\"eventId\":\""
                        + fixtures.event().getId()
                        + "\""
        ));
        assertTrue(getResponse.body().contains(
                "\"venueId\":\""
                        + fixtures.venue().getId()
                        + "\""
        ));
    }

    @Test
    void invalidSessionRequestsDoNotPersistData()
            throws Exception {

        TestFixtures fixtures = createFixtures();
        String endpoint = sessionCreationPath(fixtures.event().getId());

        HttpResponse<String> invalidTimeResponse = sendJson(
                "POST",
                endpoint,
                sessionJson(
                        fixtures.venue().getId(),
                        "2026-10-10T22:00:00+02:00",
                        "2026-10-10T19:00:00+02:00",
                        "Europe/Stockholm",
                        500
                )
        );

        assertEquals(400, invalidTimeResponse.statusCode());
        assertTrue(invalidTimeResponse.body().contains(
                "End time must be after start time"
        ));

        HttpResponse<String> invalidTimezoneResponse = sendJson(
                "POST",
                endpoint,
                sessionJson(
                        fixtures.venue().getId(),
                        "2026-10-10T19:00:00+02:00",
                        "2026-10-10T22:00:00+02:00",
                        "Stockholm",
                        500
                )
        );

        assertEquals(400, invalidTimezoneResponse.statusCode());
        assertTrue(invalidTimezoneResponse.body().contains(
                "Must be a valid IANA timezone"
        ));

        HttpResponse<String> invalidCapacityResponse = sendJson(
                "POST",
                endpoint,
                sessionJson(
                        fixtures.venue().getId(),
                        "2026-10-10T19:00:00+02:00",
                        "2026-10-10T22:00:00+02:00",
                        "Europe/Stockholm",
                        0
                )
        );

        assertEquals(400, invalidCapacityResponse.statusCode());
        assertTrue(invalidCapacityResponse.body().contains(
                "Total capacity must be greater than zero"
        ));

        HttpResponse<String> missingOffsetResponse = sendJson(
                "POST",
                endpoint,
                sessionJson(
                        fixtures.venue().getId(),
                        "2026-10-10T19:00:00",
                        "2026-10-10T22:00:00",
                        "Europe/Stockholm",
                        500
                )
        );

        assertEquals(400, missingOffsetResponse.statusCode());
        assertTrue(missingOffsetResponse.body().contains(
                "Request body is malformed or contains an invalid value"
        ));

        assertEquals(0, eventSessionRepository.count());
        assertEquals(0, bookingSlotRepository.count());
    }

    @Test
    void missingEventOrVenueReturnsNotFoundWithoutPartialData()
            throws Exception {

        TestFixtures fixtures = createFixtures();
        UUID missingId = UUID.fromString(
                "00000000-0000-0000-0000-000000000000"
        );

        HttpResponse<String> missingEventResponse = sendJson(
                "POST",
                sessionCreationPath(missingId),
                sessionJson(
                        fixtures.venue().getId(),
                        "2026-10-10T19:00:00+02:00",
                        "2026-10-10T22:00:00+02:00",
                        "Europe/Stockholm",
                        500
                )
        );

        assertEquals(404, missingEventResponse.statusCode());
        assertTrue(missingEventResponse.body().contains(
                "Event not found: " + missingId
        ));

        HttpResponse<String> missingVenueResponse = sendJson(
                "POST",
                sessionCreationPath(fixtures.event().getId()),
                sessionJson(
                        missingId,
                        "2026-10-10T19:00:00+02:00",
                        "2026-10-10T22:00:00+02:00",
                        "Europe/Stockholm",
                        500
                )
        );

        assertEquals(404, missingVenueResponse.statusCode());
        assertTrue(missingVenueResponse.body().contains(
                "Venue not found: " + missingId
        ));

        assertEquals(0, eventSessionRepository.count());
        assertEquals(0, bookingSlotRepository.count());
    }

    @Test
    void missingSessionReturnsNotFoundForSessionAndAvailability()
            throws Exception {

        UUID missingId = UUID.fromString(
                "00000000-0000-0000-0000-000000000000"
        );

        HttpResponse<String> sessionResponse = sendGet(
                "/api/v1/sessions/" + missingId
        );
        HttpResponse<String> availabilityResponse = sendGet(
                "/api/v1/sessions/"
                        + missingId
                        + "/availability"
        );

        assertEquals(404, sessionResponse.statusCode());
        assertEquals(404, availabilityResponse.statusCode());
        assertTrue(sessionResponse.body().contains(
                "Event session not found: " + missingId
        ));
        assertTrue(availabilityResponse.body().contains(
                "Event session not found: " + missingId
        ));
    }

    @Test
    void missingEventSessionListIsNotTheSameAsAnEmptyList()
            throws Exception {

        Event emptyEvent = eventRepository.saveAndFlush(
                new Event(
                        "Empty Event",
                        null,
                        organizerIdentity.user()
                )
        );
        UUID missingId = UUID.fromString(
                "00000000-0000-0000-0000-000000000000"
        );

        HttpResponse<String> emptyResponse = sendGet(
                "/api/v1/events/"
                        + emptyEvent.getId()
                        + "/sessions"
        );
        HttpResponse<String> missingResponse = sendGet(
                "/api/v1/events/" + missingId + "/sessions"
        );

        assertEquals(200, emptyResponse.statusCode());
        assertTrue(emptyResponse.body().contains("\"content\":[]"));
        assertTrue(emptyResponse.body().contains("\"totalElements\":0"));

        assertEquals(404, missingResponse.statusCode());
        assertTrue(missingResponse.body().contains(
                "Event not found: " + missingId
        ));
    }

    private TestFixtures createFixtures() {
        Event event = eventRepository.saveAndFlush(
                new Event(
                        "Session Test Event",
                        null,
                        organizerIdentity.user()
                )
        );

        Venue venue = venueRepository.saveAndFlush(
                new Venue(
                        "Session Test Venue",
                        "1 Test Street",
                        null,
                        "Stockholm",
                        null,
                        "111 11",
                        "SE"
                )
        );

        return new TestFixtures(event, venue);
    }

    private HttpResponse<String> createSession(
            TestFixtures fixtures,
            String startTime,
            String endTime,
            int capacity
    ) throws Exception {
        return sendJson(
                "POST",
                sessionCreationPath(fixtures.event().getId()),
                sessionJson(
                        fixtures.venue().getId(),
                        startTime,
                        endTime,
                        "Europe/Stockholm",
                        capacity
                )
        );
    }

    private String sessionCreationPath(UUID eventId) {
        return "/api/v1/events/" + eventId + "/sessions";
    }

    private String sessionJson(
            UUID venueId,
            String startTime,
            String endTime,
            String displayTimezone,
            int capacity
    ) {
        return """
               {
                 "venueId": "%s",
                 "startTime": "%s",
                 "endTime": "%s",
                 "displayTimezone": "%s",
                 "totalCapacity": %d,
                 "unitPriceMinor": 10000,
                 "currency": "SEK"
               }
               """.formatted(
                venueId,
                startTime,
                endTime,
                displayTimezone,
                capacity
        );
    }

    private HttpResponse<String> sendGet(String path)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri(path))
                .GET()
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> sendJson(
            String method,
            String path,
            String body
    ) throws Exception {
        return sendJson(
                method,
                path,
                body,
                organizerIdentity.accessToken()
        );
    }

    private HttpResponse<String> sendJson(
            String method,
            String path,
            String body,
            String accessToken
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Content-Type", "application/json")
                .header(
                        "Authorization",
                        "Bearer " + accessToken
                )
                .method(
                        method,
                        HttpRequest.BodyPublishers.ofString(body)
                )
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private record TestFixtures(Event event, Venue venue) {
    }
}
