package com.slotforge.api.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.booking.Booking;
import com.slotforge.api.booking.BookingRepository;
import com.slotforge.api.event.Event;
import com.slotforge.api.event.EventRepository;
import com.slotforge.api.session.EventSession;
import com.slotforge.api.session.EventSessionRepository;
import com.slotforge.api.user.UserAccount;
import com.slotforge.api.user.UserAccountRepository;
import com.slotforge.api.venue.Venue;
import com.slotforge.api.venue.VenueRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PaymentPersistenceIntegrationTests {

    private static final Currency SEK = Currency.getInstance("SEK");

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EventSessionRepository eventSessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    void persistsIntentWithMinorUnitsAndCurrencyConverter() {
        Booking booking = persistBooking();
        PaymentIntent saved = paymentIntentRepository.saveAndFlush(
                new PaymentIntent(booking, 12_500L, SEK)
        );

        PaymentIntent loaded = paymentIntentRepository
                .findByBooking_Id(booking.getId())
                .orElseThrow();

        assertEquals(saved.getId(), loaded.getId());
        assertEquals(booking.getId(), loaded.getBooking().getId());
        assertEquals(12_500L, loaded.getAmountMinor());
        assertEquals(SEK, loaded.getCurrency());
        assertEquals(PaymentIntentStatus.PENDING, loaded.getStatus());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }

    @Test
    void databaseEnforcesOnePaymentIntentPerBooking() {
        Booking booking = persistBooking();
        paymentIntentRepository.saveAndFlush(
                new PaymentIntent(booking, 12_500L, SEK)
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> paymentIntentRepository.saveAndFlush(
                        new PaymentIntent(booking, 12_500L, SEK)
                )
        );
    }

    @Test
    void persistsAndQueriesPaymentEventHistory() {
        PaymentIntent paymentIntent = persistPaymentIntent();
        Instant firstOccurrence =
                Instant.parse("2026-08-20T12:00:00Z");
        PaymentEvent saved = paymentEventRepository.saveAndFlush(
                new PaymentEvent(
                        paymentIntent,
                        "evt-123",
                        PaymentEventType.AUTHORIZED,
                        firstOccurrence
                )
        );

        PaymentEvent byExternalId = paymentEventRepository
                .findByExternalEventId("evt-123")
                .orElseThrow();
        List<PaymentEvent> history = paymentEventRepository
                .findAllByPaymentIntent_IdOrderByReceivedAtAscIdAsc(
                        paymentIntent.getId()
                );

        assertEquals(saved.getId(), byExternalId.getId());
        assertEquals(1, history.size());
        assertEquals(
                PaymentEventType.AUTHORIZED,
                history.getFirst().getEventType()
        );
        assertEquals(firstOccurrence, history.getFirst().getOccurredAt());
        assertNotNull(history.getFirst().getReceivedAt());
    }

    @Test
    void databaseRejectsDuplicateExternalEventId() {
        PaymentIntent paymentIntent = persistPaymentIntent();
        Instant occurredAt = Instant.parse("2026-08-20T12:00:00Z");
        paymentEventRepository.saveAndFlush(
                new PaymentEvent(
                        paymentIntent,
                        "evt-duplicate",
                        PaymentEventType.AUTHORIZED,
                        occurredAt
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> paymentEventRepository.saveAndFlush(
                        new PaymentEvent(
                                paymentIntent,
                                "evt-duplicate",
                                PaymentEventType.AUTHORIZED,
                                occurredAt
                        )
                )
        );
    }

    @Test
    void pessimisticLookupReturnsIntentInsideTransaction() {
        PaymentIntent saved = persistPaymentIntent();

        PaymentIntent locked = paymentIntentRepository
                .findByIdForUpdate(saved.getId())
                .orElseThrow();

        assertEquals(saved.getId(), locked.getId());
        assertEquals(PaymentIntentStatus.PENDING, locked.getStatus());
    }

    private PaymentIntent persistPaymentIntent() {
        return paymentIntentRepository.saveAndFlush(
                new PaymentIntent(persistBooking(), 12_500L, SEK)
        );
    }

    private Booking persistBooking() {
        String fixtureId = UUID.randomUUID().toString();
        UserAccount customer = userAccountRepository.save(
                new UserAccount(
                        "customer-" + fixtureId + "@example.com",
                        "encoded-password"
                )
        );
        UserAccount organizer = userAccountRepository.save(
                new UserAccount(
                        "organizer-" + fixtureId + "@example.com",
                        "encoded-password"
                )
        );
        Venue venue = venueRepository.save(
                new Venue(
                        "Payment Test Venue",
                        "1 Test Street",
                        null,
                        "Stockholm",
                        null,
                        "11122",
                        "SE"
                )
        );
        Event event = eventRepository.save(
                new Event("Payment Test Event", null, organizer)
        );
        EventSession session = eventSessionRepository.save(
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

        return bookingRepository.save(
                new Booking(
                        customer,
                        session,
                        Instant.parse("2026-08-20T12:15:00Z")
                )
        );
    }
}
