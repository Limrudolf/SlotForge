package com.slotforge.api.booking;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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

@Entity
@Table(name = "booking_state_transitions")
public class BookingStateTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 30)
    private BookingStatus fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 30)
    private BookingStatus toState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private UserAccount changedByUser;

    @Column(name = "reason", length = 255)
    private String reason;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected BookingStateTransition() {
        // Required by JPA.
    }

    public BookingStateTransition(
            Booking booking,
            BookingStatus fromState,
            BookingStatus toState,
            UserAccount changedByUser,
            String reason
    ) {
        if (toState == null) {
            throw new IllegalArgumentException("Destination state is required");
        }
        if (fromState == toState) {
            throw new IllegalArgumentException("A transition must change state");
        }
        if (reason != null && reason.isBlank()) {
            throw new IllegalArgumentException("Transition reason must not be blank");
        }
        this.booking = booking;
        this.fromState = fromState;
        this.toState = toState;
        this.changedByUser = changedByUser;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public BookingStatus getFromState() { return fromState; }
    public BookingStatus getToState() { return toState; }
    public UserAccount getChangedByUser() { return changedByUser; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
