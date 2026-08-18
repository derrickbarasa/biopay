package com.biopay.agent.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ApiClientUrlTest {

    @Test
    public void normalizesPhysicalDeviceHost() {
        assertEquals(
                "http://192.168.100.163:7730/biopay",
                ApiClient.normalizeBaseUrl("192.168.100.163:7730"));
    }

    @Test
    public void preservesCompleteHttpsEndpointAndRemovesTrailingSlash() {
        assertEquals(
                "https://api.example.org/biopay",
                ApiClient.normalizeBaseUrl("https://api.example.org/biopay/"));
    }

    @Test
    public void rejectsBlankEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> ApiClient.normalizeBaseUrl("  "));
    }
}
