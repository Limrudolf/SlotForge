package com.slotforge.api.availability;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.slotforge.api.session.EventSession;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "booking_slots")
public class BookingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "event_session_id",
            nullable = false,
            unique = true
    )
    private EventSession eventSession;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "remaining_capacity", nullable = false)
    private int remainingCapacity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected BookingSlot() {
        // Required by JPA.
    }

    public BookingSlot(EventSession eventSession, int totalCapacity) {
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("Total capacity must be positive");
        }
        this.eventSession = eventSession;
        this.totalCapacity = totalCapacity;
        this.remainingCapacity = totalCapacity;
    }

    public boolean canReserve(int quantity) {
        return quantity > 0 && quantity <= remainingCapacity;
    }

    public void reserve(int quantity) {
        requirePositiveQuantity(quantity);
        if (quantity > remainingCapacity) {
            throw new IllegalStateException("Insufficient remaining capacity");
        }
        remainingCapacity -= quantity;
    }

    public void release(int quantity) {
        requirePositiveQuantity(quantity);
        if (remainingCapacity + quantity > totalCapacity) {
            throw new IllegalStateException("Released capacity exceeds total capacity");
        }
        remainingCapacity += quantity;
    }

    private static void requirePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public UUID getId() {
        return id;
    }

    public EventSession getEventSession() {
        return eventSession;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getRemainingCapacity() {
        return remainingCapacity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
