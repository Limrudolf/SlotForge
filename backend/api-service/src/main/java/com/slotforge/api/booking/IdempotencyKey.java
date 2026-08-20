package com.slotforge.api.booking;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.slotforge.api.user.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String keyValue;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
        // Required by JPA.
    }

    public IdempotencyKey(
            UserAccount user,
            String keyValue,
            String requestFingerprint,
            Booking booking
    ) {
        if (keyValue == null || keyValue.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }
        if (requestFingerprint == null
                || !requestFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Request fingerprint must be a lowercase SHA-256 value"
            );
        }
        this.user = user;
        this.keyValue = keyValue;
        this.requestFingerprint = requestFingerprint;
        this.booking = booking;
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getKeyValue() { return keyValue; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Booking getBooking() { return booking; }
    public Instant getCreatedAt() { return createdAt; }
}
