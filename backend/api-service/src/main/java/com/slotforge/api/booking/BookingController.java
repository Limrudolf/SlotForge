package com.slotforge.api.booking;

import static com.slotforge.api.common.config.OpenApiConfiguration.BEARER_AUTH;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.slotforge.api.common.PageResponse;
import com.slotforge.api.common.error.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Bookings",
        description = "Create and manage scarce-capacity bookings"
)
public class BookingController {

    private final BookingService bookingService;
    private final BookingQueryService bookingQueryService;
    private final BookingCancellationService bookingCancellationService;

    public BookingController(
            BookingService bookingService,
            BookingQueryService bookingQueryService,
            BookingCancellationService bookingCancellationService
    ) {
        this.bookingService = bookingService;
        this.bookingQueryService = bookingQueryService;
        this.bookingCancellationService = bookingCancellationService;
    }

    @PostMapping("/sessions/{sessionId}/bookings")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Create a booking",
            description = """
                    Atomically reserves capacity and creates a booking in the
                    PENDING_PAYMENT state.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Booking created",
                    headers = @Header(
                            name = "Location",
                            description = "Canonical URL of the booking"
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "Original booking returned for a replay"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid booking request",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Customer role is required",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event session not found",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Requested capacity is unavailable",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            )
    })
    public ResponseEntity<BookingResponse> create(
            @PathVariable UUID sessionId,
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key must not be blank")
            @Size(
                    max = 255,
                    message = "Idempotency-Key must not exceed 255 characters"
            )
            String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingCreationResult result = bookingService.create(
                sessionId,
                idempotencyKey,
                request
        );
        BookingResponse response = result.booking();
        URI location = URI.create("/api/v1/bookings/" + response.id());
        if (result.replayed()) {
            return ResponseEntity.ok().location(location).body(response);
        }

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/bookings/{bookingId}")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Get a booking",
            description = "Returns a booking to its owner or an administrator."
    )
    public BookingResponse get(@PathVariable UUID bookingId) {
        return bookingQueryService.get(bookingId);
    }

    @GetMapping("/me/bookings")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "List the current user's bookings",
            description = """
                    Returns the authenticated user's bookings in reverse
                    chronological order using zero-based pagination.
                    """
    )
    public PageResponse<BookingResponse> listCurrentUser(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be zero or greater")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100")
            int size
    ) {
        return bookingQueryService.listCurrentUser(page, size);
    }

    @GetMapping("/bookings/{bookingId}/state-transitions")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "List booking state transitions",
            description = """
                    Returns the authorized booking's state history in
                    chronological order.
                    """
    )
    public List<BookingStateTransitionResponse> listTransitions(
            @PathVariable UUID bookingId
    ) {
        return bookingQueryService.listTransitions(bookingId);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Cancel a booking",
            description = """
                    Cancels an eligible booking and atomically restores its
                    held capacity. Only the booking owner may cancel it.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Booking cancelled and capacity restored"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The user does not own the booking",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Booking not found",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "State conflict or concurrent modification",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            )
    })
    public BookingResponse cancel(@PathVariable UUID bookingId) {
        return bookingCancellationService.cancel(bookingId);
    }
}
