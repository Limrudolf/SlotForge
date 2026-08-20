package com.slotforge.api.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Currency;

import org.junit.jupiter.api.Test;

import com.slotforge.api.booking.Booking;
import com.slotforge.api.booking.BookingItem;
import com.slotforge.api.common.persistence.CurrencyAttributeConverter;

class PaymentDomainTests {

    private static final Currency SEK = Currency.getInstance("SEK");

    @Test
    void storesMoneyAsLongMinorUnits() throws NoSuchFieldException {
        PaymentIntent paymentIntent = paymentIntent();

        assertEquals(12_500L, paymentIntent.getAmountMinor());
        assertEquals(
                long.class,
                PaymentIntent.class
                        .getDeclaredField("amountMinor")
                        .getType()
        );
    }

    @Test
    void rejectsNonPositivePaymentAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentIntent(booking(), 0, SEK)
        );
    }

    @Test
    void usesRecognizedJavaCurrencyValues() {
        assertEquals(SEK, paymentIntent().getCurrency());
        assertThrows(
                IllegalArgumentException.class,
                () -> Currency.getInstance("LOL")
        );
    }

    @Test
    void convertsCurrencyToAndFromItsDatabaseCode() {
        CurrencyAttributeConverter converter =
                new CurrencyAttributeConverter();

        assertEquals("SEK", converter.convertToDatabaseColumn(SEK));
        assertEquals(SEK, converter.convertToEntityAttribute("SEK"));
    }

    @Test
    void authorizesPendingPaymentIntent() {
        PaymentIntent paymentIntent = paymentIntent();

        paymentIntent.authorize();

        assertEquals(
                PaymentIntentStatus.AUTHORIZED,
                paymentIntent.getStatus()
        );
        assertTrue(paymentIntent.getStatus().isTerminal());
    }

    @Test
    void rejectsConflictingOutcomeAfterFailure() {
        PaymentIntent paymentIntent = paymentIntent();
        paymentIntent.fail();

        assertThrows(
                InvalidPaymentStateTransitionException.class,
                paymentIntent::authorize
        );
        assertEquals(PaymentIntentStatus.FAILED, paymentIntent.getStatus());
    }

    @Test
    void pendingPaymentIntentIsNotTerminal() {
        assertFalse(paymentIntent().getStatus().isTerminal());
    }

    @Test
    void validatesExternalPaymentEventIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentEvent(
                        paymentIntent(),
                        "   ",
                        PaymentEventType.AUTHORIZED,
                        Instant.now()
                )
        );
    }

    @Test
    void calculatesBookingItemTotalWithExactIntegerArithmetic() {
        BookingItem item = new BookingItem(
                null,
                null,
                3,
                10_000L,
                SEK
        );

        assertEquals(30_000L, item.totalAmountMinor());
    }

    @Test
    void rejectsOverflowWhileCalculatingBookingItemTotal() {
        BookingItem item = new BookingItem(
                null,
                null,
                2,
                Long.MAX_VALUE,
                SEK
        );

        assertThrows(ArithmeticException.class, item::totalAmountMinor);
    }

    private static PaymentIntent paymentIntent() {
        return new PaymentIntent(booking(), 12_500L, SEK);
    }

    private static Booking booking() {
        return new Booking(
                null,
                null,
                Instant.parse("2026-08-20T12:15:00Z")
        );
    }
}
