package com.slotforge.api.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

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
class BookingCancellationIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

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
    void ownerCancelsBookingAndRestoresCapacityOnce() throws Exception {
        EventSession session = createSession(2);
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);
        UUID bookingId = createBooking(session, customer);

        HttpResponse<String> first = cancel(bookingId, customer.accessToken());
        HttpResponse<String> repeated = cancel(
                bookingId,
                customer.accessToken()
        );

        assertEquals(200, first.statusCode());
        assertEquals(409, repeated.statusCode());
        assertEquals(
                BookingStatus.CANCELLED,
                bookingRepository.findById(bookingId).orElseThrow().getStatus()
        );
        assertEquals(2, transitions(bookingId).size());
        assertEquals(
                BookingStatus.CANCELLED,
                transitions(bookingId).getLast().getToState()
        );
        assertEquals(2, remainingCapacity(session));
    }

    @Test
    void anotherCustomerCannotCancelBooking() throws Exception {
        EventSession session = createSession(1);
        TestIdentity owner = createIdentity(RoleName.CUSTOMER);
        TestIdentity other = createIdentity(RoleName.CUSTOMER);
        UUID bookingId = createBooking(session, owner);

        HttpResponse<String> response = cancel(
                bookingId,
                other.accessToken()
        );

        assertEquals(403, response.statusCode());
        assertEquals(
                BookingStatus.PENDING_PAYMENT,
                bookingRepository.findById(bookingId).orElseThrow().getStatus()
        );
        assertEquals(0, remainingCapacity(session));
        assertEquals(1, transitions(bookingId).size());
    }

    @Test
    void simultaneousDuplicateCancellationRestoresCapacityOnce()
            throws Exception {
        EventSession session = createSession(1);
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);
        UUID bookingId = createBooking(session, customer);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpResponse<String>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return cancel(bookingId, customer.accessToken());
            });
            Future<HttpResponse<String>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return cancel(bookingId, customer.accessToken());
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS).statusCode(),
                    second.get(10, TimeUnit.SECONDS).statusCode()
            ).stream().sorted().toList();
            assertEquals(List.of(200, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, remainingCapacity(session));
        assertEquals(2, transitions(bookingId).size());
    }

    @Test
    void independentConcurrentCancellationsBothRestoreSharedCapacity()
            throws Exception {
        EventSession session = createSession(2);
        TestIdentity firstCustomer = createIdentity(RoleName.CUSTOMER);
        TestIdentity secondCustomer = createIdentity(RoleName.CUSTOMER);
        UUID firstBooking = createBooking(session, firstCustomer);
        UUID secondBooking = createBooking(session, secondCustomer);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpResponse<String>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return cancel(firstBooking, firstCustomer.accessToken());
            });
            Future<HttpResponse<String>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return cancel(secondBooking, secondCustomer.accessToken());
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(200, first.get(10, TimeUnit.SECONDS).statusCode());
            assertEquals(200, second.get(10, TimeUnit.SECONDS).statusCode());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, remainingCapacity(session));
        assertEquals(2, transitions(firstBooking).size());
        assertEquals(2, transitions(secondBooking).size());
    }

    private UUID createBooking(
            EventSession session,
            TestIdentity customer
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri(
                        "/api/v1/sessions/" + session.getId() + "/bookings"
                ))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + customer.accessToken())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"quantity\":1}"
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

    private HttpResponse<String> cancel(UUID bookingId, String accessToken)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/bookings/" + bookingId + "/cancel"))
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private int remainingCapacity(EventSession session) {
        return bookingSlotRepository
                .findByEventSession_Id(session.getId())
                .orElseThrow()
                .getRemainingCapacity();
    }

    private List<BookingStateTransition> transitions(UUID bookingId) {
        return transitionRepository
                .findAllByBooking_IdOrderByOccurredAtAscIdAsc(bookingId);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private EventSession createSession(int capacity) {
        TestIdentity organizer = createIdentity(RoleName.ORGANIZER);
        Event event = eventRepository.saveAndFlush(
                new Event("Cancellation Test Event", null, organizer.user())
        );
        Venue venue = venueRepository.saveAndFlush(
                new Venue(
                        "Cancellation Test Venue",
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
