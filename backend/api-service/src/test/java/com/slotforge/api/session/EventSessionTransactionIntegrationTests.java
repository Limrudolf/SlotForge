package com.slotforge.api.session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.slotforge.api.TestcontainersConfiguration;
import com.slotforge.api.SecurityTestTokenFactory;
import com.slotforge.api.SecurityTestTokenFactory.TestIdentity;
import com.slotforge.api.availability.BookingSlot;
import com.slotforge.api.availability.BookingSlotRepository;
import com.slotforge.api.event.Event;
import com.slotforge.api.event.EventRepository;
import com.slotforge.api.security.JwtService;
import com.slotforge.api.user.RoleName;
import com.slotforge.api.user.RoleRepository;
import com.slotforge.api.user.UserAccountRepository;
import com.slotforge.api.venue.Venue;
import com.slotforge.api.venue.VenueRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class EventSessionTransactionIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @MockitoBean
    private BookingSlotRepository bookingSlotRepository;

    @Autowired
    private EventSessionRepository eventSessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private TestIdentity organizerIdentity;

    @BeforeEach
    void clearDatabase() {
        eventSessionRepository.deleteAll();
        eventRepository.deleteAll();
        venueRepository.deleteAll();
        organizerIdentity = SecurityTestTokenFactory.createIdentity(
                RoleName.ORGANIZER,
                userAccountRepository,
                roleRepository,
                jwtService
        );
    }

    @Test
    void capacityPersistenceFailureRollsBackSessionInsert()
            throws Exception {

        Event event = eventRepository.saveAndFlush(
                new Event(
                        "Rollback Test Event",
                        null,
                        organizerIdentity.user()
                )
        );
        Venue venue = venueRepository.saveAndFlush(
                new Venue(
                        "Rollback Test Venue",
                        "1 Test Street",
                        null,
                        "Stockholm",
                        null,
                        "111 11",
                        "SE"
                )
        );

        when(bookingSlotRepository.save(any(BookingSlot.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Forced capacity persistence failure"
                ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:"
                                + port
                                + "/api/v1/events/"
                                + event.getId()
                                + "/sessions"
                ))
                .header("Content-Type", "application/json")
                .header(
                        "Authorization",
                        "Bearer " + organizerIdentity.accessToken()
                )
                .POST(HttpRequest.BodyPublishers.ofString(
                        """
                        {
                          "venueId": "%s",
                          "startTime": "2026-10-10T19:00:00+02:00",
                          "endTime": "2026-10-10T22:00:00+02:00",
                          "displayTimezone": "Europe/Stockholm",
                          "totalCapacity": 500
                        }
                        """.formatted(venue.getId())
                ))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(409, response.statusCode());
        assertEquals(0, eventSessionRepository.count());
    }
}
