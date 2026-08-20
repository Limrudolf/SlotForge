package com.slotforge.api.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Currency;
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
import com.slotforge.api.booking.Booking;
import com.slotforge.api.booking.BookingItem;
import com.slotforge.api.booking.BookingItemRepository;
import com.slotforge.api.booking.BookingRepository;
import com.slotforge.api.booking.BookingStateTransition;
import com.slotforge.api.booking.BookingStateTransitionRepository;
import com.slotforge.api.booking.BookingStatus;
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
class PaymentIntentApiIntegrationTests {

    private static final Currency SEK = Currency.getInstance("SEK");
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired private PaymentEventRepository paymentEventRepository;
    @Autowired private PaymentIntentRepository paymentIntentRepository;
    @Autowired private BookingExpiryProcessor expiryProcessor;
    @Autowired private BookingItemRepository bookingItemRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingStateTransitionRepository transitionRepository;
    @Autowired private BookingSlotRepository bookingSlotRepository;
    @Autowired private EventSessionRepository eventSessionRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JwtService jwtService;

    @AfterEach
    void cleanFixtures() {
        paymentEventRepository.deleteAll();
        paymentIntentRepository.deleteAll();
        transitionRepository.deleteAll();
        bookingItemRepository.deleteAll();
        bookingRepository.deleteAll();
        bookingSlotRepository.deleteAll();
        eventSessionRepository.deleteAll();
        eventRepository.deleteAll();
        venueRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void ownerCreatesIntentFromBookingPriceSnapshot() throws Exception {
        Fixture fixture = createFixture(2, Instant.now().plusSeconds(900));

        HttpResponse<String> response = postIntent(
                fixture.booking().getId(),
                fixture.owner().accessToken()
        );

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("\"amountMinor\":20000"));
        assertTrue(response.body().contains("\"currency\":\"SEK\""));
        assertTrue(response.body().contains("\"status\":\"PENDING\""));
        assertEquals(1, paymentIntentRepository.count());

        PaymentIntent persisted = paymentIntentRepository
                .findByBooking_Id(fixture.booking().getId())
                .orElseThrow();
        assertEquals(20_000L, persisted.getAmountMinor());
        assertEquals(SEK, persisted.getCurrency());
    }

    @Test
    void repeatedCreationReturnsOriginalIntent() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));

        HttpResponse<String> first = postIntent(
                fixture.booking().getId(),
                fixture.owner().accessToken()
        );
        HttpResponse<String> replay = postIntent(
                fixture.booking().getId(),
                fixture.owner().accessToken()
        );

        assertEquals(201, first.statusCode());
        assertEquals(200, replay.statusCode());
        assertEquals(
                first.headers().firstValue("Location").orElseThrow(),
                replay.headers().firstValue("Location").orElseThrow()
        );
        assertEquals(1, paymentIntentRepository.count());
    }

    @Test
    void concurrentCreationProducesOneIntent() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpResponse<String>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postIntent(
                        fixture.booking().getId(),
                        fixture.owner().accessToken()
                );
            });
            Future<HttpResponse<String>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postIntent(
                        fixture.booking().getId(),
                        fixture.owner().accessToken()
                );
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            HttpResponse<String> firstResponse =
                    first.get(10, TimeUnit.SECONDS);
            HttpResponse<String> secondResponse =
                    second.get(10, TimeUnit.SECONDS);

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

        assertEquals(1, paymentIntentRepository.count());
    }

    @Test
    void expiredBookingCannotCreateIntent() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().minusSeconds(1));

        HttpResponse<String> response = postIntent(
                fixture.booking().getId(),
                fixture.owner().accessToken()
        );

        assertEquals(409, response.statusCode());
        assertEquals(0, paymentIntentRepository.count());
    }

    @Test
    void cancelledBookingCannotCreateIntent() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        fixture.booking().cancel();
        bookingRepository.saveAndFlush(fixture.booking());

        HttpResponse<String> response = postIntent(
                fixture.booking().getId(),
                fixture.owner().accessToken()
        );

        assertEquals(409, response.statusCode());
        assertEquals(0, paymentIntentRepository.count());
    }

    @Test
    void anotherCustomerCannotCreateOrReadIntent() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        TestIdentity otherCustomer = identity(RoleName.CUSTOMER);
        HttpResponse<String> created = postIntent(
                fixture.booking().getId(),
                fixture.owner().accessToken()
        );
        String location = created.headers()
                .firstValue("Location").orElseThrow();

        assertEquals(
                403,
                postIntent(
                        fixture.booking().getId(),
                        otherCustomer.accessToken()
                ).statusCode()
        );
        assertEquals(
                403,
                get(location, otherCustomer.accessToken()).statusCode()
        );
    }

    @Test
    void ownerAndAdminCanReadIntent() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        HttpResponse<String> created = postIntent(
                fixture.booking().getId(),
                fixture.owner().accessToken()
        );
        String location = created.headers()
                .firstValue("Location").orElseThrow();

        assertEquals(
                200,
                get(location, fixture.owner().accessToken()).statusCode()
        );
        assertEquals(200, get(location, admin.accessToken()).statusCode());
    }

    @Test
    void adminAuthorizationConfirmsBookingAndPreservesCapacity()
            throws Exception {
        Fixture fixture = createFixture(2, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        String location = postIntent(
                fixture.booking().getId(),
                fixture.owner().accessToken()
        ).headers().firstValue("Location").orElseThrow();
        UUID paymentIntentId = idFromLocation(location);
        int capacityBefore = fixture.slot().getRemainingCapacity();

        HttpResponse<String> response = authorize(
                paymentIntentId,
                admin.accessToken(),
                "evt-authorized"
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(
                "\"paymentStatus\":\"AUTHORIZED\""
        ));
        assertTrue(response.body().contains(
                "\"bookingStatus\":\"CONFIRMED\""
        ));
        assertTrue(response.body().contains("\"replayed\":false"));
        assertEquals(
                PaymentIntentStatus.AUTHORIZED,
                paymentIntentRepository.findById(paymentIntentId)
                        .orElseThrow().getStatus()
        );
        assertEquals(
                BookingStatus.CONFIRMED,
                bookingRepository.findById(fixture.booking().getId())
                        .orElseThrow().getStatus()
        );
        assertEquals(1, paymentEventRepository.count());

        List<BookingStateTransition> transitions = transitionRepository
                .findAllByBooking_IdOrderByOccurredAtAscIdAsc(
                        fixture.booking().getId()
                );
        assertEquals(2, transitions.size());
        assertEquals(
                BookingStatus.PAYMENT_AUTHORIZED,
                transitions.get(0).getToState()
        );
        assertEquals(
                BookingStatus.CONFIRMED,
                transitions.get(1).getToState()
        );
        assertEquals(
                capacityBefore,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
    }

    @Test
    void exactAuthorizationReplayDoesNotRepeatEffects() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);

        HttpResponse<String> first = authorize(
                intentId,
                admin.accessToken(),
                "evt-replay"
        );
        HttpResponse<String> replay = authorize(
                intentId,
                admin.accessToken(),
                "evt-replay"
        );

        assertEquals(200, first.statusCode());
        assertEquals(200, replay.statusCode());
        assertTrue(first.body().contains("\"replayed\":false"));
        assertTrue(replay.body().contains("\"replayed\":true"));
        assertEquals(1, paymentEventRepository.count());
        assertEquals(
                2,
                transitionRepository
                        .findAllByBooking_IdOrderByOccurredAtAscIdAsc(
                                fixture.booking().getId()
                        ).size()
        );
    }

    @Test
    void concurrentDuplicateAuthorizationAppliesOnce() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpResponse<String>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return authorize(
                        intentId,
                        admin.accessToken(),
                        "evt-concurrent"
                );
            });
            Future<HttpResponse<String>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return authorize(
                        intentId,
                        admin.accessToken(),
                        "evt-concurrent"
                );
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<HttpResponse<String>> responses = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            assertTrue(responses.stream().allMatch(
                    response -> response.statusCode() == 200
            ));
            assertEquals(
                    1,
                    responses.stream().filter(response -> response.body()
                            .contains("\"replayed\":true")).count()
            );
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, paymentEventRepository.count());
        assertEquals(
                2,
                transitionRepository
                        .findAllByBooking_IdOrderByOccurredAtAscIdAsc(
                                fixture.booking().getId()
                        ).size()
        );
    }

    @Test
    void secondLogicalAuthorizationIsRejected() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);
        assertEquals(
                200,
                authorize(intentId, admin.accessToken(), "evt-first")
                        .statusCode()
        );

        HttpResponse<String> response = authorize(
                intentId,
                admin.accessToken(),
                "evt-second"
        );

        assertEquals(409, response.statusCode());
        assertEquals(1, paymentEventRepository.count());
    }

    @Test
    void eventIdCannotBeReusedForAnotherPaymentIntent() throws Exception {
        Fixture firstFixture = createFixture(
                1,
                Instant.now().plusSeconds(900)
        );
        Fixture secondFixture = createFixture(
                1,
                Instant.now().plusSeconds(900)
        );
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID firstIntentId = createIntentId(firstFixture);
        UUID secondIntentId = createIntentId(secondFixture);
        assertEquals(
                200,
                authorize(
                        firstIntentId,
                        admin.accessToken(),
                        "evt-shared"
                ).statusCode()
        );

        HttpResponse<String> response = authorize(
                secondIntentId,
                admin.accessToken(),
                "evt-shared"
        );

        assertEquals(409, response.statusCode());
        assertEquals(
                PaymentIntentStatus.PENDING,
                paymentIntentRepository.findById(secondIntentId)
                        .orElseThrow().getStatus()
        );
        assertEquals(1, paymentEventRepository.count());
    }

    @Test
    void expiredHoldRejectsAuthorization() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().minusSeconds(1));
        PaymentIntent intent = paymentIntentRepository.saveAndFlush(
                new PaymentIntent(fixture.booking(), 10_000L, SEK)
        );
        TestIdentity admin = identity(RoleName.ADMIN);

        HttpResponse<String> response = authorize(
                intent.getId(),
                admin.accessToken(),
                "evt-expired"
        );

        assertEquals(409, response.statusCode());
        assertEquals(0, paymentEventRepository.count());
        assertEquals(
                PaymentIntentStatus.PENDING,
                paymentIntentRepository.findById(intent.getId())
                        .orElseThrow().getStatus()
        );
    }

    @Test
    void customerCannotOperateFakeProvider() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        UUID intentId = createIntentId(fixture);

        HttpResponse<String> response = authorize(
                intentId,
                fixture.owner().accessToken(),
                "evt-forbidden"
        );

        assertEquals(403, response.statusCode());
        assertEquals(0, paymentEventRepository.count());
    }

    @Test
    void cancelledBookingRejectsAuthorization() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);
        fixture.booking().cancel();
        bookingRepository.saveAndFlush(fixture.booking());

        HttpResponse<String> response = authorize(
                intentId,
                admin.accessToken(),
                "evt-cancelled"
        );

        assertEquals(409, response.statusCode());
        assertEquals(0, paymentEventRepository.count());
    }

    @Test
    void failedPaymentReleasesHeldCapacity() throws Exception {
        Fixture fixture = createFixture(2, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);
        assertEquals(18, fixture.slot().getRemainingCapacity());

        HttpResponse<String> response = fail(
                intentId,
                admin.accessToken(),
                "evt-failed"
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(
                "\"paymentStatus\":\"FAILED\""
        ));
        assertTrue(response.body().contains(
                "\"bookingStatus\":\"PAYMENT_FAILED\""
        ));
        assertTrue(response.body().contains("\"replayed\":false"));
        assertEquals(
                20,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
        assertEquals(1, paymentEventRepository.count());
        List<BookingStateTransition> transitions = transitionRepository
                .findAllByBooking_IdOrderByOccurredAtAscIdAsc(
                        fixture.booking().getId()
                );
        assertEquals(1, transitions.size());
        assertEquals(
                BookingStatus.PAYMENT_FAILED,
                transitions.getFirst().getToState()
        );
    }

    @Test
    void exactFailureReplayDoesNotReleaseCapacityTwice() throws Exception {
        Fixture fixture = createFixture(2, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);

        HttpResponse<String> first = fail(
                intentId,
                admin.accessToken(),
                "evt-failure-replay"
        );
        HttpResponse<String> replay = fail(
                intentId,
                admin.accessToken(),
                "evt-failure-replay"
        );

        assertEquals(200, first.statusCode());
        assertEquals(200, replay.statusCode());
        assertTrue(first.body().contains("\"replayed\":false"));
        assertTrue(replay.body().contains("\"replayed\":true"));
        assertEquals(1, paymentEventRepository.count());
        assertEquals(
                20,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
        assertEquals(
                1,
                transitionRepository
                        .findAllByBooking_IdOrderByOccurredAtAscIdAsc(
                                fixture.booking().getId()
                        ).size()
        );
    }

    @Test
    void concurrentDuplicateFailureReleasesCapacityOnce() throws Exception {
        Fixture fixture = createFixture(2, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpResponse<String>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return fail(
                        intentId,
                        admin.accessToken(),
                        "evt-concurrent-failure"
                );
            });
            Future<HttpResponse<String>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return fail(
                        intentId,
                        admin.accessToken(),
                        "evt-concurrent-failure"
                );
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<HttpResponse<String>> responses = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            assertTrue(responses.stream().allMatch(
                    response -> response.statusCode() == 200
            ));
            assertEquals(
                    1,
                    responses.stream().filter(response -> response.body()
                            .contains("\"replayed\":true")).count()
            );
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, paymentEventRepository.count());
        assertEquals(
                20,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
    }

    @Test
    void authorizationAndFailureRaceHasOneTerminalWinner()
            throws Exception {
        Fixture fixture = createFixture(2, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpResponse<String>> authorization = executor.submit(() -> {
                ready.countDown();
                start.await();
                return authorize(
                        intentId,
                        admin.accessToken(),
                        "evt-race-auth"
                );
            });
            Future<HttpResponse<String>> failure = executor.submit(() -> {
                ready.countDown();
                start.await();
                return fail(
                        intentId,
                        admin.accessToken(),
                        "evt-race-fail"
                );
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = List.of(
                    authorization.get(10, TimeUnit.SECONDS).statusCode(),
                    failure.get(10, TimeUnit.SECONDS).statusCode()
            ).stream().sorted().toList();
            assertEquals(List.of(200, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        PaymentIntent intent = paymentIntentRepository.findById(intentId)
                .orElseThrow();
        Booking booking = bookingRepository
                .findById(fixture.booking().getId()).orElseThrow();
        int remaining = bookingSlotRepository
                .findById(fixture.slot().getId()).orElseThrow()
                .getRemainingCapacity();
        assertEquals(1, paymentEventRepository.count());
        if (intent.getStatus() == PaymentIntentStatus.AUTHORIZED) {
            assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
            assertEquals(18, remaining);
        } else {
            assertEquals(PaymentIntentStatus.FAILED, intent.getStatus());
            assertEquals(BookingStatus.PAYMENT_FAILED, booking.getStatus());
            assertEquals(20, remaining);
        }
    }

    @Test
    void authorizationEventIdCannotBeReplayedAsFailure()
            throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);
        assertEquals(
                200,
                authorize(intentId, admin.accessToken(), "evt-cross-type")
                        .statusCode()
        );

        HttpResponse<String> response = fail(
                intentId,
                admin.accessToken(),
                "evt-cross-type"
        );

        assertEquals(409, response.statusCode());
        assertEquals(1, paymentEventRepository.count());
        assertEquals(
                19,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
    }

    @Test
    void capacityFailureRollsBackPaymentAndBookingChanges()
            throws Exception {
        Fixture fixture = createFixture(2, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);
        BookingSlot corruptedSlot = bookingSlotRepository
                .findById(fixture.slot().getId()).orElseThrow();
        corruptedSlot.release(2);
        bookingSlotRepository.saveAndFlush(corruptedSlot);

        HttpResponse<String> response = fail(
                intentId,
                admin.accessToken(),
                "evt-rollback"
        );

        assertEquals(500, response.statusCode());
        assertEquals(
                PaymentIntentStatus.PENDING,
                paymentIntentRepository.findById(intentId)
                        .orElseThrow().getStatus()
        );
        assertEquals(
                BookingStatus.PENDING_PAYMENT,
                bookingRepository.findById(fixture.booking().getId())
                        .orElseThrow().getStatus()
        );
        assertEquals(0, paymentEventRepository.count());
        assertTrue(
                transitionRepository
                        .findAllByBooking_IdOrderByOccurredAtAscIdAsc(
                                fixture.booking().getId()
                        ).isEmpty()
        );
        assertEquals(
                20,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
    }

    @Test
    void independentConcurrentFailuresRestoreSharedCapacity()
            throws Exception {
        Fixture first = createFixture(2, Instant.now().plusSeconds(900));
        TestIdentity secondOwner = identity(RoleName.CUSTOMER);
        BookingSlot sharedSlot = bookingSlotRepository
                .findById(first.slot().getId()).orElseThrow();
        sharedSlot.reserve(3);
        bookingSlotRepository.saveAndFlush(sharedSlot);
        Booking secondBooking = bookingRepository.saveAndFlush(
                new Booking(
                        secondOwner.user(),
                        first.booking().getEventSession(),
                        Instant.now().plusSeconds(900)
                )
        );
        bookingItemRepository.saveAndFlush(
                new BookingItem(
                        secondBooking,
                        sharedSlot,
                        3,
                        10_000L,
                        SEK
                )
        );
        Fixture second = new Fixture(secondBooking, sharedSlot, secondOwner);
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID firstIntentId = createIntentId(first);
        UUID secondIntentId = createIntentId(second);
        assertEquals(
                15,
                bookingSlotRepository.findById(sharedSlot.getId())
                        .orElseThrow().getRemainingCapacity()
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<HttpResponse<String>> firstFailure = executor.submit(() -> {
                ready.countDown();
                start.await();
                return fail(
                        firstIntentId,
                        admin.accessToken(),
                        "evt-independent-first"
                );
            });
            Future<HttpResponse<String>> secondFailure = executor.submit(() -> {
                ready.countDown();
                start.await();
                return fail(
                        secondIntentId,
                        admin.accessToken(),
                        "evt-independent-second"
                );
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(
                    200,
                    firstFailure.get(10, TimeUnit.SECONDS).statusCode()
            );
            assertEquals(
                    200,
                    secondFailure.get(10, TimeUnit.SECONDS).statusCode()
            );
        } finally {
            executor.shutdownNow();
        }

        assertEquals(
                20,
                bookingSlotRepository.findById(sharedSlot.getId())
                        .orElseThrow().getRemainingCapacity()
        );
        assertEquals(2, paymentEventRepository.count());
        assertEquals(
                BookingStatus.PAYMENT_FAILED,
                bookingRepository.findById(first.booking().getId())
                        .orElseThrow().getStatus()
        );
        assertEquals(
                BookingStatus.PAYMENT_FAILED,
                bookingRepository.findById(secondBooking.getId())
                        .orElseThrow().getStatus()
        );
    }

    @Test
    void overdueManualTimeoutExpiresBookingAndReleasesCapacity()
            throws Exception {
        Fixture fixture = createFixture(2, Instant.now().minusSeconds(1));
        PaymentIntent intent = paymentIntentRepository.saveAndFlush(
                new PaymentIntent(fixture.booking(), 20_000L, SEK)
        );
        TestIdentity admin = identity(RoleName.ADMIN);

        HttpResponse<String> response = timeOut(
                intent.getId(),
                admin.accessToken(),
                "evt-timeout"
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(
                "\"paymentStatus\":\"TIMED_OUT\""
        ));
        assertTrue(response.body().contains(
                "\"bookingStatus\":\"EXPIRED\""
        ));
        assertEquals(
                20,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
        assertEquals(1, paymentEventRepository.count());
        assertEquals(1, transitionRepository
                .findAllByBooking_IdOrderByOccurredAtAscIdAsc(
                        fixture.booking().getId()
                ).size());
    }

    @Test
    void earlyManualTimeoutIsRejected() throws Exception {
        Fixture fixture = createFixture(1, Instant.now().plusSeconds(900));
        TestIdentity admin = identity(RoleName.ADMIN);
        UUID intentId = createIntentId(fixture);

        HttpResponse<String> response = timeOut(
                intentId,
                admin.accessToken(),
                "evt-too-early"
        );

        assertEquals(409, response.statusCode());
        assertEquals(0, paymentEventRepository.count());
        assertEquals(
                19,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
    }

    @Test
    void exactManualTimeoutReplayAppliesOnce() throws Exception {
        Fixture fixture = createFixture(2, Instant.now().minusSeconds(1));
        PaymentIntent intent = paymentIntentRepository.saveAndFlush(
                new PaymentIntent(fixture.booking(), 20_000L, SEK)
        );
        TestIdentity admin = identity(RoleName.ADMIN);

        HttpResponse<String> first = timeOut(
                intent.getId(),
                admin.accessToken(),
                "evt-timeout-replay"
        );
        HttpResponse<String> replay = timeOut(
                intent.getId(),
                admin.accessToken(),
                "evt-timeout-replay"
        );

        assertEquals(200, first.statusCode());
        assertEquals(200, replay.statusCode());
        assertTrue(first.body().contains("\"replayed\":false"));
        assertTrue(replay.body().contains("\"replayed\":true"));
        assertEquals(1, paymentEventRepository.count());
        assertEquals(
                20,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
    }

    @Test
    void expiryProcessorHandlesBookingWithoutPaymentIntent() {
        Fixture fixture = createFixture(2, Instant.now().minusSeconds(1));

        assertTrue(expiryProcessor.expire(fixture.booking().getId()));
        assertEquals(
                BookingStatus.EXPIRED,
                bookingRepository.findById(fixture.booking().getId())
                        .orElseThrow().getStatus()
        );
        assertEquals(0, paymentEventRepository.count());
        assertEquals(
                20,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
    }

    @Test
    void expiryProcessorTimesOutIntentAndIsRepeatSafe() {
        Fixture fixture = createFixture(2, Instant.now().minusSeconds(1));
        PaymentIntent intent = paymentIntentRepository.saveAndFlush(
                new PaymentIntent(fixture.booking(), 20_000L, SEK)
        );

        assertTrue(expiryProcessor.expire(fixture.booking().getId()));
        assertTrue(!expiryProcessor.expire(fixture.booking().getId()));
        assertEquals(
                PaymentIntentStatus.TIMED_OUT,
                paymentIntentRepository.findById(intent.getId())
                        .orElseThrow().getStatus()
        );
        PaymentEvent event = paymentEventRepository.findAll()
                .getFirst();
        assertEquals(PaymentEventType.TIMED_OUT, event.getEventType());
        assertEquals(
                "scheduler-timeout:" + fixture.booking().getId(),
                event.getExternalEventId()
        );
        assertEquals(1, paymentEventRepository.count());
    }

    @Test
    void concurrentExpiryProcessorInvocationExpiresOnce() throws Exception {
        Fixture fixture = createFixture(2, Instant.now().minusSeconds(1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return expiryProcessor.expire(fixture.booking().getId());
            });
            Future<Boolean> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return expiryProcessor.expire(fixture.booking().getId());
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Boolean> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            ).stream().sorted().toList();
            assertEquals(List.of(false, true), results);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(
                20,
                bookingSlotRepository.findById(fixture.slot().getId())
                        .orElseThrow().getRemainingCapacity()
        );
        assertEquals(1, transitionRepository
                .findAllByBooking_IdOrderByOccurredAtAscIdAsc(
                        fixture.booking().getId()
                ).size());
    }

    private Fixture createFixture(int quantity, Instant expiresAt) {
        TestIdentity owner = identity(RoleName.CUSTOMER);
        TestIdentity organizer = identity(RoleName.ORGANIZER);
        Venue venue = venueRepository.saveAndFlush(
                new Venue(
                        "Payment API Venue",
                        "1 Test Street",
                        null,
                        "Stockholm",
                        null,
                        "11122",
                        "SE"
                )
        );
        Event event = eventRepository.saveAndFlush(
                new Event("Payment API Event", null, organizer.user())
        );
        EventSession session = eventSessionRepository.saveAndFlush(
                new EventSession(
                        event,
                        venue,
                        Instant.parse("2026-09-01T18:00:00Z"),
                        Instant.parse("2026-09-01T20:00:00Z"),
                        "Europe/Stockholm",
                        10_000L,
                        SEK
                )
        );
        BookingSlot slot = new BookingSlot(session, 20);
        slot.reserve(quantity);
        slot = bookingSlotRepository.saveAndFlush(slot);
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(owner.user(), session, expiresAt)
        );
        bookingItemRepository.saveAndFlush(
                new BookingItem(
                        booking,
                        slot,
                        quantity,
                        session.getUnitPriceMinor(),
                        session.getCurrency()
                )
        );
        return new Fixture(booking, slot, owner);
    }

    private TestIdentity identity(RoleName roleName) {
        return SecurityTestTokenFactory.createIdentity(
                roleName,
                userAccountRepository,
                roleRepository,
                jwtService
        );
    }

    private HttpResponse<String> postIntent(
            UUID bookingId,
            String accessToken
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/bookings/" + bookingId + "/payment-intent"))
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(
                request,
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

    private UUID createIntentId(Fixture fixture) throws Exception {
        String location = postIntent(
                fixture.booking().getId(),
                fixture.owner().accessToken()
        ).headers().firstValue("Location").orElseThrow();
        return idFromLocation(location);
    }

    private HttpResponse<String> authorize(
            UUID paymentIntentId,
            String accessToken,
            String eventId
    ) throws Exception {
        return callback(
                paymentIntentId,
                accessToken,
                eventId,
                "authorize"
        );
    }

    private HttpResponse<String> fail(
            UUID paymentIntentId,
            String accessToken,
            String eventId
    ) throws Exception {
        return callback(paymentIntentId, accessToken, eventId, "fail");
    }

    private HttpResponse<String> timeOut(
            UUID paymentIntentId,
            String accessToken,
            String eventId
    ) throws Exception {
        return callback(paymentIntentId, accessToken, eventId, "timeout");
    }

    private HttpResponse<String> callback(
            UUID paymentIntentId,
            String accessToken,
            String eventId,
            String outcome
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri(
                        "/api/v1/fake-payments/" + paymentIntentId
                                + "/" + outcome
                ))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        """
                        {
                          "eventId": "%s",
                          "occurredAt": "2026-08-20T12:00:00Z"
                        }
                        """.formatted(eventId)
                ))
                .build();
        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static UUID idFromLocation(String location) {
        return UUID.fromString(location.substring(
                location.lastIndexOf('/') + 1
        ));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private record Fixture(
            Booking booking,
            BookingSlot slot,
            TestIdentity owner
    ) {
    }
}
