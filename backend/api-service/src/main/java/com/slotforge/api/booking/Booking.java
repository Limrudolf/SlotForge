package com.slotforge.api.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.slotforge.api.session.EventSession;
import com.slotforge.api.user.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_session_id", nullable = false)
    private EventSession eventSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BookingStatus status;

    @Column(name = "payment_expires_at", nullable = false)
    private Instant paymentExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Booking() {
        // Required by JPA.
    }

    public Booking(
            UserAccount user,
            EventSession eventSession,
            Instant paymentExpiresAt
    ) {
        this.user = user;
        this.eventSession = eventSession;
        this.paymentExpiresAt = Objects.requireNonNull(
                paymentExpiresAt,
                "Payment expiry is required"
        );
        this.status = BookingStatus.PENDING_PAYMENT;
    }

    public void authorizePayment() {
        transitionTo(
                BookingStatus.PAYMENT_AUTHORIZED,
                BookingStatus.PENDING_PAYMENT
        );
    }

    public void confirm() {
        transitionTo(
                BookingStatus.CONFIRMED,
                BookingStatus.PAYMENT_AUTHORIZED
        );
    }

    public void failPayment() {
        transitionTo(
                BookingStatus.PAYMENT_FAILED,
                BookingStatus.PENDING_PAYMENT
        );
    }

    public void expire() {
        transitionTo(
                BookingStatus.EXPIRED,
                BookingStatus.PENDING_PAYMENT
        );
    }

    public void cancel() {
        transitionTo(
                BookingStatus.CANCELLED,
                BookingStatus.PENDING_PAYMENT,
                BookingStatus.CONFIRMED
        );
    }

    public boolean isPaymentExpired(Instant now) {
        Objects.requireNonNull(now, "Current time is required");
        return !now.isBefore(paymentExpiresAt);
    }

    private void transitionTo(
            BookingStatus destination,
            BookingStatus... permittedSources
    ) {
        for (BookingStatus permittedSource : permittedSources) {
            if (status == permittedSource) {
                status = destination;
                return;
            }
        }

        throw new InvalidBookingStateTransitionException(
                id,
                status,
                destination
        );
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public EventSession getEventSession() { return eventSession; }
    public BookingStatus getStatus() { return status; }
    public Instant getPaymentExpiresAt() { return paymentExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
