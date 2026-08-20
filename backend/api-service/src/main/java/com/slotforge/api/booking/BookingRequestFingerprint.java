package com.slotforge.api.booking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class BookingRequestFingerprint {

    private static final String OPERATION_VERSION = "booking-create:v1";

    public String create(UUID sessionId, int quantity) {
        String canonicalRequest = OPERATION_VERSION
                + "|sessionId=" + sessionId
                + "|quantity=" + quantity;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
