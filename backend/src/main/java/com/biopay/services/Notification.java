package com.biopay.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import com.biopay.utilities.Logging;

/**
 * Fire-and-forget notification sink. No real SMTP/SMS provider is
 * configured for this environment (no credentials were supplied), so
 * outbound messages are logged rather than dropped silently -- wiring a
 * real provider means implementing {@link #sendEmail} against it; every
 * caller already goes through the "EMAIL" event bus address, so nothing
 * else changes.
 */
public class Notification extends AbstractVerticle {

    EventBus eventBus;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Notification =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        eventBus.consumer("EMAIL", this::sendEmail);
        startPromise.complete();
    }

    private void sendEmail(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Logging.applicationLog(Logging.logPreString() + "EMAIL to=" + payload.getString("mailTo")
                + " subject=" + payload.getString("subject") + " body=" + payload.getString("msg") + "\n\n", "", 5);
    }
}
