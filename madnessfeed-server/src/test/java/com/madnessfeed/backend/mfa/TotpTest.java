package com.madnessfeed.backend.mfa;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

class TotpTest {

    // RFC 6238 test secret "12345678901234567890"
    private static final String RFC_SECRET =
            Totp.base32Encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void rfc6238TestVectors() {
        // last 6 digits of the RFC 6238 SHA1 test vectors
        Assertions.assertEquals("287082", Totp.generateCode(RFC_SECRET, 59 / 30));
        Assertions.assertEquals("081804", Totp.generateCode(RFC_SECRET, 1111111109L / 30));
        Assertions.assertEquals("050471", Totp.generateCode(RFC_SECRET, 1111111111L / 30));
        Assertions.assertEquals("005924", Totp.generateCode(RFC_SECRET, 1234567890L / 30));
        Assertions.assertEquals("279037", Totp.generateCode(RFC_SECRET, 2000000000L / 30));
    }

    @Test
    void base32() {
        Assertions.assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", RFC_SECRET);
        Assertions.assertArrayEquals(
                "12345678901234567890".getBytes(StandardCharsets.US_ASCII),
                Totp.base32Decode(RFC_SECRET));
        String secret = Totp.generateSecret();
        Assertions.assertEquals(secret, Totp.base32Encode(Totp.base32Decode(secret)));
    }

    @Test
    void verify() {
        Instant now = Instant.ofEpochSecond(1234567890L);
        long step = Totp.currentStep(now);

        Assertions.assertEquals(step, Totp.verify(RFC_SECRET, "005924", now, null).orElseThrow());
        Assertions.assertEquals(step, Totp.verify(RFC_SECRET, "005 924", now, null).orElseThrow());

        // previous and next steps are accepted
        String previous = Totp.generateCode(RFC_SECRET, step - 1);
        String next = Totp.generateCode(RFC_SECRET, step + 1);
        Assertions.assertTrue(Totp.verify(RFC_SECRET, previous, now, null).isPresent());
        Assertions.assertTrue(Totp.verify(RFC_SECRET, next, now, null).isPresent());

        // too old
        String old = Totp.generateCode(RFC_SECRET, step - 2);
        Assertions.assertTrue(Totp.verify(RFC_SECRET, old, now, null).isEmpty());

        // replay
        Assertions.assertTrue(Totp.verify(RFC_SECRET, "005924", now, step).isEmpty());

        // garbage
        Assertions.assertTrue(Totp.verify(RFC_SECRET, "abcdef", now, null).isEmpty());
        Assertions.assertTrue(Totp.verify(RFC_SECRET, "", now, null).isEmpty());
        Assertions.assertTrue(Totp.verify(RFC_SECRET, null, now, null).isEmpty());
    }

    @Test
    void uri() {
        Assertions.assertEquals(
                "otpauth://totp/MadnessFeed:john%20doe?secret=ABC&issuer=MadnessFeed&algorithm=SHA1&digits=6&period=30",
                Totp.buildUri("MadnessFeed", "john doe", "ABC"));
    }
}
