package com.commafeed.backend.service;

import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.dao.UserPasskeyDAO;
import com.commafeed.backend.mfa.Totp;
import com.commafeed.backend.mfa.WebAuthn;
import com.commafeed.backend.mfa.WebAuthn.RegisteredCredential;
import com.commafeed.backend.mfa.WebAuthn.WebAuthnException;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserPasskey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import jakarta.inject.Singleton;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two-factor authentication: authenticator apps (TOTP) and passkeys (WebAuthn).
 *
 * <p>The secrets are stored in the database. Short-lived state (challenges, codes being set up,
 * failed attempts, reset codes) is kept in memory.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class MfaService {

    public static final String ISSUER = "CommaFeed";
    public static final int MAX_FAILED_ATTEMPTS = 10;
    public static final Duration RESET_CODE_VALIDITY = Duration.ofMinutes(15);
    private static final int MAX_RESET_CODE_ATTEMPTS = 5;
    private static final String RESET_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserDAO userDAO;
    private final UserPasskeyDAO userPasskeyDAO;

    // not static: a SecureRandom must not be created at native image build time
    private final SecureRandom random = new SecureRandom();

    // user id -> TOTP secret being set up
    private final Cache<Long, String> pendingTotpSecrets =
            CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(15)).build();

    // user id -> challenge of the passkey being registered
    private final Cache<Long, String> pendingRegistrationChallenges =
            CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build();

    // challenge -> user id, for passkey logins
    private final Cache<String, Long> pendingLoginChallenges =
            CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build();

    // user id -> reset code
    private final Cache<Long, ResetCode> resetCodes =
            CacheBuilder.newBuilder().expireAfterWrite(RESET_CODE_VALIDITY).build();

    // user id -> failed second factor attempts
    private final Cache<Long, AtomicInteger> failedAttempts =
            CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(15)).build();

    public boolean isMfaEnabled(User user) {
        return user.getTotpSecret() != null || userPasskeyDAO.count(user) > 0;
    }

    // --- authenticator app

    public TotpSetup startTotpSetup(User user) {
        String secret = Totp.generateSecret();
        pendingTotpSecrets.put(user.getId(), secret);
        return new TotpSetup(secret, Totp.buildUri(ISSUER, user.getName(), secret));
    }

    /**
     * @return true if the code matches the secret being set up and the authenticator app is now
     *     enabled
     */
    public boolean enableTotp(User user, String code) {
        String secret = pendingTotpSecrets.getIfPresent(user.getId());
        if (secret == null) {
            return false;
        }

        OptionalLong step = Totp.verify(secret, code, Instant.now(), null);
        if (step.isEmpty()) {
            return false;
        }

        pendingTotpSecrets.invalidate(user.getId());
        user.setTotpSecret(secret);
        user.setTotpLastUsedStep(step.getAsLong());
        userDAO.merge(user);
        log.info("authenticator app enabled for user {}", user.getName());
        return true;
    }

    public void disableTotp(User user) {
        user.setTotpSecret(null);
        user.setTotpLastUsedStep(null);
        userDAO.merge(user);
        log.info("authenticator app disabled for user {}", user.getName());
    }

    // --- passkeys

    public List<UserPasskey> getPasskeys(User user) {
        return userPasskeyDAO.findAll(user);
    }

    public PasskeyRegistrationOptions startPasskeyRegistration(User user) {
        String challenge = randomChallenge();
        pendingRegistrationChallenges.put(user.getId(), challenge);
        return new PasskeyRegistrationOptions(
                challenge,
                ISSUER,
                WebAuthn.base64UrlEncode(
                        ByteBuffer.allocate(Long.BYTES).putLong(user.getId()).array()),
                user.getName(),
                WebAuthn.SUPPORTED_ALGORITHMS,
                userPasskeyDAO.findAll(user).stream().map(UserPasskey::getCredentialId).toList());
    }

    public UserPasskey finishPasskeyRegistration(
            User user, String name, String clientDataJson, String attestationObject)
            throws WebAuthnException {
        String challenge = pendingRegistrationChallenges.getIfPresent(user.getId());
        if (challenge == null) {
            throw new WebAuthnException("the registration expired, please try again");
        }
        pendingRegistrationChallenges.invalidate(user.getId());

        RegisteredCredential credential =
                WebAuthn.verifyRegistration(
                        WebAuthn.base64UrlDecode(clientDataJson),
                        WebAuthn.base64UrlDecode(attestationObject),
                        challenge);

        boolean alreadyRegistered =
                userPasskeyDAO.findAll(user).stream()
                        .anyMatch(p -> p.getCredentialId().equals(credential.credentialId()));
        if (alreadyRegistered) {
            throw new WebAuthnException("this passkey is already registered");
        }

        UserPasskey passkey = new UserPasskey();
        passkey.setUser(user);
        passkey.setName(
                StringUtils.defaultIfBlank(
                        StringUtils.left(StringUtils.trim(name), 128), "Passkey"));
        passkey.setCredentialId(credential.credentialId());
        passkey.setPublicKey(credential.publicKey());
        passkey.setAlgorithm(credential.algorithm());
        passkey.setSignCount(credential.signCount());
        passkey.setRpId(credential.rpId());
        passkey.setCreated(Instant.now());
        userPasskeyDAO.persist(passkey);
        log.info("passkey '{}' added for user {}", passkey.getName(), user.getName());
        return passkey;
    }

    /**
     * @return true if the passkey existed and was deleted
     */
    public boolean deletePasskey(User user, Long passkeyId) {
        Optional<UserPasskey> passkey =
                userPasskeyDAO.findAll(user).stream()
                        .filter(p -> Objects.equals(p.getId(), passkeyId))
                        .findFirst();
        passkey.ifPresent(
                p -> {
                    userPasskeyDAO.delete(p);
                    log.info("passkey '{}' deleted for user {}", p.getName(), user.getName());
                });
        return passkey.isPresent();
    }

    // --- login

    /** called after the password of the user has been verified, before the second factor */
    public LoginOptions getLoginOptions(User user) {
        List<String> credentialIds =
                userPasskeyDAO.findAll(user).stream().map(UserPasskey::getCredentialId).toList();
        String challenge = null;
        if (!credentialIds.isEmpty()) {
            challenge = randomChallenge();
            pendingLoginChallenges.put(challenge, user.getId());
        }
        return new LoginOptions(
                user.getTotpSecret() != null, !credentialIds.isEmpty(), challenge, credentialIds);
    }

    /**
     * verify the second factor of a user whose password has already been verified. Exactly one of
     * the parameters is expected.
     *
     * @param totpCode code of the authenticator app
     * @param passkeyAssertion JSON with the base64url encoded id, clientDataJSON, authenticatorData
     *     and signature returned by navigator.credentials.get()
     * @param resetCode code written in the server logs, disables two-factor authentication
     */
    public LoginResult verifyLogin(
            User user, String totpCode, String passkeyAssertion, String resetCode) {
        AtomicInteger failures;
        try {
            failures = failedAttempts.get(user.getId(), AtomicInteger::new);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
        if (failures.get() >= MAX_FAILED_ATTEMPTS) {
            return LoginResult.TOO_MANY_ATTEMPTS;
        }

        boolean success;
        if (StringUtils.isNotBlank(resetCode)) {
            success = consumeResetCode(user, resetCode);
            if (success) {
                disableAll(user);
                log.warn(
                        "two-factor authentication of user {} has been disabled with a reset code",
                        user.getName());
            }
        } else if (StringUtils.isNotBlank(totpCode)) {
            success = verifyTotpLogin(user, totpCode);
        } else if (StringUtils.isNotBlank(passkeyAssertion)) {
            success = verifyPasskeyLogin(user, passkeyAssertion);
        } else {
            return LoginResult.MISSING;
        }

        if (!success) {
            int count = failures.incrementAndGet();
            log.warn(
                    "failed two-factor authentication attempt for user {} ({}/{})",
                    user.getName(),
                    count,
                    MAX_FAILED_ATTEMPTS);
            return LoginResult.INVALID;
        }

        failedAttempts.invalidate(user.getId());
        return LoginResult.SUCCESS;
    }

    /**
     * generate a single use code that disables two-factor authentication when used on the login
     * page. The code is only written in the server logs, so that only the administrator of the
     * server can see it.
     */
    public void requestResetCode(User user) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                code.append('-');
            }
            code.append(RESET_CODE_ALPHABET.charAt(random.nextInt(RESET_CODE_ALPHABET.length())));
        }
        resetCodes.put(
                user.getId(), new ResetCode(hashResetCode(code.toString()), new AtomicInteger()));

        String separator = "=".repeat(78);
        log.warn(
                "\n{}\nTWO-FACTOR AUTHENTICATION RESET requested for user '{}'\nReset code: {}\n"
                        + "Valid for {} minutes. Enter it on the login page to disable two-factor"
                        + " authentication for this user.\nIgnore this message if you did not request it.\n{}",
                separator,
                user.getName(),
                code,
                RESET_CODE_VALIDITY.toMinutes(),
                separator);
    }

    /**
     * forget all short-lived state (challenges, codes being set up, failed attempts, reset codes)
     */
    public void clearTemporaryState() {
        pendingTotpSecrets.invalidateAll();
        pendingRegistrationChallenges.invalidateAll();
        pendingLoginChallenges.invalidateAll();
        resetCodes.invalidateAll();
        failedAttempts.invalidateAll();
    }

    public void disableAll(User user) {
        user.setTotpSecret(null);
        user.setTotpLastUsedStep(null);
        userDAO.merge(user);
        userPasskeyDAO.delete(userPasskeyDAO.findAll(user));
        pendingTotpSecrets.invalidate(user.getId());
        pendingRegistrationChallenges.invalidate(user.getId());
    }

    private boolean verifyTotpLogin(User user, String code) {
        OptionalLong step =
                Totp.verify(user.getTotpSecret(), code, Instant.now(), user.getTotpLastUsedStep());
        if (step.isEmpty()) {
            return false;
        }
        user.setTotpLastUsedStep(step.getAsLong());
        userDAO.merge(user);
        return true;
    }

    private boolean verifyPasskeyLogin(User user, String assertionJson) {
        try {
            JsonNode assertion = OBJECT_MAPPER.readTree(assertionJson);
            if (assertion == null || !assertion.isObject()) {
                return false;
            }

            byte[] clientDataJson =
                    WebAuthn.base64UrlDecode(assertion.path("clientDataJSON").asText());
            String challenge = OBJECT_MAPPER.readTree(clientDataJson).path("challenge").asText("");
            Long challengeUserId = pendingLoginChallenges.getIfPresent(challenge);
            if (challengeUserId == null || !challengeUserId.equals(user.getId())) {
                return false;
            }
            pendingLoginChallenges.invalidate(challenge);

            String credentialId = assertion.path("id").asText();
            Optional<UserPasskey> passkey =
                    userPasskeyDAO.findAll(user).stream()
                            .filter(p -> p.getCredentialId().equals(credentialId))
                            .findFirst();
            if (passkey.isEmpty()) {
                return false;
            }

            UserPasskey p = passkey.get();
            long signCount =
                    WebAuthn.verifyAssertion(
                            new RegisteredCredential(
                                    p.getCredentialId(),
                                    p.getPublicKey(),
                                    p.getAlgorithm(),
                                    p.getSignCount(),
                                    p.getRpId()),
                            clientDataJson,
                            WebAuthn.base64UrlDecode(assertion.path("authenticatorData").asText()),
                            WebAuthn.base64UrlDecode(assertion.path("signature").asText()),
                            challenge);
            p.setSignCount(signCount);
            p.setLastUsed(Instant.now());
            userPasskeyDAO.merge(p);
            return true;
        } catch (IOException | WebAuthnException e) {
            log.debug(
                    "passkey verification failed for user {}: {}", user.getName(), e.getMessage());
            return false;
        }
    }

    private boolean consumeResetCode(User user, String code) {
        ResetCode resetCode = resetCodes.getIfPresent(user.getId());
        if (resetCode == null) {
            return false;
        }

        if (resetCode.attempts().incrementAndGet() > MAX_RESET_CODE_ATTEMPTS) {
            resetCodes.invalidate(user.getId());
            return false;
        }

        if (!MessageDigest.isEqual(resetCode.hash(), hashResetCode(code))) {
            return false;
        }

        resetCodes.invalidate(user.getId());
        return true;
    }

    private static byte[] hashResetCode(String code) {
        String normalized = code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String randomChallenge() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return WebAuthn.base64UrlEncode(bytes);
    }

    public enum LoginResult {
        SUCCESS,
        MISSING,
        INVALID,
        TOO_MANY_ATTEMPTS
    }

    public record TotpSetup(String secret, String uri) {}

    public record PasskeyRegistrationOptions(
            String challenge,
            String rpName,
            String userId,
            String userName,
            List<Integer> algorithms,
            List<String> excludeCredentialIds) {}

    public record LoginOptions(
            boolean totp,
            boolean passkey,
            String passkeyChallenge,
            List<String> passkeyCredentialIds) {}

    private record ResetCode(byte[] hash, AtomicInteger attempts) {}
}
