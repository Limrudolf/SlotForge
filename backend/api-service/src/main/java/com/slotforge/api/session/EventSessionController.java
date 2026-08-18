package com.slotforge.api.session;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slotforge.api.common.PageResponse;
import com.slotforge.api.common.error.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Event Sessions",
        description = "Schedule and retrieve occurrences of events"
)
public class EventSessionController {

    private final EventSessionService eventSessionService;

    public EventSessionController(
            EventSessionService eventSessionService
    ) {
        this.eventSessionService = eventSessionService;
    }

    @PostMapping("/events/{eventId}/sessions")
    @Operation(
            summary = "Create an event session",
            description = """
                    Creates a scheduled event session and its capacity state
                    atomically. Input times must contain a UTC offset.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Event session and capacity created",
                    headers = @Header(
                            name = "Location",
                            description = "Canonical URL of the session"
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid session request",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event or venue not found",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Session creation conflicts with stored data",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            )
    })
    public ResponseEntity<EventSessionResponse> create(
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateEventSessionRequest request
    ) {
        EventSessionResponse response = eventSessionService.create(
                eventId,
                request
        );

        URI location = URI.create(
                "/api/v1/sessions/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/events/{eventId}/sessions")
    @Operation(
            summary = "List sessions for an event",
            description = """
                    Returns sessions in chronological order using zero-based
                    pagination.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Chronological session page returned"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination parameters",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            )
    })
    public PageResponse<EventSessionResponse> listForEvent(
            @PathVariable UUID eventId,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be zero or greater")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100")
            int size
    ) {
        return eventSessionService.listForEvent(
                eventId,
                page,
                size
        );
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(
            summary = "Get an event session",
            description = """
                    Returns one session with UTC timestamps and its separate
                    IANA display timezone.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Event session returned"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed session UUID",
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
            )
    })
    public EventSessionResponse get(
            @PathVariable UUID sessionId
    ) {
        return eventSessionService.get(sessionId);
    }
}
