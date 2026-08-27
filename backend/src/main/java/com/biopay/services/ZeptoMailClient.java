package com.biopay.services;

import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Minimal ZeptoMail HTTPS client. Configuration is read from environment/.env only. */
final class ZeptoMailClient {

    private static final String DEFAULT_URL = "https://api.zeptomail.com/v1.1/email";
    private final HttpClient http;
    private final URI endpoint;
    private final String authorization;
    private final String fromAddress;
    private final String fromName;

    ZeptoMailClient(HttpClient http, URI endpoint, String token, String fromAddress, String fromName) {
        this.http = http;
        this.endpoint = endpoint;
        this.authorization = authorizationValue(token);
        this.fromAddress = required(fromAddress, "MAIL_FROM (or ZEPTO_MAIL_FROM)");
        this.fromName = fromName == null || fromName.isBlank() ? "BioPay" : fromName.trim();
    }

    static ZeptoMailClient fromEnvironment(Dotenv env) {
        String from = firstNonBlank(env.get("ZEPTO_MAIL_FROM"), env.get("MAIL_FROM"));
        return new ZeptoMailClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                URI.create(env.get("ZEPTOMAIL_API_URL", DEFAULT_URL)),
                env.get("ZEPTOMAIL_API_TOKEN"),
                from,
                env.get("MAIL_FROM_NAME", "BioPay"));
    }

    CompletableFuture<Void> send(String recipient, String subject, String html) {
        return send(recipient, subject, html, null);
    }

    /** {@code inlineImages}, when non-empty, is ZeptoMail's {@code inline_images} array shape
     *  verbatim ({@code cid}/{@code mime_type}/{@code content} per entry) -- referenced from
     *  {@code html} via {@code <img src="cid:...">}, not linked or data-URI, since neither of
     *  those survive Gmail's/Outlook.com's HTML sanitizer on the way into an inbox. */
    CompletableFuture<Void> send(String recipient, String subject, String html, JsonArray inlineImages) {
        JsonObject body = payload(fromAddress, fromName, recipient, subject, html, inlineImages);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(body.encode()))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("ZeptoMail returned HTTP " + response.statusCode());
                    }
                    return null;
                });
    }

    static JsonObject payload(String from, String fromName, String recipient, String subject, String html) {
        return payload(from, fromName, recipient, subject, html, null);
    }

    static JsonObject payload(String from, String fromName, String recipient, String subject, String html,
            JsonArray inlineImages) {
        String to = required(recipient, "mailTo");
        if (!to.contains("@")) {
            throw new IllegalArgumentException("mailTo is not a valid email address");
        }
        JsonObject body = new JsonObject()
                .put("from", new JsonObject().put("address", required(from, "MAIL_FROM")).put("name", fromName))
                .put("to", new JsonArray().add(new JsonObject().put("email_address",
                        new JsonObject().put("address", to))))
                .put("subject", required(subject, "subject"))
                .put("htmlbody", required(html, "msg"));
        if (inlineImages != null && !inlineImages.isEmpty()) {
            body.put("inline_images", inlineImages);
        }
        return body;
    }

    static String authorizationValue(String token) {
        String value = required(token, "ZEPTOMAIL_API_TOKEN");
        return value.toLowerCase(java.util.Locale.ROOT).startsWith("zoho-enczapikey ")
                ? value
                : "Zoho-enczapikey " + value;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
