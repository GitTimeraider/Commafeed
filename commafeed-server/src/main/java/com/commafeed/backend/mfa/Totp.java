package com.commafeed.backend.mfa;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238) as used by authenticator apps: HMAC-SHA1, 6 digits, 30
 * seconds steps.
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;

    /** number of steps before and after the current one that are also accepted (clock drift) */
    private static final int ALLOWED_DRIFT_STEPS = 1;

    private static final int SECRET_LENGTH_BYTES = 20;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {}

    /**
     * @return a new random base32 encoded secret
     */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_LENGTH_BYTES];
        // not a static field: a SecureRandom must not be created at native image build time
        new SecureRandom().nextBytes(bytes);
        return base32Encode(bytes);
    }

    /**
     * @return the otpauth:// uri that authenticator apps can import, usually as a QR code
     */
    public static String buildUri(String issuer, String accountName, String secret) {
        String label = encode(issuer) + ":" + encode(accountName);
        return "otpauth://totp/"
                + label
                + "?secret="
                + secret
                + "&issuer="
                + encode(issuer)
                + "&algorithm=SHA1&digits="
                + DIGITS
                + "&period="
                + PERIOD_SECONDS;
    }

    public static long currentStep(Instant now) {
        return now.getEpochSecond() / PERIOD_SECONDS;
    }

    /**
     * @return the time step matching the given code if it is valid around the given time and newer
     *     than lastUsedStep, empty otherwise
     */
    public static OptionalLong verify(String secret, String code, Instant now, Long lastUsedStep) {
        if (secret == null || code == null) {
            return OptionalLong.empty();
        }

        String normalizedCode = code.replaceAll("\\s", "");
        if (normalizedCode.length() != DIGITS
                || !normalizedCode.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }

        byte[] key = base32Decode(secret);
        long current = currentStep(now);
        for (long step = current - ALLOWED_DRIFT_STEPS;
                step <= current + ALLOWED_DRIFT_STEPS;
                step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                // prevent replaying a code that was already used
                continue;
            }
            byte[] expected = generateCode(key, step).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(
                    expected, normalizedCode.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /**
     * @return the code for the given secret and time step
     */
    public static String generateCode(String secret, long step) {
        return generateCode(base32Decode(secret), step);
    }

    private static String generateCode(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());

            int offset = hash[hash.length - 1] & 0x0f;
            int binary =
                    ((hash[offset] & 0x7f) << 24)
                            | ((hash[offset + 1] & 0xff) << 16)
                            | ((hash[offset + 2] & 0xff) << 8)
                            | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32Encode(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String input) {
        String normalized = input.replace("=", "").replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : normalized.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("invalid base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
