package com.commafeed.backend.mfa;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal CBOR (RFC 8949) decoder, sufficient for the WebAuthn attestation objects and COSE keys.
 *
 * <p>Integers are decoded as {@link Long}, byte strings as byte[], text strings as {@link String},
 * arrays as {@link List} and maps as {@link Map}. Indefinite lengths, tags and floats are not
 * supported since WebAuthn requires the CTAP2 canonical encoding which does not use them.
 */
final class Cbor {

    private static final int MAX_DEPTH = 16;

    private final byte[] data;
    private int position;

    Cbor(byte[] data, int offset) {
        this.data = data;
        this.position = offset;
    }

    /** decode a single item that must span the whole input */
    static Object decode(byte[] data) {
        Cbor cbor = new Cbor(data, 0);
        Object result = cbor.read();
        if (cbor.position != data.length) {
            throw new IllegalArgumentException("unexpected trailing CBOR data");
        }
        return result;
    }

    int position() {
        return position;
    }

    Object read() {
        return read(0);
    }

    private Object read(int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("CBOR data is too deeply nested");
        }

        int initial = nextByte();
        int majorType = initial >> 5;
        int additional = initial & 0x1f;

        return switch (majorType) {
            case 0 -> readLength(additional);
            case 1 -> -1 - readLength(additional);
            case 2 -> readBytes(toInt(readLength(additional)));
            case 3 -> new String(readBytes(toInt(readLength(additional))), StandardCharsets.UTF_8);
            case 4 -> {
                int size = toInt(readLength(additional));
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    list.add(read(depth + 1));
                }
                yield list;
            }
            case 5 -> {
                int size = toInt(readLength(additional));
                Map<Object, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) {
                    Object key = read(depth + 1);
                    map.put(key, read(depth + 1));
                }
                yield map;
            }
            case 7 ->
                    switch (additional) {
                        case 20 -> false;
                        case 21 -> true;
                        case 22, 23 -> null;
                        default ->
                                throw new IllegalArgumentException("unsupported CBOR simple value");
                    };
            default ->
                    throw new IllegalArgumentException("unsupported CBOR major type " + majorType);
        };
    }

    private long readLength(int additional) {
        if (additional < 24) {
            return additional;
        }
        int bytes =
                switch (additional) {
                    case 24 -> 1;
                    case 25 -> 2;
                    case 26 -> 4;
                    case 27 -> 8;
                    default -> throw new IllegalArgumentException("unsupported CBOR length");
                };
        byte[] raw = readBytes(bytes);
        BigInteger value = new BigInteger(1, raw);
        if (value.bitLength() > 63) {
            throw new IllegalArgumentException("CBOR integer is too large");
        }
        return value.longValue();
    }

    private int nextByte() {
        if (position >= data.length) {
            throw new IllegalArgumentException("unexpected end of CBOR data");
        }
        return data[position++] & 0xff;
    }

    private byte[] readBytes(int length) {
        if (length < 0 || length > data.length - position) {
            throw new IllegalArgumentException("unexpected end of CBOR data");
        }
        byte[] result = new byte[length];
        System.arraycopy(data, position, result, 0, length);
        position += length;
        return result;
    }

    private static int toInt(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("CBOR length is too large");
        }
        return (int) value;
    }
}
