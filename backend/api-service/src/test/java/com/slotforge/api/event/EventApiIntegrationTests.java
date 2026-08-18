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
import com.slotforge.api.availability.BookingSlotRepository;
import com.slotforge.api.session.EventSessionRepository;
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

    @BeforeEach
    void clearDatabase() {
        bookingSlotRepository.deleteAll();
        eventSessionRepository.deleteAll();
        eventRepository.deleteAll();
        venueRepository.deleteAll();
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
    }

    @Test
    void getEventReturnsPersistedEvent() throws Exception {
        Event savedEvent = eventRepository.saveAndFlush(
                new Event(
                        "Retrieved Event",
                        "Retrieved through the API"
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
                new Event("Draft Event", null)
        );

        Event publishedEvent = new Event(
                "Published Event",
                null
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
                        "Original description"
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri(path))
                .header("Content-Type", "application/json")
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
