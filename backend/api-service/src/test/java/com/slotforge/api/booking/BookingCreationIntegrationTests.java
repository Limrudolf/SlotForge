package com.slotforge.api.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
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
class BookingCreationIntegrationTests {

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
    void cleanBookingFixtures() {
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
    void customerCreatesPendingBookingAndConsumesCapacity() throws Exception {
        BookingFixture fixture = createFixture(3);
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);
        Instant requestStartedAt = Instant.now();

        HttpResponse<String> response = sendBookingRequest(
                fixture.session().getId(),
                customer.accessToken(),
                UUID.randomUUID().toString(),
                2
        );

        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("Location").isPresent());

        List<Booking> bookings = bookingRepository.findAllByUser_Id(
                customer.user().getId()
        );
        assertEquals(1, bookings.size());
        Booking booking = bookings.getFirst();
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
        assertEquals(customer.user().getId(), booking.getUser().getId());
        assertNotNull(booking.getPaymentExpiresAt());
        assertFalse(booking.getPaymentExpiresAt().isBefore(
                requestStartedAt.plusSeconds(14 * 60)
        ));
        assertFalse(booking.getPaymentExpiresAt().isAfter(
                Instant.now().plusSeconds(16 * 60)
        ));

        BookingItem item = bookingItemRepository
                .findByBooking_Id(booking.getId())
                .orElseThrow();
        assertEquals(2, item.getQuantity());

        List<BookingStateTransition> transitions = transitionRepository
                .findAllByBooking_IdOrderByOccurredAtAscIdAsc(booking.getId());
        assertEquals(1, transitions.size());
        assertNull(transitions.getFirst().getFromState());
        assertEquals(
                BookingStatus.PENDING_PAYMENT,
                transitions.getFirst().getToState()
        );

        BookingSlot updatedSlot = bookingSlotRepository
                .findByEventSession_Id(fixture.session().getId())
                .orElseThrow();
        assertEquals(1, updatedSlot.getRemainingCapacity());
    }

    @Test
    void insufficientCapacityReturnsConflictWithoutCreatingBooking()
            throws Exception {
        BookingFixture fixture = createFixture(1);
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);
        long bookingsBefore = bookingRepository.count();

        HttpResponse<String> response = sendBookingRequest(
                fixture.session().getId(),
                customer.accessToken(),
                UUID.randomUUID().toString(),
                2
        );

        assertEquals(409, response.statusCode());
        assertEquals(bookingsBefore, bookingRepository.count());
        assertEquals(
                1,
                bookingSlotRepository
                        .findByEventSession_Id(fixture.session().getId())
                        .orElseThrow()
                        .getRemainingCapacity()
        );
    }

    @Test
    void concurrentCustomersCannotBothBookTheLastSlot() throws Exception {
        BookingFixture fixture = createFixture(1);
        TestIdentity firstCustomer = createIdentity(RoleName.CUSTOMER);
        TestIdentity secondCustomer = createIdentity(RoleName.CUSTOMER);
        long bookingsBefore = bookingRepository.count();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpResponse<String>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return sendBookingRequest(
                        fixture.session().getId(),
                        firstCustomer.accessToken(),
                        UUID.randomUUID().toString(),
                        1
                );
            });
            Future<HttpResponse<String>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return sendBookingRequest(
                        fixture.session().getId(),
                        secondCustomer.accessToken(),
                        UUID.randomUUID().toString(),
                        1
                );
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS).statusCode(),
                    second.get(10, TimeUnit.SECONDS).statusCode()
            ).stream().sorted().toList();

            assertEquals(List.of(201, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(bookingsBefore + 1, bookingRepository.count());
        assertEquals(
                0,
                bookingSlotRepository
                        .findByEventSession_Id(fixture.session().getId())
                        .orElseThrow()
                        .getRemainingCapacity()
        );
    }

    @Test
    void identicalReplayReturnsOriginalBookingWithoutConsumingCapacityAgain()
            throws Exception {
        BookingFixture fixture = createFixture(2);
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);
        String key = UUID.randomUUID().toString();

        HttpResponse<String> first = sendBookingRequest(
                fixture.session().getId(),
                customer.accessToken(),
                key,
                1
        );
        HttpResponse<String> replay = sendBookingRequest(
                fixture.session().getId(),
                customer.accessToken(),
                key,
                1
        );

        assertEquals(201, first.statusCode());
        assertEquals(200, replay.statusCode());
        assertEquals(
                first.headers().firstValue("Location").orElseThrow(),
                replay.headers().firstValue("Location").orElseThrow()
        );
        assertEquals(
                1,
                bookingRepository.findAllByUser_Id(customer.user().getId())
                        .size()
        );
        assertEquals(1, idempotencyKeyRepository.count());
        assertEquals(
                1,
                bookingSlotRepository
                        .findByEventSession_Id(fixture.session().getId())
                        .orElseThrow()
                        .getRemainingCapacity()
        );
    }

    @Test
    void reusedKeyWithDifferentPayloadReturnsConflict() throws Exception {
        BookingFixture fixture = createFixture(3);
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);
        String key = UUID.randomUUID().toString();

        HttpResponse<String> first = sendBookingRequest(
                fixture.session().getId(),
                customer.accessToken(),
                key,
                1
        );
        HttpResponse<String> conflicting = sendBookingRequest(
                fixture.session().getId(),
                customer.accessToken(),
                key,
                2
        );

        assertEquals(201, first.statusCode());
        assertEquals(409, conflicting.statusCode());
        assertEquals(
                1,
                bookingRepository.findAllByUser_Id(customer.user().getId())
                        .size()
        );
        assertEquals(
                2,
                bookingSlotRepository
                        .findByEventSession_Id(fixture.session().getId())
                        .orElseThrow()
                        .getRemainingCapacity()
        );
    }

    @Test
    void differentUsersMayUseTheSameIdempotencyKey() throws Exception {
        BookingFixture fixture = createFixture(2);
        TestIdentity firstCustomer = createIdentity(RoleName.CUSTOMER);
        TestIdentity secondCustomer = createIdentity(RoleName.CUSTOMER);
        String key = UUID.randomUUID().toString();

        HttpResponse<String> first = sendBookingRequest(
                fixture.session().getId(),
                firstCustomer.accessToken(),
                key,
                1
        );
        HttpResponse<String> second = sendBookingRequest(
                fixture.session().getId(),
                secondCustomer.accessToken(),
                key,
                1
        );

        assertEquals(201, first.statusCode());
        assertEquals(201, second.statusCode());
        assertEquals(2, idempotencyKeyRepository.count());
        assertEquals(
                0,
                bookingSlotRepository
                        .findByEventSession_Id(fixture.session().getId())
                        .orElseThrow()
                        .getRemainingCapacity()
        );
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        BookingFixture fixture = createFixture(1);
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port
                                + "/api/v1/sessions/"
                                + fixture.session().getId()
                                + "/bookings"
                ))
                .header("Content-Type", "application/json")
                .header(
                        "Authorization",
                        "Bearer " + customer.accessToken()
                )
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"quantity\":1}"
                ))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(400, response.statusCode());
        assertEquals(0, idempotencyKeyRepository.count());
        assertTrue(
                bookingRepository.findAllByUser_Id(customer.user().getId())
                        .isEmpty()
        );
    }

    @Test
    void simultaneousIdenticalRequestsCreateOneBooking() throws Exception {
        BookingFixture fixture = createFixture(2);
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);
        String key = UUID.randomUUID().toString();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpResponse<String>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return sendBookingRequest(
                        fixture.session().getId(),
                        customer.accessToken(),
                        key,
                        1
                );
            });
            Future<HttpResponse<String>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return sendBookingRequest(
                        fixture.session().getId(),
                        customer.accessToken(),
                        key,
                        1
                );
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            HttpResponse<String> firstResponse = first.get(
                    10,
                    TimeUnit.SECONDS
            );
            HttpResponse<String> secondResponse = second.get(
                    10,
                    TimeUnit.SECONDS
            );
            List<Integer> statuses = List.of(
                    firstResponse.statusCode(),
                    secondResponse.statusCode()
            ).stream().sorted().toList();

            assertEquals(List.of(200, 201), statuses);
            assertEquals(
                    firstResponse.headers()
                            .firstValue("Location").orElseThrow(),
                    secondResponse.headers()
                            .firstValue("Location").orElseThrow()
            );
        } finally {
            executor.shutdownNow();
        }

        List<Booking> bookings = bookingRepository.findAllByUser_Id(
                customer.user().getId()
        );
        assertEquals(1, bookings.size());
        assertEquals(1, idempotencyKeyRepository.count());
        assertEquals(
                1,
                transitionRepository
                        .findAllByBooking_IdOrderByOccurredAtAscIdAsc(
                                bookings.getFirst().getId()
                        )
                        .size()
        );
        assertEquals(
                1,
                bookingSlotRepository
                        .findByEventSession_Id(fixture.session().getId())
                        .orElseThrow()
                        .getRemainingCapacity()
        );
    }

    @Test
    void simultaneousConflictingRequestsOnDifferentSessionsCommitOneWinner()
            throws Exception {
        BookingFixture firstFixture = createFixture(1);
        BookingFixture secondFixture = createFixture(1);
        TestIdentity customer = createIdentity(RoleName.CUSTOMER);
        String key = UUID.randomUUID().toString();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpResponse<String>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return sendBookingRequest(
                        firstFixture.session().getId(),
                        customer.accessToken(),
                        key,
                        1
                );
            });
            Future<HttpResponse<String>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return sendBookingRequest(
                        secondFixture.session().getId(),
                        customer.accessToken(),
                        key,
                        1
                );
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS).statusCode(),
                    second.get(10, TimeUnit.SECONDS).statusCode()
            ).stream().sorted().toList();

            assertEquals(List.of(201, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(
                1,
                bookingRepository.findAllByUser_Id(customer.user().getId())
                        .size()
        );
        assertEquals(1, idempotencyKeyRepository.count());

        int totalRemaining = bookingSlotRepository
                .findByEventSession_Id(firstFixture.session().getId())
                .orElseThrow()
                .getRemainingCapacity()
                + bookingSlotRepository
                        .findByEventSession_Id(secondFixture.session().getId())
                        .orElseThrow()
                        .getRemainingCapacity();
        assertEquals(1, totalRemaining);
    }

    private HttpResponse<String> sendBookingRequest(
            UUID sessionId,
            String accessToken,
            String idempotencyKey,
            int quantity
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port
                                + "/api/v1/sessions/" + sessionId
                                + "/bookings"
                ))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"quantity\":" + quantity + "}"
                ))
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private BookingFixture createFixture(int capacity) {
        TestIdentity organizer = createIdentity(RoleName.ORGANIZER);
        Event event = eventRepository.saveAndFlush(
                new Event("Booking Test Event", null, organizer.user())
        );
        Venue venue = venueRepository.saveAndFlush(
                new Venue(
                        "Booking Test Venue",
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
        BookingSlot slot = bookingSlotRepository.saveAndFlush(
                new BookingSlot(session, capacity)
        );
        return new BookingFixture(session, slot);
    }

    private TestIdentity createIdentity(RoleName roleName) {
        return SecurityTestTokenFactory.createIdentity(
                roleName,
                userAccountRepository,
                roleRepository,
                jwtService
        );
    }

    private record BookingFixture(
            EventSession session,
            BookingSlot slot
    ) {
    }
}
