package com.commafeed.security.network;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IpNetworkTest {

    @Test
    void ipv4() {
        IpNetwork network = IpNetwork.parse("192.168.1.0/24");
        Assertions.assertTrue(network.contains(IpNetwork.parseAddress("192.168.1.1")));
        Assertions.assertTrue(network.contains(IpNetwork.parseAddress("192.168.1.255")));
        Assertions.assertFalse(network.contains(IpNetwork.parseAddress("192.168.2.1")));
        Assertions.assertFalse(network.contains(IpNetwork.parseAddress("10.0.0.1")));

        // IPv4-mapped IPv6 address
        Assertions.assertTrue(network.contains(IpNetwork.parseAddress("::ffff:192.168.1.7")));

        IpNetwork single = IpNetwork.parse("10.1.2.3");
        Assertions.assertTrue(single.contains(IpNetwork.parseAddress("10.1.2.3")));
        Assertions.assertFalse(single.contains(IpNetwork.parseAddress("10.1.2.4")));

        Assertions.assertTrue(
                IpNetwork.parse("0.0.0.0/0").contains(IpNetwork.parseAddress("8.8.8.8")));
        Assertions.assertTrue(
                IpNetwork.parse("172.16.0.0/12").contains(IpNetwork.parseAddress("172.31.255.1")));
        Assertions.assertFalse(
                IpNetwork.parse("172.16.0.0/12").contains(IpNetwork.parseAddress("172.32.0.1")));
    }

    @Test
    void ipv6() {
        IpNetwork network = IpNetwork.parse("fd00::/8");
        Assertions.assertTrue(network.contains(IpNetwork.parseAddress("fd12:3456::1")));
        Assertions.assertTrue(network.contains(IpNetwork.parseAddress("[fd12:3456::1]")));
        Assertions.assertFalse(network.contains(IpNetwork.parseAddress("2001:db8::1")));
        Assertions.assertFalse(network.contains(IpNetwork.parseAddress("10.0.0.1")));
    }

    @Test
    void invalid() {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> IpNetwork.parse("192.168.1.0/33"));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> IpNetwork.parse("192.168.1.0/x"));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> IpNetwork.parse("example.com"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> IpNetwork.parse("999.1.1.1"));
        Assertions.assertNull(IpNetwork.parseAddress("localhost"));
        Assertions.assertNull(IpNetwork.parseAddress(null));
    }

    @Test
    void publicPaths() {
        Assertions.assertTrue(NetworkAccessFilter.isPublicPath("/"));
        Assertions.assertTrue(NetworkAccessFilter.isPublicPath("/index.html"));
        Assertions.assertTrue(NetworkAccessFilter.isPublicPath("/assets/index-abc123.js"));
        Assertions.assertTrue(NetworkAccessFilter.isPublicPath("/app-icon-192.png"));
        Assertions.assertTrue(NetworkAccessFilter.isPublicPath("/rest/server/get"));
        Assertions.assertTrue(NetworkAccessFilter.isPublicPath("/rest/public/token/tree"));

        Assertions.assertFalse(NetworkAccessFilter.isPublicPath("/j_security_check"));
        Assertions.assertFalse(NetworkAccessFilter.isPublicPath("/rest/user/profile"));
        Assertions.assertFalse(NetworkAccessFilter.isPublicPath("/rest/server/proxy"));
        Assertions.assertFalse(NetworkAccessFilter.isPublicPath("/rest/fever/user/1"));
        Assertions.assertFalse(NetworkAccessFilter.isPublicPath("/ws"));
        Assertions.assertFalse(NetworkAccessFilter.isPublicPath("/openapi"));
        Assertions.assertFalse(NetworkAccessFilter.isPublicPath("/custom_css.css"));
        Assertions.assertFalse(NetworkAccessFilter.isPublicPath("/rest/public/../user/profile"));
        Assertions.assertFalse(NetworkAccessFilter.isPublicPath("/assets/../rest/user/profile"));
        Assertions.assertFalse(NetworkAccessFilter.isPublicPath(null));
    }
}
