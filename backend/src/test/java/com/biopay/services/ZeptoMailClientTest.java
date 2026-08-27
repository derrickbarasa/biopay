package com.biopay.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.json.JsonArray;
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

    @Test
    void attachesInlineImagesWhenPresent() {
        JsonArray images = new JsonArray().add(new JsonObject()
                .put("cid", "logo").put("mime_type", "image/png").put("content", "base64=="));

        JsonObject withImages = ZeptoMailClient.payload(
                "noreply@example.org", "BioPay", "person@example.org", "Subject", "Body", images);
        assertEquals(images, withImages.getJsonArray("inline_images"));

        JsonObject withoutImages = ZeptoMailClient.payload(
                "noreply@example.org", "BioPay", "person@example.org", "Subject", "Body", new JsonArray());
        assertFalse(withoutImages.containsKey("inline_images"));

        assertTrue(withImages.containsKey("inline_images"));
    }
}
