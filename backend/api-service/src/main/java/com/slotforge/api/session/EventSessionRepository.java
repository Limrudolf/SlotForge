package com.slotforge.api.session;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSessionRepository
        extends JpaRepository<EventSession, UUID> {

    Page<EventSession> findByEvent_Id(
            UUID eventId,
            Pageable pageable
    );
}