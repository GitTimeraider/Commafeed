package com.commafeed.backend.mfa;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Software WebAuthn authenticator (ES256) used to test passkey registration and login */
public class TestAuthenticator {

    private final KeyPair keyPair;
    private final byte[] credentialId;
    private int signCount;

    public TestAuthenticator() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            this.keyPair = generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        this.credentialId = new byte[16];
        new SecureRandom().nextBytes(credentialId);
    }

    public String credentialId() {
        return b64(credentialId);
    }

    public static String clientData(String type, String challenge, String origin) {
        String json =
                "{\"type\":\"%s\",\"challenge\":\"%s\",\"origin\":\"%s\",\"crossOrigin\":false}"
                        .formatted(type, challenge, origin);
        return b64(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return the base64url encoded attestationObject ("none" attestation)
     */
    public String attestationObject(String rpId) {
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        Map<Object, Object> cose = new LinkedHashMap<>();
        cose.put(1L, 2L);
        cose.put(3L, -7L);
        cose.put(-1L, 1L);
        cose.put(-2L, unsigned32(publicKey.getW().getAffineX()));
        cose.put(-3L, unsigned32(publicKey.getW().getAffineY()));

        ByteArrayOutputStream authData = new ByteArrayOutputStream();
        authData.writeBytes(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
        authData.write(0x01 | 0x40); // user present, attested credential data
        authData.writeBytes(ByteBuffer.allocate(4).putInt(signCount).array());
        authData.writeBytes(new byte[16]); // aaguid
        authData.write(credentialId.length >> 8);
        authData.write(credentialId.length & 0xff);
        authData.writeBytes(credentialId);
        authData.writeBytes(encode(cose));

        Map<Object, Object> attestation = new LinkedHashMap<>();
        attestation.put("fmt", "none");
        attestation.put("attStmt", new LinkedHashMap<>());
        attestation.put("authData", authData.toByteArray());
        return b64(encode(attestation));
    }

    /**
     * @return the JSON sent by the login page after navigator.credentials.get()
     */
    public String assertionJson(String rpId, String challenge, String origin) {
        signCount++;
        ByteArrayOutputStream authData = new ByteArrayOutputStream();
        authData.writeBytes(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
        authData.write(0x01);
        authData.writeBytes(ByteBuffer.allocate(4).putInt(signCount).array());

        String clientData = clientData("webauthn.get", challenge, origin);
        byte[] signature;
        try {
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(authData.toByteArray());
            signer.update(sha256(Base64.getUrlDecoder().decode(clientData)));
            signature = signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }

        return "{\"id\":\"%s\",\"clientDataJSON\":\"%s\",\"authenticatorData\":\"%s\",\"signature\":\"%s\"}"
                .formatted(credentialId(), clientData, b64(authData.toByteArray()), b64(signature));
    }

    public static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] result = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, result, 32 - length, length);
        return result;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // minimal CBOR encoder for the types used above
    private static byte[] encode(Object value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, value);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, Object value) {
        switch (value) {
            case Long l when l >= 0 -> header(out, 0, l);
            case Long l -> header(out, 1, -1 - l);
            case byte[] bytes -> {
                header(out, 2, bytes.length);
                out.writeBytes(bytes);
            }
            case String s -> {
                byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                header(out, 3, bytes.length);
                out.writeBytes(bytes);
            }
            case Map<?, ?> map -> {
                header(out, 5, map.size());
                map.forEach(
                        (k, v) -> {
                            write(out, k);
                            write(out, v);
                        });
            }
            default -> throw new IllegalArgumentException("unsupported type " + value);
        }
    }

    private static void header(ByteArrayOutputStream out, int majorType, long length) {
        if (length < 24) {
            out.write((majorType << 5) | (int) length);
        } else if (length < 256) {
            out.write((majorType << 5) | 24);
            out.write((int) length);
        } else {
            out.write((majorType << 5) | 25);
            out.write((int) (length >> 8));
            out.write((int) (length & 0xff));
        }
    }
}
