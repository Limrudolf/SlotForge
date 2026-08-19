package com.slotforge.api.event;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import static com.slotforge.api.common.config.OpenApiConfiguration.BEARER_AUTH;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/events")
@Tag(
        name = "Events",
        description = "Create, retrieve, list, and update events"
)
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Create an event",
            description = "Creates a new event in DRAFT status."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Event created",
                    headers = @Header(
                            name = "Location",
                            description = "Canonical URL of the event"
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid event request",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            )
    })
    public ResponseEntity<EventResponse> create(
            @Valid @RequestBody CreateEventRequest request
    ) {
        EventResponse response = eventService.create(request);

        URI location = URI.create(
                "/api/v1/events/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping
    @Operation(
            summary = "List events",
            description = """
                    Returns events using zero-based pagination, with optional
                    filtering by event status.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Event page returned"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or status filter",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            )
    })
    public PageResponse<EventResponse> list(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be zero or greater")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100")
            int size,

            @RequestParam(required = false)
            EventStatus status
    ) {
        return eventService.list(page, size, status);
    }

    @GetMapping("/{eventId}")
    @Operation(
            summary = "Get an event",
            description = "Returns one event by its UUID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Event returned"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed event UUID",
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
    public EventResponse get(
            @PathVariable UUID eventId
    ) {
        return eventService.get(eventId);
    }

    @PatchMapping("/{eventId}")
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Partially update an event",
            description = """
                    Updates only supplied fields. Omitted fields remain
                    unchanged.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Event updated"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid event update",
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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Concurrent update conflict",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            )
    })
    public EventResponse update(
            @PathVariable UUID eventId,
            @Valid @RequestBody UpdateEventRequest request
    ) {
        return eventService.update(eventId, request);
    }
}
