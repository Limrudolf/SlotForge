package com.slotforge.api.availability;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slotforge.api.session.EventSessionService;
import com.slotforge.api.common.error.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(
        name = "Availability",
        description = "Read authoritative session-capacity state"
)
public class AvailabilityController {

    private final EventSessionService eventSessionService;

    public AvailabilityController(
            EventSessionService eventSessionService
    ) {
        this.eventSessionService = eventSessionService;
    }

    @GetMapping("/{sessionId}/availability")
    @Operation(
            summary = "Get session availability",
            description = """
                    Returns total and remaining capacity for one event session.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Session availability returned"
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
    public AvailabilityResponse getAvailability(
            @PathVariable UUID sessionId
    ) {
        return eventSessionService.getAvailability(sessionId);
    }
}
