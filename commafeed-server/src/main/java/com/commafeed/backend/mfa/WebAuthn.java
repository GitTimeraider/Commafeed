package com.commafeed.backend.mfa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server side verification of WebAuthn (passkeys, security keys) registrations and assertions.
 *
 * <p>Only what's needed to use a passkey as a second factor is implemented: attestation statements
 * are not verified (the client requests "none" attestation), and the ES256, EdDSA and RS256
 * algorithms are supported.
 */
public final class WebAuthn {

    public static final int ALG_ES256 = -7;
    public static final int ALG_EDDSA = -8;
    public static final int ALG_RS256 = -257;
    public static final List<Integer> SUPPORTED_ALGORITHMS =
            List.of(ALG_ES256, ALG_EDDSA, ALG_RS256);

    private static final int FLAG_USER_PRESENT = 0x01;
    private static final int FLAG_ATTESTED_CREDENTIAL_DATA = 0x40;

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    // DER prefix of an Ed25519 X.509 SubjectPublicKeyInfo, followed by the 32 bytes of the key
    private static final byte[] ED25519_SPKI_PREFIX =
            HexFormat.of().parseHex("302a300506032b6570032100");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private WebAuthn() {}

    /**
     * verify the response of navigator.credentials.create()
     *
     * @param expectedChallenge base64url encoded challenge sent to the browser
     * @return the credential to store, bound to the host name of the page that created it
     */
    public static RegisteredCredential verifyRegistration(
            byte[] clientDataJson, byte[] attestationObject, String expectedChallenge)
            throws WebAuthnException {
        return verifyRegistration(clientDataJson, attestationObject, expectedChallenge, Set.of());
    }

    /**
     * verify the response of navigator.credentials.create()
     *
     * @param expectedChallenge base64url encoded challenge sent to the browser
     * @param allowedTopOrigins normalized origins of the pages allowed to embed the application in
     *     an iframe (see {@link #normalizeOrigin(String)})
     * @return the credential to store, bound to the host name of the page that created it
     */
    public static RegisteredCredential verifyRegistration(
            byte[] clientDataJson,
            byte[] attestationObject,
            String expectedChallenge,
            Set<String> allowedTopOrigins)
            throws WebAuthnException {
        String origin =
                verifyClientData(
                        clientDataJson, "webauthn.create", expectedChallenge, allowedTopOrigins);
        String rpId = getHost(origin);
        verifyOrigin(origin, rpId);

        Object decoded;
        try {
            decoded = Cbor.decode(attestationObject);
        } catch (IllegalArgumentException e) {
            throw new WebAuthnException("invalid attestation object: " + e.getMessage());
        }
        if (!(decoded instanceof Map<?, ?> attestation)
                || !(attestation.get("authData") instanceof byte[] authData)) {
            throw new WebAuthnException("invalid attestation object");
        }

        int flags = verifyAuthenticatorData(authData, rpId);
        if ((flags & FLAG_ATTESTED_CREDENTIAL_DATA) == 0) {
            throw new WebAuthnException("missing attested credential data");
        }

        // attested credential data: aaguid (16), credential id length (2), credential id, COSE key
        int position = 37 + 16;
        if (authData.length < position + 2) {
            throw new WebAuthnException("invalid authenticator data");
        }
        int credentialIdLength =
                ((authData[position] & 0xff) << 8) | (authData[position + 1] & 0xff);
        position += 2;
        if (credentialIdLength == 0 || authData.length < position + credentialIdLength) {
            throw new WebAuthnException("invalid credential id");
        }
        byte[] credentialId = Arrays.copyOfRange(authData, position, position + credentialIdLength);
        position += credentialIdLength;

        Object coseKey;
        try {
            coseKey = new Cbor(authData, position).read();
        } catch (IllegalArgumentException e) {
            throw new WebAuthnException("invalid credential public key: " + e.getMessage());
        }
        if (!(coseKey instanceof Map<?, ?> coseMap)) {
            throw new WebAuthnException("invalid credential public key");
        }

        int algorithm = toInt(coseMap.get(3L));
        PublicKey publicKey = parseCoseKey(coseMap, algorithm);
        return new RegisteredCredential(
                base64UrlEncode(credentialId),
                Base64.getEncoder().encodeToString(publicKey.getEncoded()),
                algorithm,
                readSignCount(authData),
                rpId);
    }

    /**
     * verify the response of navigator.credentials.get()
     *
     * @param expectedChallenge base64url encoded challenge sent to the browser
     * @return the new signature counter of the credential
     */
    public static long verifyAssertion(
            RegisteredCredential credential,
            byte[] clientDataJson,
            byte[] authenticatorData,
            byte[] signature,
            String expectedChallenge)
            throws WebAuthnException {
        return verifyAssertion(
                credential,
                clientDataJson,
                authenticatorData,
                signature,
                expectedChallenge,
                Set.of());
    }

    /**
     * verify the response of navigator.credentials.get()
     *
     * @param expectedChallenge base64url encoded challenge sent to the browser
     * @param allowedTopOrigins normalized origins of the pages allowed to embed the application in
     *     an iframe (see {@link #normalizeOrigin(String)})
     * @return the new signature counter of the credential
     */
    public static long verifyAssertion(
            RegisteredCredential credential,
            byte[] clientDataJson,
            byte[] authenticatorData,
            byte[] signature,
            String expectedChallenge,
            Set<String> allowedTopOrigins)
            throws WebAuthnException {
        String origin =
                verifyClientData(
                        clientDataJson, "webauthn.get", expectedChallenge, allowedTopOrigins);
        verifyOrigin(origin, credential.rpId());
        verifyAuthenticatorData(authenticatorData, credential.rpId());

        try {
            PublicKey publicKey =
                    KeyFactory.getInstance(keyAlgorithm(credential.algorithm()))
                            .generatePublic(
                                    new X509EncodedKeySpec(
                                            Base64.getDecoder().decode(credential.publicKey())));
            Signature verifier = Signature.getInstance(signatureAlgorithm(credential.algorithm()));
            verifier.initVerify(publicKey);
            verifier.update(authenticatorData);
            verifier.update(sha256(clientDataJson));
            if (!verifier.verify(signature)) {
                throw new WebAuthnException("invalid signature");
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new WebAuthnException("invalid signature: " + e.getMessage());
        }

        long signCount = readSignCount(authenticatorData);
        if ((signCount != 0 || credential.signCount() != 0)
                && signCount <= credential.signCount()) {
            // the counter must increase, otherwise the authenticator may have been cloned
            throw new WebAuthnException("invalid signature counter");
        }
        return signCount;
    }

    public static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] base64UrlDecode(String value) throws WebAuthnException {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new WebAuthnException("invalid base64url value");
        }
    }

    /**
     * @return the origin in its normalized form (lowercase scheme://host[:port], without the
     *     default port and without path)
     * @throws IllegalArgumentException if the value is not a valid http(s) origin
     */
    public static String normalizeOrigin(String origin) {
        String value = origin.trim();
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid origin: " + origin);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        boolean hasPath = uri.getRawPath() != null && !uri.getRawPath().isEmpty();
        if (!Set.of("http", "https").contains(scheme)
                || host == null
                || hasPath
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("invalid origin: " + origin);
        }

        int port = uri.getPort();
        boolean defaultPort =
                port == -1
                        || "http".equals(scheme) && port == 80
                        || "https".equals(scheme) && port == 443;
        return scheme + "://" + host.toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
    }

    /**
     * @return the origin of the client data
     */
    private static String verifyClientData(
            byte[] clientDataJson,
            String expectedType,
            String expectedChallenge,
            Set<String> allowedTopOrigins)
            throws WebAuthnException {
        JsonNode clientData;
        try {
            clientData = OBJECT_MAPPER.readTree(clientDataJson);
        } catch (IOException e) {
            throw new WebAuthnException("invalid client data");
        }
        if (clientData == null || !clientData.isObject()) {
            throw new WebAuthnException("invalid client data");
        }

        if (!expectedType.equals(clientData.path("type").asText())) {
            throw new WebAuthnException("unexpected client data type");
        }

        byte[] challenge = clientData.path("challenge").asText("").getBytes(StandardCharsets.UTF_8);
        if (expectedChallenge == null
                || !MessageDigest.isEqual(
                        challenge, expectedChallenge.getBytes(StandardCharsets.UTF_8))) {
            throw new WebAuthnException("unexpected challenge");
        }

        if (clientData.path("crossOrigin").asBoolean(false)) {
            // the application is embedded in an iframe of another origin, only accept it when the
            // top level page is explicitly trusted
            String topOrigin = clientData.path("topOrigin").asText("");
            if (topOrigin.isEmpty()) {
                throw new WebAuthnException(
                        "cross origin requests are not allowed (the browser did not send the"
                                + " origin of the top level page)");
            }
            String normalizedTopOrigin;
            try {
                normalizedTopOrigin = normalizeOrigin(topOrigin);
            } catch (IllegalArgumentException e) {
                throw new WebAuthnException("invalid top level origin");
            }
            if (!allowedTopOrigins.contains(normalizedTopOrigin)) {
                throw new WebAuthnException(
                        "cross origin requests are not allowed from " + normalizedTopOrigin);
            }
        }

        String origin = clientData.path("origin").asText("");
        if (origin.isEmpty()) {
            throw new WebAuthnException("missing origin");
        }
        return origin;
    }

    /** the origin must be secure and its host must match the relying party id */
    private static void verifyOrigin(String origin, String rpId) throws WebAuthnException {
        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException e) {
            throw new WebAuthnException("invalid origin");
        }

        String host = getHost(origin);
        boolean secure =
                "https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()) && LOCAL_HOSTS.contains(host);
        if (!secure) {
            throw new WebAuthnException("passkeys require HTTPS");
        }

        if (!host.equals(rpId) && !host.endsWith("." + rpId)) {
            throw new WebAuthnException("origin does not match the relying party");
        }
    }

    private static String getHost(String origin) throws WebAuthnException {
        try {
            String host = new URI(origin).getHost();
            if (host == null || host.isBlank()) {
                throw new WebAuthnException("invalid origin");
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            throw new WebAuthnException("invalid origin");
        }
    }

    /**
     * @return the flags of the authenticator data
     */
    private static int verifyAuthenticatorData(byte[] authData, String rpId)
            throws WebAuthnException {
        if (authData.length < 37) {
            throw new WebAuthnException("invalid authenticator data");
        }

        byte[] rpIdHash = Arrays.copyOfRange(authData, 0, 32);
        if (!MessageDigest.isEqual(rpIdHash, sha256(rpId.getBytes(StandardCharsets.UTF_8)))) {
            throw new WebAuthnException("relying party id does not match");
        }

        int flags = authData[32] & 0xff;
        if ((flags & FLAG_USER_PRESENT) == 0) {
            throw new WebAuthnException("user presence is required");
        }
        return flags;
    }

    private static long readSignCount(byte[] authData) {
        return ByteBuffer.wrap(authData, 33, 4).getInt() & 0xffffffffL;
    }

    private static PublicKey parseCoseKey(Map<?, ?> cose, int algorithm) throws WebAuthnException {
        try {
            return switch (algorithm) {
                case ALG_ES256 -> {
                    if (toInt(cose.get(1L)) != 2 || toInt(cose.get(-1L)) != 1) {
                        throw new WebAuthnException("unsupported EC key");
                    }
                    byte[] x = toBytes(cose.get(-2L));
                    byte[] y = toBytes(cose.get(-3L));
                    if (x.length != 32 || y.length != 32) {
                        throw new WebAuthnException("invalid EC key");
                    }
                    AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
                    parameters.init(new ECGenParameterSpec("secp256r1"));
                    ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
                    ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
                    yield KeyFactory.getInstance("EC")
                            .generatePublic(new ECPublicKeySpec(point, spec));
                }
                case ALG_EDDSA -> {
                    if (toInt(cose.get(1L)) != 1 || toInt(cose.get(-1L)) != 6) {
                        throw new WebAuthnException("unsupported OKP key");
                    }
                    byte[] x = toBytes(cose.get(-2L));
                    if (x.length != 32) {
                        throw new WebAuthnException("invalid Ed25519 key");
                    }
                    byte[] spki = new byte[ED25519_SPKI_PREFIX.length + x.length];
                    System.arraycopy(ED25519_SPKI_PREFIX, 0, spki, 0, ED25519_SPKI_PREFIX.length);
                    System.arraycopy(x, 0, spki, ED25519_SPKI_PREFIX.length, x.length);
                    yield KeyFactory.getInstance("Ed25519")
                            .generatePublic(new X509EncodedKeySpec(spki));
                }
                case ALG_RS256 -> {
                    if (toInt(cose.get(1L)) != 3) {
                        throw new WebAuthnException("unsupported RSA key");
                    }
                    BigInteger n = new BigInteger(1, toBytes(cose.get(-1L)));
                    BigInteger e = new BigInteger(1, toBytes(cose.get(-2L)));
                    yield KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
                }
                default -> throw new WebAuthnException("unsupported algorithm " + algorithm);
            };
        } catch (GeneralSecurityException e) {
            throw new WebAuthnException("invalid public key: " + e.getMessage());
        }
    }

    private static String keyAlgorithm(int algorithm) throws WebAuthnException {
        return switch (algorithm) {
            case ALG_ES256 -> "EC";
            case ALG_EDDSA -> "Ed25519";
            case ALG_RS256 -> "RSA";
            default -> throw new WebAuthnException("unsupported algorithm " + algorithm);
        };
    }

    private static String signatureAlgorithm(int algorithm) throws WebAuthnException {
        return switch (algorithm) {
            case ALG_ES256 -> "SHA256withECDSA";
            case ALG_EDDSA -> "Ed25519";
            case ALG_RS256 -> "SHA256withRSA";
            default -> throw new WebAuthnException("unsupported algorithm " + algorithm);
        };
    }

    private static int toInt(Object value) throws WebAuthnException {
        if (value instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return l.intValue();
        }
        throw new WebAuthnException("invalid COSE key");
    }

    private static byte[] toBytes(Object value) throws WebAuthnException {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        throw new WebAuthnException("invalid COSE key");
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public record RegisteredCredential(
            String credentialId, String publicKey, int algorithm, long signCount, String rpId) {}

    public static class WebAuthnException extends Exception {
        @java.io.Serial private static final long serialVersionUID = 1L;

        public WebAuthnException(String message) {
            super(message);
        }
    }
}
