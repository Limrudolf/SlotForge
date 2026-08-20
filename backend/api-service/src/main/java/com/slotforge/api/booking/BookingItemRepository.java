package com.slotforge.api.booking;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingItemRepository extends JpaRepository<BookingItem, UUID> {

    Optional<BookingItem> findByBooking_Id(UUID bookingId);

    List<BookingItem> findAllByBooking_IdIn(Collection<UUID> bookingIds);
}
