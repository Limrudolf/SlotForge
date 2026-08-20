package com.slotforge.api.booking;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import com.slotforge.api.security.CurrentActor;
import com.slotforge.api.security.CurrentActorProvider;

@Service
public class BookingService {

    private final CurrentActorProvider currentActorProvider;
    private final BookingRequestFingerprint requestFingerprint;
    private final BookingCreationTransaction creationTransaction;

    public BookingService(
            CurrentActorProvider currentActorProvider,
            BookingRequestFingerprint requestFingerprint,
            BookingCreationTransaction creationTransaction
    ) {
        this.currentActorProvider = currentActorProvider;
        this.requestFingerprint = requestFingerprint;
        this.creationTransaction = creationTransaction;
    }

    public BookingCreationResult create(
            UUID sessionId,
            String idempotencyKey,
            CreateBookingRequest request
    ) {
        CurrentActor actor = currentActorProvider.currentActor();
        String normalizedKey = idempotencyKey.trim();
        String fingerprint = requestFingerprint.create(
                sessionId,
                request.quantity()
        );

        try {
            return creationTransaction.create(
                    actor.userId(),
                    sessionId,
                    request.quantity(),
                    normalizedKey,
                    fingerprint
            );
        } catch (DataIntegrityViolationException exception) {
            return creationTransaction.resolveExisting(
                            actor.userId(),
                            normalizedKey,
                            fingerprint
                    )
                    .orElseThrow(() -> exception);
        }
    }
}
