package com.slotforge.api.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PaymentIntentRepository
        extends JpaRepository<PaymentIntent, UUID> {

    Optional<PaymentIntent> findByBooking_Id(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select paymentIntent
            from PaymentIntent paymentIntent
            where paymentIntent.id = :paymentIntentId
            """)
    Optional<PaymentIntent> findByIdForUpdate(
            @Param("paymentIntentId") UUID paymentIntentId
    );
}
