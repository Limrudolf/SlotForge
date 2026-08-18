package com.slotforge.api.venue;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.slotforge.api.common.PageResponse;

@Service
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Transactional
    public VenueResponse create(CreateVenueRequest request) {
        Venue venue = new Venue(
                request.name().trim(),
                request.addressLine1().trim(),
                trimToNull(request.addressLine2()),
                request.city().trim(),
                trimToNull(request.region()),
                trimToNull(request.postalCode()),
                request.countryCode()
                        .trim()
                        .toUpperCase(Locale.ROOT)
        );

        Venue savedVenue = venueRepository.saveAndFlush(venue);

        return VenueResponse.from(savedVenue);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> list(int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "name")
                        .and(Sort.by(Sort.Direction.ASC, "id"))
        );

        Page<VenueResponse> venues = venueRepository
                .findAll(pageRequest)
                .map(VenueResponse::from);

        return PageResponse.from(venues);
    }
}
