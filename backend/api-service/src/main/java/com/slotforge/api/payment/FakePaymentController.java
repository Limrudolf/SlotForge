package com.slotforge.api.payment;

import static com.slotforge.api.common.config.OpenApiConfiguration.BEARER_AUTH;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/fake-payments")
@Tag(
        name = "Fake Payments",
        description = "Administrative payment-provider simulation"
)
public class FakePaymentController {

    private final PaymentAuthorizationService authorizationService;
    private final PaymentFailureService failureService;
    private final PaymentTimeoutService timeoutService;

    public FakePaymentController(
            PaymentAuthorizationService authorizationService,
            PaymentFailureService failureService,
            PaymentTimeoutService timeoutService
    ) {
        this.authorizationService = authorizationService;
        this.failureService = failureService;
        this.timeoutService = timeoutService;
    }

    @PostMapping("/{paymentIntentId}/authorize")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Authorize a fake payment",
            description = """
                    Simulates an idempotent payment-provider authorization
                    callback and confirms the associated booking.
                    """
    )
    public PaymentCallbackResponse authorize(
            @PathVariable UUID paymentIntentId,
            @Valid @RequestBody FakePaymentCallbackRequest request
    ) {
        return authorizationService.authorize(paymentIntentId, request);
    }

    @PostMapping("/{paymentIntentId}/fail")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Fail a fake payment",
            description = """
                    Simulates an idempotent payment-provider failure callback,
                    marks the booking as PAYMENT_FAILED, and restores its held
                    capacity.
                    """
    )
    public PaymentCallbackResponse fail(
            @PathVariable UUID paymentIntentId,
            @Valid @RequestBody FakePaymentCallbackRequest request
    ) {
        return failureService.fail(paymentIntentId, request);
    }

    @PostMapping("/{paymentIntentId}/timeout")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Time out a fake payment",
            description = """
                    Expires an overdue payment reservation and atomically
                    restores its held capacity.
                    """
    )
    public PaymentCallbackResponse timeOut(
            @PathVariable UUID paymentIntentId,
            @Valid @RequestBody FakePaymentCallbackRequest request
    ) {
        return timeoutService.timeOut(paymentIntentId, request);
    }
}
