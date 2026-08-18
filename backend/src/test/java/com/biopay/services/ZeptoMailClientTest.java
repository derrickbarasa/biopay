package com.biopay.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

class ZeptoMailClientTest {

    @Test
    void createsZeptoMailPayloadWithoutLosingHtml() {
        JsonObject payload = ZeptoMailClient.payload(
                "noreply@example.org", "BioPay", "person@example.org",
                "Verification code", "Your code is <strong>123456</strong>");

        assertEquals("noreply@example.org", payload.getJsonObject("from").getString("address"));
        assertEquals("BioPay", payload.getJsonObject("from").getString("name"));
        assertEquals("person@example.org", payload.getJsonArray("to").getJsonObject(0)
                .getJsonObject("email_address").getString("address"));
        assertEquals("Your code is <strong>123456</strong>", payload.getString("htmlbody"));
    }

    @Test
    void normalizesRawSendMailToken() {
        assertEquals("Zoho-enczapikey secret", ZeptoMailClient.authorizationValue("secret"));
        assertEquals("Zoho-enczapikey secret",
                ZeptoMailClient.authorizationValue("Zoho-enczapikey secret"));
    }

    @Test
    void rejectsInvalidRecipient() {
        assertThrows(IllegalArgumentException.class, () ->
                ZeptoMailClient.payload("noreply@example.org", "BioPay", "invalid", "Subject", "Body"));
    }
}
