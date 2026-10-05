package com.madnessfeed.security.network;

import com.madnessfeed.MadnessFeedConfiguration;

import jakarta.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.List;

/**
 * Restricts the application to clients from the networks configured in
 * madnessfeed.allowed-networks. Clients from other networks can only use the public pages.
 */
@Slf4j
@Singleton
public class NetworkAccessService {

    private static final List<IpNetwork> LOOPBACK =
            List.of(IpNetwork.parse("127.0.0.0/8"), IpNetwork.parse("::1/128"));

    private final List<IpNetwork> allowedNetworks;

    public NetworkAccessService(MadnessFeedConfiguration config) {
        this.allowedNetworks =
                config.allowedNetworks().orElse(List.of()).stream()
                        .filter(s -> !s.isBlank())
                        .map(IpNetwork::parse)
                        .toList();
        if (!allowedNetworks.isEmpty()) {
            log.info(
                    "access restricted to {} (and localhost), other networks can only open public pages",
                    allowedNetworks.stream().map(IpNetwork::definition).toList());
            warnIfForwardedHeadersAreTrustedFromAnywhere();
        }
    }

    public boolean isEnabled() {
        return !allowedNetworks.isEmpty();
    }

    /**
     * @return true if the client with the given IP address may only access the public pages
     */
    public boolean isRestricted(String clientAddress) {
        if (!isEnabled()) {
            return false;
        }

        byte[] address = IpNetwork.parseAddress(clientAddress);
        if (address == null) {
            // unknown address (e.g. unix socket): be safe
            return true;
        }
        boolean allowed =
                LOOPBACK.stream().anyMatch(n -> n.contains(address))
                        || allowedNetworks.stream().anyMatch(n -> n.contains(address));
        return !allowed;
    }

    private static void warnIfForwardedHeadersAreTrustedFromAnywhere() {
        Config quarkusConfig = ConfigProvider.getConfig();
        boolean forwarding =
                quarkusConfig
                        .getOptionalValue(
                                "quarkus.http.proxy.proxy-address-forwarding", Boolean.class)
                        .orElse(false);
        boolean trustedProxies =
                quarkusConfig
                        .getOptionalValue("quarkus.http.proxy.trusted-proxies", String.class)
                        .filter(v -> !v.isBlank())
                        .isPresent();
        if (forwarding && !trustedProxies) {
            log.warn(
                    "quarkus.http.proxy.proxy-address-forwarding is enabled but"
                            + " quarkus.http.proxy.trusted-proxies is not set: any client can fake its address"
                            + " with a forwarded header and bypass madnessfeed.allowed-networks. Set"
                            + " quarkus.http.proxy.trusted-proxies to the address of your reverse proxy.");
        }
    }
}
