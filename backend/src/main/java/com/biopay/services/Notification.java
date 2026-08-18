package com.biopay.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import com.biopay.utilities.Logging;

/** Sends transactional email through ZeptoMail without exposing message bodies in logs. */
public class Notification extends AbstractVerticle {

    EventBus eventBus;
    ZeptoMailClient mailClient;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Notification =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        try {
            mailClient = ZeptoMailClient.fromEnvironment(io.github.cdimascio.dotenv.Dotenv.load());
        } catch (Exception ex) {
            Logging.applicationLog(Logging.logPreString() + "EMAIL CONFIGURATION ERROR: "
                    + ex.getMessage() + "\n\n", "", 3);
        }
        eventBus.consumer("EMAIL", this::sendEmail);
        startPromise.complete();
    }

    private void sendEmail(Message<Object> message) {
        JsonObject payload = message.body() instanceof JsonObject
                ? (JsonObject) message.body()
                : new JsonObject(message.body().toString());
        String recipient = payload.getString("mailTo", "").trim();
        String subject = payload.getString("subject", "").trim();
        String html = payload.getString("msg", "");

        if (mailClient == null) {
            Logging.applicationLog(Logging.logPreString()
                    + "EMAIL NOT SENT: ZeptoMail is not configured; recipient=" + recipient + "\n\n", "", 3);
            if (message.replyAddress() != null) {
                message.fail(503, "Email delivery is not configured");
            }
            return;
        }

        try {
            mailClient.send(recipient, subject, html).whenComplete((ignored, error) -> {
                if (error == null) {
                    Logging.applicationLog(Logging.logPreString() + "EMAIL SENT recipient=" + recipient
                            + " subject=" + subject + "\n\n", "", 2);
                    if (message.replyAddress() != null) {
                        message.reply(new JsonObject().put("delivered", true));
                    }
                } else {
                    Logging.applicationLog(Logging.logPreString() + "EMAIL DELIVERY FAILED recipient="
                            + recipient + " error=" + rootMessage(error) + "\n\n", "", 3);
                    if (message.replyAddress() != null) {
                        message.fail(502, "Email delivery failed");
                    }
                }
            });
        } catch (Exception ex) {
            Logging.applicationLog(Logging.logPreString() + "EMAIL REQUEST REJECTED recipient="
                    + recipient + " error=" + ex.getMessage() + "\n\n", "", 3);
            if (message.replyAddress() != null) {
                message.fail(400, "Invalid email request");
            }
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
