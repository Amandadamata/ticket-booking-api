package com.amandadamata.ticketing.booking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** A request to hold seats, already validated by the web layer. */
public record HoldCommand(long eventId, String customerEmail, int quantity) {

    public HoldCommand {
        customerEmail = customerEmail.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * SHA-256 of what the request asks for. A retry with the same Idempotency-Key must ask for
     * exactly the same thing; otherwise the key is being reused by mistake.
     */
    String fingerprint() {
        String canonical = eventId + "|" + customerEmail + "|" + quantity;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", impossible);
        }
    }
}
