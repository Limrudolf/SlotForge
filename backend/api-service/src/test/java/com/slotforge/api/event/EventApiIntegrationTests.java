package com.slotforge.api.event;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import com.slotforge.api.availability.BookingSlotRepository;
import com.slotforge.api.security.JwtService;
import com.slotforge.api.session.EventSessionRepository;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.RoleRepository;
import com.slotforge.api.user.UserAccountRepository;
import com.slotforge.api.venue.VenueRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class EventApiIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingSlotRepository bookingSlotRepository;

    @Autowired
    private EventSessionRepository eventSessionRepository;

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
    void createEventPersistsDraftEvent() throws Exception {
        HttpResponse<String> response = sendJson(
                "POST",
                "/api/v1/events",
                """
                {
                  "name": "Stockholm Summer Concert",
                  "description": "An outdoor evening concert"
                }
                """
        );

        assertEquals(201, response.statusCode());

        List<Event> events = eventRepository.findAll();
        assertEquals(1, events.size());

        Event savedEvent = events.getFirst();

        assertEquals(
                "Stockholm Summer Concert",
                savedEvent.getName()
        );
        assertEquals(
                "An outdoor evening concert",
                savedEvent.getDescription()
        );
        assertEquals(EventStatus.DRAFT, savedEvent.getStatus());
        assertEquals(
                organizerIdentity.user().getId(),
                savedEvent.getOrganizer().getId()
        );
        assertEquals(0, savedEvent.getVersion());

        assertEquals(
                "/api/v1/events/" + savedEvent.getId(),
                response.headers()
                        .firstValue("Location")
                        .orElseThrow()
        );

        assertTrue(
                response.body().contains(
                        "\"id\":\"" + savedEvent.getId() + "\""
                )
        );
        assertTrue(
                response.body().contains("\"status\":\"DRAFT\"")
        );
        assertTrue(response.body().contains(
                "\"organizerId\":\""
                        + organizerIdentity.user().getId()
                        + "\""
        ));
    }

    @Test
    void anotherOrganizerCannotUpdateEvent() throws Exception {
        Event event = eventRepository.saveAndFlush(
                new Event("Owned Event", null, organizerIdentity.user())
        );
        TestIdentity otherOrganizer = SecurityTestTokenFactory.createIdentity(
                RoleName.ORGANIZER,
                userAccountRepository,
                roleRepository,
                jwtService
        );

        HttpResponse<String> response = sendJson(
                "PATCH",
                "/api/v1/events/" + event.getId(),
                """
                { "name": "Unauthorized change" }
                """,
                otherOrganizer.accessToken()
        );

        assertEquals(403, response.statusCode());
        assertEquals(
                "Owned Event",
                eventRepository.findById(event.getId()).orElseThrow().getName()
        );
    }

    @Test
    void adminCanUpdateEventWithoutTakingOwnership() throws Exception {
        Event event = eventRepository.saveAndFlush(
                new Event("Owned Event", null, organizerIdentity.user())
        );
        TestIdentity admin = SecurityTestTokenFactory.createIdentity(
                RoleName.ADMIN,
                userAccountRepository,
                roleRepository,
                jwtService
        );

        HttpResponse<String> response = sendJson(
                "PATCH",
                "/api/v1/events/" + event.getId(),
                """
                { "name": "Admin change" }
                """,
                admin.accessToken()
        );

        assertEquals(200, response.statusCode());
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals("Admin change", updated.getName());
        assertEquals(
                organizerIdentity.user().getId(),
                updated.getOrganizer().getId()
        );
    }

    @Test
    void getEventReturnsPersistedEvent() throws Exception {
        Event savedEvent = eventRepository.saveAndFlush(
                new Event(
                        "Retrieved Event",
                        "Retrieved through the API",
                        organizerIdentity.user()
                )
        );

        HttpResponse<String> response = sendGet(
                "/api/v1/events/" + savedEvent.getId()
        );

        assertEquals(200, response.statusCode());
        assertTrue(
                response.body().contains(
                        "\"id\":\"" + savedEvent.getId() + "\""
                )
        );
        assertTrue(
                response.body().contains(
                        "\"name\":\"Retrieved Event\""
                )
        );
    }

    @Test
    void listEventsSupportsPaginationAndStatusFiltering()
            throws Exception {

        Event draftEvent = eventRepository.saveAndFlush(
                new Event(
                        "Draft Event",
                        null,
                        organizerIdentity.user()
                )
        );

        Event publishedEvent = new Event(
                "Published Event",
                null,
                organizerIdentity.user()
        );
        publishedEvent.changeStatus(EventStatus.PUBLISHED);
        publishedEvent = eventRepository.saveAndFlush(publishedEvent);

        HttpResponse<String> draftResponse = sendGet(
                "/api/v1/events?page=0&size=1&status=DRAFT"
        );

        assertEquals(200, draftResponse.statusCode());
        assertTrue(
                draftResponse.body().contains(
                        "\"id\":\"" + draftEvent.getId() + "\""
                )
        );
        assertFalse(
                draftResponse.body().contains(
                        "\"id\":\"" + publishedEvent.getId() + "\""
                )
        );
        assertTrue(
                draftResponse.body().contains("\"page\":0")
        );
        assertTrue(
                draftResponse.body().contains("\"size\":1")
        );
        assertTrue(
                draftResponse.body().contains(
                        "\"totalElements\":1"
                )
        );
    }

    @Test
    void patchEventUpdatesOnlyProvidedFieldsAndVersion()
            throws Exception {

        Event savedEvent = eventRepository.saveAndFlush(
                new Event(
                        "Original Name",
                        "Original description",
                        organizerIdentity.user()
                )
        );

        assertEquals(0, savedEvent.getVersion());

        HttpResponse<String> response = sendJson(
                "PATCH",
                "/api/v1/events/" + savedEvent.getId(),
                """
                {
                  "status": "PUBLISHED"
                }
                """
        );

        assertEquals(200, response.statusCode());

        Event updatedEvent = eventRepository
                .findById(savedEvent.getId())
                .orElseThrow();

        assertEquals("Original Name", updatedEvent.getName());
        assertEquals(
                "Original description",
                updatedEvent.getDescription()
        );
        assertEquals(
                EventStatus.PUBLISHED,
                updatedEvent.getStatus()
        );
        assertEquals(1, updatedEvent.getVersion());

        assertTrue(
                response.body().contains(
                        "\"status\":\"PUBLISHED\""
                )
        );
        assertTrue(
                response.body().contains("\"version\":1")
        );
    }

    @Test
    void invalidCreateRequestReturnsStandardValidationError()
            throws Exception {

        HttpResponse<String> response = sendJson(
                "POST",
                "/api/v1/events",
                """
                {
                  "name": "   "
                }
                """
        );

        assertEquals(400, response.statusCode());
        assertTrue(
                response.body().contains("\"status\":400")
        );
        assertTrue(
                response.body().contains(
                        "\"error\":\"Bad Request\""
                )
        );
        assertTrue(
                response.body().contains(
                        "\"message\":\"Request validation failed\""
                )
        );
        assertTrue(
                response.body().contains("\"field\":\"name\"")
        );
        assertTrue(
                response.body().contains(
                        "\"path\":\"/api/v1/events\""
                )
        );

        assertEquals(0, eventRepository.count());
    }

    @Test
    void unknownEventReturnsNotFoundError() throws Exception {
        UUID missingId = UUID.fromString(
                "00000000-0000-0000-0000-000000000000"
        );

        HttpResponse<String> response = sendGet(
                "/api/v1/events/" + missingId
        );

        assertEquals(404, response.statusCode());
        assertTrue(
                response.body().contains("\"status\":404")
        );
        assertTrue(
                response.body().contains(
                        "\"message\":\"Event not found: "
                                + missingId
                                + "\""
                )
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
        return URI.create(
                "http://localhost:" + port + path
        );
    }
}
