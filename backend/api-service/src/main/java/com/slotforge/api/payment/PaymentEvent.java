package com.slotforge.api.payment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_intent_id", nullable = false)
    private PaymentIntent paymentIntent;

    @Column(
            name = "external_event_id",
            nullable = false,
            unique = true,
            length = 255
    )
    private String externalEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private PaymentEventType eventType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected PaymentEvent() {
        // Required by JPA.
    }

    public PaymentEvent(
            PaymentIntent paymentIntent,
            String externalEventId,
            PaymentEventType eventType,
            Instant occurredAt
    ) {
        this.paymentIntent = Objects.requireNonNull(
                paymentIntent,
                "Payment intent is required"
        );
        this.externalEventId = requireExternalEventId(externalEventId);
        this.eventType = Objects.requireNonNull(
                eventType,
                "Payment event type is required"
        );
        this.occurredAt = Objects.requireNonNull(
                occurredAt,
                "Payment event occurrence time is required"
        );
    }

    private static String requireExternalEventId(String externalEventId) {
        String normalized = Objects.requireNonNull(
                externalEventId,
                "External event ID is required"
        ).trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "External event ID must not be blank"
            );
        }
        if (normalized.length() > 255) {
            throw new IllegalArgumentException(
                    "External event ID must not exceed 255 characters"
            );
        }

        return normalized;
    }

    public UUID getId() { return id; }
    public PaymentIntent getPaymentIntent() { return paymentIntent; }
    public String getExternalEventId() { return externalEventId; }
    public PaymentEventType getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
