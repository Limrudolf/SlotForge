package com.slotforge.api.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findAllByUser_Id(UUID userId);

    @EntityGraph(attributePaths = {"user", "eventSession"})
    @Query("""
            select booking
            from Booking booking
            where booking.id = :bookingId
            """)
    Optional<Booking> findWithDetailsById(
            @Param("bookingId") UUID bookingId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            where booking.id = :bookingId
            """)
    Optional<Booking> findByIdForUpdate(
            @Param("bookingId") UUID bookingId
    );

    @EntityGraph(attributePaths = {"user", "eventSession"})
    Page<Booking> findByUser_Id(UUID userId, Pageable pageable);

    @Query("""
            select booking.id
            from Booking booking
            where booking.status = :status
              and booking.paymentExpiresAt <= :now
            order by booking.paymentExpiresAt, booking.id
            """)
    List<UUID> findExpiredCandidateIds(
            @Param("status") BookingStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
