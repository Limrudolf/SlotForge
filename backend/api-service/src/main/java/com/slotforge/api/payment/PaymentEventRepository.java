package com.slotforge.api.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository
        extends JpaRepository<PaymentEvent, UUID> {

    Optional<PaymentEvent> findByExternalEventId(String externalEventId);

    boolean existsByExternalEventId(String externalEventId);

    List<PaymentEvent>
            findAllByPaymentIntent_IdOrderByReceivedAtAscIdAsc(
                    UUID paymentIntentId
            );
}
