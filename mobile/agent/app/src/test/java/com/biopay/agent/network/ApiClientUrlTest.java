package com.biopay.agent.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URI;

/**
 * BIOPAY_API_BASE_URL is intentionally machine-specific: it comes from backend/.env's
 * BIOPAY_MOBILE_API_BASE_URL (see app/build.gradle.kts), which developers point at the Android
 * emulator's 10.0.2.2 loopback for emulator work and at the machine's LAN IP for physical-device
 * testing. A test that hardcodes one of those hosts breaks every time a developer switches
 * targets without actually finding a bug, so this only checks the invariants ApiClient's own
 * callers rely on (a well-formed http(s) URL ending in /biopay), not a specific host.
 */
public class ApiClientUrlTest {

    @Test
    public void baseUrlIsAWellFormedBiopayEndpoint() {
        String baseUrl = ApiClient.getBaseUrl();
        URI uri = URI.create(baseUrl);

        assertTrue("scheme must be http or https, was: " + baseUrl,
                "http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
        assertFalse("host must not be blank: " + baseUrl, uri.getHost() == null || uri.getHost().isEmpty());
        assertFalse("must not end with a trailing slash: " + baseUrl, baseUrl.endsWith("/"));
        assertTrue("path must end with /biopay so callers can append sub-paths safely: " + baseUrl,
                uri.getPath() != null && uri.getPath().endsWith("/biopay"));
    }
}
