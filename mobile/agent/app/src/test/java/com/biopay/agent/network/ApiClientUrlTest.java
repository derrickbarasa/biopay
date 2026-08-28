package com.biopay.agent.network;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ApiClientUrlTest {

    @Test
    public void usesConfiguredEmulatorEndpoint() {
        assertEquals("http://10.0.2.2:7730/biopay", ApiClient.getBaseUrl());
    }
}
