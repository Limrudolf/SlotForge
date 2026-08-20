package com.slotforge.api.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotforge.api.SecurityTestTokenFactory;
import com.slotforge.api.SecurityTestTokenFactory.TestIdentity;
import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.availability.BookingSlot;
import com.slotforge.api.availability.BookingSlotRepository;
import com.slotforge.api.event.Event;
import com.slotforge.api.event.EventRepository;
import com.slotforge.api.security.JwtService;
import com.slotforge.api.session.EventSession;
import com.slotforge.api.session.EventSessionRepository;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.RoleRepository;
import com.slotforge.api.user.UserAccountRepository;
import com.slotforge.api.venue.Venue;
import com.slotforge.api.venue.VenueRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class BookingQueryIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @LocalServerPort
    private int port;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private BookingStateTransitionRepository transitionRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

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

    @AfterEach
    void cleanFixtures() {
        transitionRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        bookingItemRepository.deleteAll();
        bookingRepository.deleteAll();
        bookingSlotRepository.deleteAll();
        eventSessionRepository.deleteAll();
        eventRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void ownerAndAdminCanGetBookingButAnotherCustomerCannot()
            throws Exception {
        EventSession session = createSession(2);
        TestIdentity owner = createIdentity(RoleName.CUSTOMER);
        TestIdentity other = createIdentity(RoleName.CUSTOMER);
        TestIdentity admin = createIdentity(RoleName.ADMIN);
        UUID bookingId = createBooking(session, owner, 1);

        HttpResponse<String> ownerResponse = get(
                "/api/v1/bookings/" + bookingId,
                owner.accessToken()
        );
        HttpResponse<String> otherResponse = get(
                "/api/v1/bookings/" + bookingId,
                other.accessToken()
        );
        HttpResponse<String> adminResponse = get(
                "/api/v1/bookings/" + bookingId,
                admin.accessToken()
        );

        assertEquals(200, ownerResponse.statusCode());
        assertEquals(403, otherResponse.statusCode());
        assertEquals(200, adminResponse.statusCode());

        JsonNode body = objectMapper.readTree(ownerResponse.body());
        assertEquals(bookingId.toString(), body.get("id").asText());
        assertEquals(owner.user().getId().toString(), body.get("userId").asText());
        assertEquals(session.getId().toString(), body.get("sessionId").asText());
        assertEquals("PENDING_PAYMENT", body.get("status").asText());
        assertEquals(1, body.get("quantity").asInt());
    }

    @Test
    void currentUserListingIsIsolatedAndPaginated() throws Exception {
        EventSession session = createSession(4);
        TestIdentity firstCustomer = createIdentity(RoleName.CUSTOMER);
        TestIdentity secondCustomer = createIdentity(RoleName.CUSTOMER);
        UUID firstId = createBooking(session, firstCustomer, 1);
        UUID secondId = createBooking(session, firstCustomer, 1);
        createBooking(session, secondCustomer, 1);

        HttpResponse<String> response = get(
                "/api/v1/me/bookings?page=0&size=1",
                firstCustomer.accessToken()
        );

        assertEquals(200, response.statusCode());
        JsonNode page = objectMapper.readTree(response.body());
        assertEquals(2, page.get("totalElements").asLong());
        assertEquals(2, page.get("totalPages").asInt());
        assertEquals(1, page.get("content").size());
        assertTrue(page.get("first").asBoolean());
        assertTrue(!page.get("last").asBoolean());

        HttpResponse<String> secondPageResponse = get(
                "/api/v1/me/bookings?page=1&size=1",
                firstCustomer.accessToken()
        );
        JsonNode secondPage = objectMapper.readTree(secondPageResponse.body());
        assertEquals(200, secondPageResponse.statusCode());
        assertEquals(1, secondPage.get("content").size());
        assertTrue(secondPage.get("last").asBoolean());

        Set<String> returnedIds = new HashSet<>();
        returnedIds.add(page.get("content").get(0).get("id").asText());
        returnedIds.add(secondPage.get("content").get(0).get("id").asText());
        assertEquals(
                Set.of(firstId.toString(), secondId.toString()),
                returnedIds
        );
    }

    @Test
    void transitionHistoryIsVisibleToOwnerAndAdminOnly() throws Exception {
        EventSession session = createSession(1);
        TestIdentity owner = createIdentity(RoleName.CUSTOMER);
        TestIdentity other = createIdentity(RoleName.CUSTOMER);
        TestIdentity admin = createIdentity(RoleName.ADMIN);
        UUID bookingId = createBooking(session, owner, 1);
        String path = "/api/v1/bookings/" + bookingId
                + "/state-transitions";

        HttpResponse<String> ownerResponse = get(path, owner.accessToken());
        HttpResponse<String> otherResponse = get(path, other.accessToken());
        HttpResponse<String> adminResponse = get(path, admin.accessToken());

        assertEquals(200, ownerResponse.statusCode());
        assertEquals(403, otherResponse.statusCode());
        assertEquals(200, adminResponse.statusCode());

        JsonNode history = objectMapper.readTree(ownerResponse.body());
        assertEquals(1, history.size());
        assertTrue(history.get(0).get("fromState").isNull());
        assertEquals(
                "PENDING_PAYMENT",
                history.get(0).get("toState").asText()
        );
        assertEquals(
                owner.user().getId().toString(),
                history.get(0).get("changedByUserId").asText()
        );
        assertEquals("Booking created", history.get(0).get("reason").asText());
    }

    @Test
    void missingBookingReturnsNotFound() throws Exception {
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);
        HttpResponse<String> response = get(
                "/api/v1/bookings/" + UUID.randomUUID(),
                customer.accessToken()
        );
        assertEquals(404, response.statusCode());
    }

    @Test
    void invalidPaginationReturnsBadRequest() throws Exception {
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);
        assertEquals(
                400,
                get(
                        "/api/v1/me/bookings?page=-1&size=20",
                        customer.accessToken()
                ).statusCode()
        );
        assertEquals(
                400,
                get(
                        "/api/v1/me/bookings?page=0&size=101",
                        customer.accessToken()
                ).statusCode()
        );
    }

    private UUID createBooking(
            EventSession session,
            TestIdentity customer,
            int quantity
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri(
                        "/api/v1/sessions/" + session.getId() + "/bookings"
                ))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + customer.accessToken())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"quantity\":" + quantity + "}"
                ))
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(201, response.statusCode());

        String location = response.headers()
                .firstValue("Location")
                .orElseThrow();
        return UUID.fromString(
                location.substring(location.lastIndexOf('/') + 1)
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

    private EventSession createSession(int capacity) {
        TestIdentity organizer = createIdentity(RoleName.ORGANIZER);
        Event event = eventRepository.saveAndFlush(
                new Event("Query Test Event", null, organizer.user())
        );
        Venue venue = venueRepository.saveAndFlush(
                new Venue(
                        "Query Test Venue",
                        "1 Test Street",
                        null,
                        "Stockholm",
                        null,
                        "111 11",
                        "SE"
                )
        );
        EventSession session = eventSessionRepository.saveAndFlush(
                new EventSession(
                        event,
                        venue,
                        Instant.parse("2026-10-10T17:00:00Z"),
                        Instant.parse("2026-10-10T20:00:00Z"),
                        "Europe/Stockholm",
                        10_000L,
                        java.util.Currency.getInstance("SEK")
                )
        );
        bookingSlotRepository.saveAndFlush(new BookingSlot(session, capacity));
        return session;
    }

    private TestIdentity createIdentity(RoleName roleName) {
        return SecurityTestTokenFactory.createIdentity(
                roleName,
                userAccountRepository,
                roleRepository,
                jwtService
        );
    }
}
