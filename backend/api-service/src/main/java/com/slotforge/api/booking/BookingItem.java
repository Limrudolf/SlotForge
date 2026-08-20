package com.slotforge.api.booking;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.slotforge.api.availability.BookingSlot;
import com.slotforge.api.common.persistence.CurrencyAttributeConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "booking_items")
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_slot_id", nullable = false)
    private BookingSlot bookingSlot;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Convert(converter = CurrencyAttributeConverter.class)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BookingItem() {
        // Required by JPA.
    }

    public BookingItem(
            Booking booking,
            BookingSlot bookingSlot,
            int quantity,
            long unitPriceMinor,
            Currency currency
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Booking item quantity must be positive");
        }
        if (unitPriceMinor <= 0) {
            throw new IllegalArgumentException(
                    "Booking item unit price must be positive"
            );
        }
        this.booking = booking;
        this.bookingSlot = bookingSlot;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
        this.currency = Objects.requireNonNull(
                currency,
                "Booking item currency is required"
        );
    }

    public long totalAmountMinor() {
        return Math.multiplyExact(unitPriceMinor, quantity);
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public BookingSlot getBookingSlot() { return bookingSlot; }
    public int getQuantity() { return quantity; }
    public long getUnitPriceMinor() { return unitPriceMinor; }
    public Currency getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
