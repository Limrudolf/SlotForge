package com.slotforge.api.payment;

import static com.slotforge.api.common.config.OpenApiConfiguration.BEARER_AUTH;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Payments",
        description = "Create and inspect fake payment attempts"
)
public class PaymentIntentController {

    private final PaymentIntentService paymentIntentService;

    public PaymentIntentController(PaymentIntentService paymentIntentService) {
        this.paymentIntentService = paymentIntentService;
    }

    @PostMapping("/bookings/{bookingId}/payment-intent")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Create a payment intent",
            description = """
                    Creates one payment intent from the booking's immutable
                    price snapshot. Repeated creation returns the same intent.
                    """
    )
    public ResponseEntity<PaymentIntentResponse> create(
            @PathVariable UUID bookingId
    ) {
        PaymentIntentCreationResult result =
                paymentIntentService.create(bookingId);
        PaymentIntentResponse response = result.paymentIntent();
        URI location = URI.create(
                "/api/v1/payment-intents/" + response.id()
        );
        if (result.replayed()) {
            return ResponseEntity.ok().location(location).body(response);
        }
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/payment-intents/{paymentIntentId}")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(summary = "Get a payment intent")
    public PaymentIntentResponse get(
            @PathVariable UUID paymentIntentId
    ) {
        return paymentIntentService.get(paymentIntentId);
    }
}
