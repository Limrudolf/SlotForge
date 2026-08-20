package com.slotforge.api.availability;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BookingSlotRepository
        extends JpaRepository<BookingSlot, UUID> {

    Optional<BookingSlot> findByEventSession_Id(UUID eventSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bookingSlot
            from BookingSlot bookingSlot
            where bookingSlot.eventSession.id = :eventSessionId
            """)
    Optional<BookingSlot> findByEventSessionIdForUpdate(
            @Param("eventSessionId") UUID eventSessionId
    );
}
