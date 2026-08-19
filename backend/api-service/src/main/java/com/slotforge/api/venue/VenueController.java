package com.slotforge.api.venue;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/venues")
@Tag(
        name = "Venues",
        description = "Create and list event venues"
)
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @SecurityRequirement(name = BEARER_AUTH)
    @Operation(
            summary = "Create a venue",
            description = "Creates a venue and normalizes its country code."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Venue created",
                    headers = @Header(
                            name = "Location",
                            description = "Canonical URL of the venue"
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid venue request",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            )
    })
    public ResponseEntity<VenueResponse> create(
            @Valid @RequestBody CreateVenueRequest request
    ) {
        VenueResponse response = venueService.create(request);

        URI location = URI.create(
                "/api/v1/venues/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping
    @Operation(
            summary = "List venues",
            description = "Returns venues using zero-based pagination."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Venue page returned"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination parameters",
                    content = @Content(schema = @Schema(
                            implementation = ApiError.class
                    ))
            )
    })
    public PageResponse<VenueResponse> list(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be zero or greater")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must not exceed 100")
            int size
    ) {
        return venueService.list(page, size);
    }
}
