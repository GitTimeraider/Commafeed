package com.commafeed.security.network;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/** An IPv4 or IPv6 network in CIDR notation (e.g. 192.168.1.0/24), or a single address. */
public record IpNetwork(String definition, byte[] address, int prefixLength) {

    // only IP literals are accepted, never host names (which would trigger DNS lookups)
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]+$");

    public static IpNetwork parse(String definition) {
        String value = definition.trim();
        String addressPart = value;
        Integer prefix = null;

        int slash = value.indexOf('/');
        if (slash >= 0) {
            addressPart = value.substring(0, slash);
            try {
                prefix = Integer.parseInt(value.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid network prefix: " + definition);
            }
        }

        byte[] address = parseAddress(addressPart);
        if (address == null) {
            throw new IllegalArgumentException("invalid network address: " + definition);
        }

        int maxPrefix = address.length * 8;
        int prefixLength = prefix == null ? maxPrefix : prefix;
        if (prefixLength < 0 || prefixLength > maxPrefix) {
            throw new IllegalArgumentException("invalid network prefix: " + definition);
        }
        return new IpNetwork(value, address, prefixLength);
    }

    /**
     * @return the address bytes (4 for IPv4, 16 for IPv6), or null if this is not an IP literal
     */
    public static byte[] parseAddress(String value) {
        if (value == null) {
            return null;
        }
        String literal = value.trim();
        if (literal.startsWith("[") && literal.endsWith("]")) {
            literal = literal.substring(1, literal.length() - 1);
        }
        int zone = literal.indexOf('%');
        if (zone >= 0) {
            literal = literal.substring(0, zone);
        }
        if (literal.isEmpty() || !IP_LITERAL.matcher(literal).matches()) {
            return null;
        }
        try {
            // IPv4-mapped IPv6 addresses (::ffff:1.2.3.4) are returned as IPv4 addresses
            return InetAddress.getByName(literal).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public boolean contains(byte[] candidate) {
        if (candidate == null || candidate.length != address.length) {
            return false;
        }
        if (prefixLength == 0) {
            return true;
        }
        int shift = address.length * 8 - prefixLength;
        BigInteger mask =
                BigInteger.ONE.shiftLeft(prefixLength).subtract(BigInteger.ONE).shiftLeft(shift);
        return new BigInteger(1, candidate).and(mask).equals(new BigInteger(1, address).and(mask));
    }
}
