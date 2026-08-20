package com.slotforge.api.payment;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.slotforge.api.booking.Booking;
import com.slotforge.api.common.persistence.CurrencyAttributeConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payment_intents")
public class PaymentIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Convert(converter = CurrencyAttributeConverter.class)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentIntentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PaymentIntent() {
        // Required by JPA.
    }

    public PaymentIntent(
            Booking booking,
            long amountMinor,
            Currency currency
    ) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be positive"
            );
        }

        this.booking = Objects.requireNonNull(
                booking,
                "Booking is required"
        );
        this.amountMinor = amountMinor;
        this.currency = Objects.requireNonNull(
                currency,
                "Currency is required"
        );
        this.status = PaymentIntentStatus.PENDING;
    }

    public void authorize() {
        transitionFromPendingTo(PaymentIntentStatus.AUTHORIZED);
    }

    public void fail() {
        transitionFromPendingTo(PaymentIntentStatus.FAILED);
    }

    public void timeOut() {
        transitionFromPendingTo(PaymentIntentStatus.TIMED_OUT);
    }

    private void transitionFromPendingTo(
            PaymentIntentStatus destination
    ) {
        if (status != PaymentIntentStatus.PENDING) {
            throw new InvalidPaymentStateTransitionException(
                    id,
                    status,
                    destination
            );
        }

        status = destination;
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public long getAmountMinor() { return amountMinor; }
    public Currency getCurrency() { return currency; }
    public PaymentIntentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
