package com.biopay.main;

import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import com.biopay.databases.Datasource;
import com.biopay.services.Auth;
import com.biopay.services.Audit;
import com.biopay.services.Approval;
import com.biopay.services.Administration;
import com.biopay.services.Biometric;
import com.biopay.services.Chat;
import com.biopay.services.Dashboard;
import com.biopay.services.Geography;
import com.biopay.services.Household;
import com.biopay.services.Notification;
import com.biopay.services.Officer;
import com.biopay.services.Organization;
import com.biopay.services.Payment;
import com.biopay.services.Payroll;
import com.biopay.services.Subscription;
import com.biopay.services.Voucher;
import com.biopay.utilities.AppReleaseStore;
import com.biopay.utilities.JwtSupport;
import com.biopay.utilities.Logging;
import com.biopay.utilities.PermissionPolicy;

/**
 * HTTP surface for biopay. Deliberately just a few routes:
 *
 * <ul>
 *   <li>{@code /biopay/authentication} -- unauthenticated. Only accepts the
 *       session-establishing processing codes (login, refresh).</li>
 *   <li>{@code /biopay/downloads/:filename} -- unauthenticated. Serves the
 *       Android agent APKs from {@link AppReleaseStore}.</li>
 *   <li>{@code /biopay/downloads/version/:filename} -- unauthenticated. Serves
 *       that APK's latest-version metadata, polled by the installed app.</li>
 *   <li>{@code /biopay/api/v1/req} -- JWT-protected. Every other processing
 *       code (organisations, officers, households, payments, payroll,
 *       dashboard, biometric sync) is dispatched from here by name over the
 *       Vert.x event bus.</li>
 * </ul>
 *
 * Adding an endpoint means adding an {@code eventBus.consumer(...)} in the
 * relevant service verticle, not a new route.
 */
public class EntryPoint extends AbstractVerticle {

    Dotenv dotenv = Dotenv.load();

    // Processing codes that stay reachable even when an anchor's subscription is
    // ARCHIVED, so the account can still see the situation and renew/manage itself.
    // Everything else routed through /api/v1/req is a gated data operation.
    private static final Set<String> SUBSCRIPTION_EXEMPT_CODES = Set.of(
            "GET_SUBSCRIPTION", "RENEW_SUBSCRIPTION", "GET_SUBSCRIPTION_INVOICES", "GET_SUBSCRIPTION_INVOICE_RECEIPT",
            "GET_SUBSCRIPTION_PRICE", "CREATE_SUBSCRIPTION_PAYMENT_REQUEST", "GET_SUBSCRIPTION_PAYMENT_REQUESTS",
            "ME", "LOGOUT", "CHANGE_PASSWORD", "UPDATE_PROFILE",
            "TOTP_SETUP_INIT", "TOTP_SETUP_CONFIRM", "TOTP_DISABLE", "EMAIL_OTP_ENABLE", "EMAIL_OTP_DISABLE",
            "GET_ORGANIZATION_MODULES");

    public static void main(String[] args) {
        VertxOptions vertxOpts = new VertxOptions()
                .setEventLoopPoolSize(4)
                .setWorkerPoolSize(200);

        Vertx vertx = Vertx.vertx(vertxOpts);

        // Initialise the reactive MSSQL pool BEFORE deploying any verticle.
        Datasource.init(vertx);

        DeploymentOptions options = new DeploymentOptions()
                .setInstances(1)
                .setThreadingModel(ThreadingModel.EVENT_LOOP)
                .setHa(true);

        deploy(vertx, EntryPoint.class.getName(), options);
        deploy(vertx, Auth.class.getName(), options);
        deploy(vertx, Audit.class.getName(), options);
        deploy(vertx, Organization.class.getName(), options);
        deploy(vertx, Officer.class.getName(), options);
        deploy(vertx, Household.class.getName(), options);
        deploy(vertx, Approval.class.getName(), options);
        deploy(vertx, Payroll.class.getName(), options);
        deploy(vertx, Payment.class.getName(), options);
        deploy(vertx, Dashboard.class.getName(), options);
        deploy(vertx, Biometric.class.getName(), options);
        deploy(vertx, Notification.class.getName(), options);
        deploy(vertx, Geography.class.getName(), options);
        deploy(vertx, Voucher.class.getName(), options);
        deploy(vertx, Subscription.class.getName(), options);
        deploy(vertx, Administration.class.getName(), options);
        deploy(vertx, Chat.class.getName(), options);
    }

    /** {@code deployVerticle} without an {@code onFailure} handler drops the failure entirely --
     *  a broken verticle (e.g. an exception thrown from its {@code start()}) silently never
     *  registers its event-bus consumers, and every request to it times out with no clue why. */
    private static void deploy(Vertx vertx, String verticleName, DeploymentOptions options) {
        vertx.deployVerticle(verticleName, options)
                .onFailure(err -> System.err.println("FAILED TO DEPLOY " + verticleName + ": " + err));
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId EntryPoint =" + vertx.getOrCreateContext().deploymentID());

        EventBus eventBus = vertx.eventBus();
        Router router = Router.router(vertx);

        JWTAuth authProvider = JwtSupport.init(vertx);

        final String allowedOriginRegex =
                "https?://localhost(:[0-9]+)?"
              + "|https?://127\\.0\\.0\\.1(:[0-9]+)?"
              + "|https://([a-z0-9-]+\\.)*biopay\\.africa";

        // ---- /biopay/authentication (public: login + refresh) -------------

        router.route("/biopay/authentication").handler(CorsHandler.create()
                .addOriginWithRegex(allowedOriginRegex)
                .allowedMethod(io.vertx.core.http.HttpMethod.POST)
                .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
                .allowCredentials(true)
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization")
                // The web dashboard's api/client.ts tags every request with this so its response
                // interceptor can tell which processingCode a failed call was for -- without it
                // allowlisted here, the browser's preflight rejects the header and login fails
                // with a CORS error before the request ever reaches this handler.
                .allowedHeader("X-Processing-Code"));

        router.route("/biopay/authentication").handler(ctx -> {
            HttpServerResponse response = ctx.response();
            response.putHeader("Content-Type", "application/json");
            String remoteAddress = ctx.request().remoteAddress().toString();

            ctx.request().bodyHandler(bodyHandler -> {
                try {
                    String body = bodyHandler.toString().trim();
                    if (body.isEmpty()) {
                        response.end(badRequest("Bad Request").toString());
                        return;
                    }
                    JsonObject data = new JsonObject(body);
                    data.put("ipAddress", remoteAddress);

                    String processingCode = data.getString("processingCode", "").trim();
                    String[] publicCodes = {
                            "LOGIN_USER", "LOGIN_SUPERVISOR", "REFRESH_TOKEN",
                            "REQUEST_LOGIN_OTP", "VERIFY_LOGIN_OTP", "SIGNUP_ANCHOR",
                            "REQUEST_PASSWORD_RESET", "RESET_PASSWORD",
                            "GET_EMAIL_APPROVAL", "CONFIRM_EMAIL_APPROVAL",
                            "API_TOKEN",
                    };
                    if (!Arrays.asList(publicCodes).contains(processingCode)) {
                        response.setStatusCode(401).end(new JsonObject()
                                .put("responseCode", "401")
                                .put("responseMessage", "Unauthorised")
                                .toString());
                        return;
                    }

                    dispatch(eventBus, processingCode, data, response);
                } catch (Exception ex) {
                    response.end(badRequest("Error occurred: " + ex.getMessage()).toString());
                }
            });
        });

        // ---- /biopay/downloads/:filename (public: Android agent APKs) -----
        // Unauthenticated on purpose -- a field officer needs to install the app before they can
        // ever sign in, and the download link is shared to devices directly (email, WhatsApp, a
        // QR code), never through the logged-in dashboard session.

        router.route("/biopay/downloads/*").handler(CorsHandler.create()
                .addOriginWithRegex(allowedOriginRegex)
                .allowedMethod(io.vertx.core.http.HttpMethod.GET)
                .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS));

        router.route("/biopay/downloads/:filename").handler(rtc -> {
            String filename = rtc.pathParam("filename");
            if (filename == null || !filename.matches("[A-Za-z0-9._-]+\\.apk")) {
                rtc.response().setStatusCode(400).end();
                return;
            }
            if (!AppReleaseStore.exists(filename)) {
                rtc.response().setStatusCode(404).end("This app build is not available yet. Please contact your administrator.");
                return;
            }
            try {
                byte[] bytes = AppReleaseStore.read(filename);
                rtc.response()
                        .putHeader("Content-Type", "application/vnd.android.package-archive")
                        .putHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                        .end(io.vertx.core.buffer.Buffer.buffer(bytes));
            } catch (Exception ex) {
                rtc.response().setStatusCode(404).end();
            }
        });

        // ---- /biopay/downloads/version/:filename (public: latest-version metadata) --------
        // Polled by the installed app itself (AppUpdateManager) to learn a newer build exists,
        // so it's unauthenticated for the same reason as the APK route above: it must work
        // before/without a session. `filename` is the APK's own name -- the sidecar it looks
        // up is that name plus ".version.json" (see AppReleaseStore). A missing sidecar just
        // means no release has been published with update metadata yet, not an error.

        router.route("/biopay/downloads/version/:filename").handler(rtc -> {
            String filename = rtc.pathParam("filename");
            if (filename == null || !filename.matches("[A-Za-z0-9._-]+\\.apk")) {
                rtc.response().setStatusCode(400).end();
                return;
            }
            if (!AppReleaseStore.versionInfoExists(filename)) {
                rtc.response().setStatusCode(404).end();
                return;
            }
            try {
                byte[] bytes = AppReleaseStore.readVersionInfo(filename);
                rtc.response().putHeader("Content-Type", "application/json")
                        .end(io.vertx.core.buffer.Buffer.buffer(bytes));
            } catch (Exception ex) {
                rtc.response().setStatusCode(404).end();
            }
        });

        // ---- /biopay/site/chat-stream (public: marketing-site chatbot) ----
        // Unauthenticated on purpose, same reasoning as /downloads -- an anonymous visitor has
        // no session to protect this with. Streamed as newline-delimited JSON chunks rather than
        // a single request/reply: a local LLM reply can run well past a normal request timeout,
        // and streaming keeps the visitor seeing progress instead of a long silent wait.

        router.route("/biopay/site/*").handler(CorsHandler.create()
                .addOriginWithRegex(allowedOriginRegex)
                .allowedMethod(io.vertx.core.http.HttpMethod.POST)
                .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
                .allowedHeader("Content-Type"));

        router.route("/biopay/site/chat-stream").handler(rtc -> {
            HttpServerResponse response = rtc.response();
            response.putHeader("Content-Type", "application/x-ndjson");
            response.setChunked(true);
            String remoteAddress = rtc.request().remoteAddress().toString();

            rtc.request().bodyHandler(bodyHandler -> {
                JsonObject data;
                try {
                    String body = bodyHandler.toString().trim();
                    data = body.isEmpty() ? new JsonObject() : new JsonObject(body);
                } catch (Exception ex) {
                    response.end(new JsonObject().put("done", true).put("error", true)
                            .put("message", "Bad Request").toString() + "\n");
                    return;
                }
                data.put("ipAddress", remoteAddress);

                String replyAddress = "chat.reply." + UUID.randomUUID();
                data.put("streamReplyAddress", replyAddress);

                MessageConsumer<Object> consumer = eventBus.consumer(replyAddress);
                consumer.handler(replyMessage -> {
                    String chunk = replyMessage.body().toString();
                    response.write(chunk + "\n");
                    JsonObject parsed;
                    try {
                        parsed = new JsonObject(chunk);
                    } catch (Exception ex) {
                        parsed = new JsonObject();
                    }
                    if (parsed.getBoolean("done", false)) {
                        consumer.unregister();
                        response.end();
                    }
                });
                // A visitor closing the tab mid-reply would otherwise leak this consumer forever --
                // it has no other way to ever receive its {"done": true} unregister trigger.
                rtc.request().connection().closeHandler(v -> consumer.unregister());

                eventBus.send("SEND_SITE_CHAT_MESSAGE_STREAM", data.toString());
            });
        });

        // ---- /biopay/api/v1/req (JWT-protected: everything else) ----------

        router.route("/biopay/api/v1/*").handler(CorsHandler.create()
                .addOriginWithRegex(allowedOriginRegex)
                .allowedMethod(io.vertx.core.http.HttpMethod.GET)
                .allowedMethod(io.vertx.core.http.HttpMethod.POST)
                .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
                .allowCredentials(true)
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization")
                .allowedHeader("X-Processing-Code")
                .exposedHeader("Content-Disposition"));

        router.route("/biopay/api/v1/*").handler(JWTAuthHandler.create(authProvider))
                .failureHandler(frc -> {
                    int statusCode = frc.statusCode() > 0 ? frc.statusCode() : 401;
                    HttpServerResponse response = frc.response();
                    response.putHeader("Content-Type", "application/json");
                    response.setStatusCode(statusCode).end(new JsonObject()
                            .put("responseCode", String.valueOf(statusCode))
                            .put("responseMessage", frc.failure() != null ? frc.failure().getMessage() : "Unauthorised")
                            .toString());
                });

        router.route("/biopay/api/v1/req").handler(rtc -> {
            HttpServerResponse response = rtc.response();
            response.putHeader("Content-Type", "application/json");
            String remoteAddress = rtc.request().remoteAddress().toString();

            // The JWTAuthHandler above already verified signature + expiry;
            // its decoded claims are the source of truth for who's calling,
            // NOT anything the client puts in the request body.
            JsonObject principal = rtc.user().principal();

            rtc.request().bodyHandler(bodyHandler -> {
                try {
                    String body = bodyHandler.toString().trim();
                    JsonObject data = body.isEmpty() ? new JsonObject() : new JsonObject(body);
                    boolean systemOwner = Boolean.TRUE.equals(principal.getValue("systemAdmin"));
                    Object sessionAnchorId = principal.getValue("anchorId");
                    Object requestedAnchorId = data.getValue("targetAnchorId");
                    data.put("ipAddress", remoteAddress);
                    data.put("actorId", principal.getValue("sub"));
                    data.put("actorRole", principal.getString("role"));
                    data.put("sessionAnchorId", sessionAnchorId);
                    data.put("anchorId", systemOwner && requestedAnchorId != null ? requestedAnchorId : sessionAnchorId);
                    data.put("partnerCode", principal.getValue("partnerCode"));
                    data.put("systemAdmin", systemOwner);
                    data.put("channel", principal.getString("channel", "PORTAL"));

                    String processingCode = data.getString("processingCode", "").trim();
                    if (processingCode.isEmpty()) {
                        response.end(badRequest("processingCode is required").toString());
                        return;
                    }

                    if (systemOwner && requestedAnchorId != null) {
                        validateTargetAnchorAndDispatch(eventBus, processingCode, data, response, requestedAnchorId);
                    } else {
                        authorizeAndDispatch(eventBus, processingCode, data, response);
                    }
                } catch (Exception ex) {
                    response.end(badRequest("Error occurred: " + ex.getMessage()).toString());
                }
            });
        });

        // ---- /biopay/api/v1/chat-stream (JWT-protected: dashboard chatbot) ------
        // Scoped exactly like /api/v1/req above: actorId/actorRole/anchorId/partnerCode/
        // systemAdmin all come from the verified JWT's own claims, never from anything the
        // client puts in the request body. Streamed for the same reason as the site chatbot --
        // a local LLM reply can run well past a normal request timeout. A system owner's
        // targetAnchorId is re-validated against the anchors table, same as /api/v1/req's
        // validateTargetAnchorAndDispatch.

        router.route("/biopay/api/v1/chat-stream").handler(rtc -> {
            HttpServerResponse response = rtc.response();
            String remoteAddress = rtc.request().remoteAddress().toString();
            JsonObject principal = rtc.user().principal();

            rtc.request().bodyHandler(bodyHandler -> {
                JsonObject data;
                try {
                    String body = bodyHandler.toString().trim();
                    data = body.isEmpty() ? new JsonObject() : new JsonObject(body);
                } catch (Exception ex) {
                    response.putHeader("Content-Type", "application/json");
                    response.setStatusCode(400).end(badRequest("Bad Request").toString());
                    return;
                }
                boolean systemOwner = Boolean.TRUE.equals(principal.getValue("systemAdmin"));
                Object sessionAnchorId = principal.getValue("anchorId");
                Object requestedAnchorId = data.getValue("targetAnchorId");
                data.put("ipAddress", remoteAddress);
                data.put("actorId", principal.getValue("sub"));
                data.put("actorRole", principal.getString("role"));
                data.put("partnerCode", principal.getValue("partnerCode"));
                data.put("systemAdmin", systemOwner);
                data.put("channel", principal.getString("channel", "PORTAL"));

                if (systemOwner && requestedAnchorId != null) {
                    validateTargetAnchorAndStartChatStream(eventBus, rtc, response, data, requestedAnchorId);
                } else {
                    data.put("anchorId", sessionAnchorId);
                    startDashboardChatStream(eventBus, rtc, response, data);
                }
            });
        });

        // Uploaded beneficiary images: served only through this authenticated route
        // (never a direct file path), keyed by the randomized filename FileStore
        // generated at upload time.
        router.route("/biopay/api/v1/files/:filename").handler(rtc -> {
            String filename = rtc.pathParam("filename");
            if (filename == null || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
                rtc.response().setStatusCode(400).end();
                return;
            }
            JsonObject principal = rtc.user().principal();
            boolean systemOwner = Boolean.TRUE.equals(principal.getValue("systemAdmin"));
            String role = principal.getString("role", "");
            Object anchorValue = principal.getValue("anchorId");
            Integer anchorId = anchorValue == null ? null : Integer.parseInt(anchorValue.toString());
            String partnerCode = principal.getString("partnerCode");
            String scopeSql = "SELECT TOP 1 1 AS allowed FROM images i "
                    + "LEFT JOIN organizations p ON p.organization_code=i.organization_code "
                    + "WHERE i.photo_url=@p1 AND (@p2=1 OR "
                    + "(@p3='ANCHOR' AND p.anchor_id=@p4) OR "
                    + "(@p3<>'ANCHOR' AND i.organization_code=@p5))";
            Datasource.pool().preparedQuery(scopeSql)
                    .execute(Tuple.of(filename, systemOwner, role, anchorId, partnerCode))
                    .onFailure(error -> rtc.response().setStatusCode(503).end())
                    .onSuccess(rows -> {
                        if (rows.size() == 0) {
                            rtc.response().setStatusCode(404).end();
                            return;
                        }
                        try {
                            byte[] bytes = com.biopay.utilities.FileStore.read(filename);
                            String contentType = filename.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
                            rtc.response().putHeader("Content-Type", contentType)
                                    .end(io.vertx.core.buffer.Buffer.buffer(bytes));
                        } catch (Exception ex) {
                            rtc.response().setStatusCode(404).end();
                        }
                    });
        });

        int port = Integer.parseInt(dotenv.get("SYSTEM_PORT", "7730"));
        String host = dotenv.get("SYSTEM_HOST", "0.0.0.0");

        vertx.createHttpServer().requestHandler(router).listen(port, host, resp -> {
            if (resp.succeeded()) {
                System.out.println("biopay listening at http://" + host + ":" + port);
                startPromise.complete();
            } else {
                System.out.println("biopay failed to start !! " + resp.cause());
                startPromise.fail(resp.cause());
            }
        });
    }

    /** A system owner may select a tenant for almost every operational request. Validate that
     * selection once at the HTTP boundary: ANCHOR is also a user access scope, so scope alone
     * does not identify the self-referencing users row that represents the tenant itself. */
    private static void validateTargetAnchorAndDispatch(EventBus eventBus, String processingCode, JsonObject data,
            HttpServerResponse response, Object requestedAnchorId) {
        final int targetAnchorId;
        try {
            targetAnchorId = Integer.parseInt(requestedAnchorId.toString());
        } catch (NumberFormatException ex) {
            response.setStatusCode(400).end(badRequest("targetAnchorId must identify an anchor").toString());
            return;
        }
        Datasource.pool().preparedQuery("SELECT 1 AS allowed FROM anchors WHERE id=@p1")
                .execute(Tuple.of(targetAnchorId))
                .onFailure(error -> response.setStatusCode(503).end(new JsonObject()
                        .put("responseCode", "503").put("responseMessage", "Unable to verify the selected anchor").toString()))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        response.setStatusCode(403).end(new JsonObject()
                                .put("responseCode", "403")
                                .put("responseMessage", "The selected account is not an anchor").toString());
                    } else {
                        authorizeAndDispatch(eventBus, processingCode, data, response);
                    }
                });
    }

    /** Same validation as {@link #validateTargetAnchorAndDispatch}, for the dashboard chatbot's
     *  own streaming route rather than the generic processingCode dispatch path. */
    private static void validateTargetAnchorAndStartChatStream(EventBus eventBus, RoutingContext rtc,
            HttpServerResponse response, JsonObject data, Object requestedAnchorId) {
        final int targetAnchorId;
        try {
            targetAnchorId = Integer.parseInt(requestedAnchorId.toString());
        } catch (NumberFormatException ex) {
            response.putHeader("Content-Type", "application/json");
            response.setStatusCode(400).end(badRequest("targetAnchorId must identify an anchor").toString());
            return;
        }
        Datasource.pool().preparedQuery("SELECT 1 AS allowed FROM anchors WHERE id=@p1")
                .execute(Tuple.of(targetAnchorId))
                .onFailure(error -> {
                    response.putHeader("Content-Type", "application/json");
                    response.setStatusCode(503).end(new JsonObject()
                            .put("responseCode", "503").put("responseMessage", "Unable to verify the selected anchor").toString());
                })
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        response.putHeader("Content-Type", "application/json");
                        response.setStatusCode(403).end(new JsonObject()
                                .put("responseCode", "403")
                                .put("responseMessage", "The selected account is not an anchor").toString());
                    } else {
                        data.put("anchorId", targetAnchorId);
                        startDashboardChatStream(eventBus, rtc, response, data);
                    }
                });
    }

    /** Shared streaming plumbing for the dashboard chatbot route, once {@code data.anchorId} is
     *  known-good: registers an ephemeral event-bus consumer for the reply, forwards each chunk
     *  as one ndjson line, and unregisters on {@code done} or an early client disconnect. */
    private static void startDashboardChatStream(EventBus eventBus, RoutingContext rtc,
            HttpServerResponse response, JsonObject data) {
        response.putHeader("Content-Type", "application/x-ndjson");
        response.setChunked(true);

        String replyAddress = "chat.reply." + UUID.randomUUID();
        data.put("streamReplyAddress", replyAddress);

        MessageConsumer<Object> consumer = eventBus.consumer(replyAddress);
        consumer.handler(replyMessage -> {
            String chunk = replyMessage.body().toString();
            response.write(chunk + "\n");
            JsonObject parsed;
            try {
                parsed = new JsonObject(chunk);
            } catch (Exception ex) {
                parsed = new JsonObject();
            }
            if (parsed.getBoolean("done", false)) {
                consumer.unregister();
                response.end();
            }
        });
        rtc.request().connection().closeHandler(v -> consumer.unregister());

        eventBus.send("SEND_DASHBOARD_CHAT_MESSAGE_STREAM", data.toString());
    }

    private static void authorizeAndDispatch(EventBus eventBus, String processingCode, JsonObject data,
            HttpServerResponse response) {
        String actorRole = data.getString("actorRole", "");
        // getString(key, def) only substitutes def when the key is entirely absent -- an explicit
        // JSON null (e.g. a cleared Vuetify select, or a form field never shown for the caller's
        // role) still comes back null and would NPE on isBlank() below, so this can't skip the
        // strOrEmpty-style guard the rest of the codebase applies to every other optional field.
        String requestedOrganisationRaw = data.getString("organisationCode");
        String requestedOrganisation = requestedOrganisationRaw == null ? "" : requestedOrganisationRaw;
        if (!data.getBoolean("systemAdmin", false) && !requestedOrganisation.isBlank()
                && !"CREATE_ORGANIZATION".equals(processingCode)) {
            if ("ORGANISATION".equalsIgnoreCase(actorRole)
                    && !requestedOrganisation.equals(data.getString("partnerCode", ""))) {
                tenantForbidden(response);
                return;
            }
            if ("ANCHOR".equalsIgnoreCase(actorRole)) {
                Object anchorId = data.getValue("anchorId");
                if (anchorId == null) { tenantForbidden(response); return; }
                Datasource.pool().preparedQuery("SELECT 1 AS allowed FROM organizations WHERE organization_code=@p1 AND anchor_id=@p2")
                        .execute(Tuple.of(requestedOrganisation, Integer.parseInt(anchorId.toString())))
                        .onFailure(error -> response.setStatusCode(503).end(new JsonObject()
                                .put("responseCode", "503").put("responseMessage", "Unable to verify tenant scope").toString()))
                        .onSuccess(rows -> {
                            if (rows.size() == 0) tenantForbidden(response);
                            else authorizePermissionsAndDispatch(eventBus, processingCode, data, response);
                        });
                return;
            }
        }
        authorizePermissionsAndDispatch(eventBus, processingCode, data, response);
    }

    private static void authorizePermissionsAndDispatch(EventBus eventBus, String processingCode, JsonObject data,
            HttpServerResponse response) {
        Set<String> requiredPermissions = PermissionPolicy.requiredPermissions(processingCode, data);
        if (requiredPermissions.isEmpty() || data.getBoolean("systemAdmin", false)
                || "SUPERVISOR".equalsIgnoreCase(data.getString("actorRole", ""))) {
            dispatchGated(eventBus, processingCode, data, response);
            return;
        }

        Object actorId = data.getValue("actorId");
        if (actorId == null) {
            forbidden(response, requiredPermissions);
            return;
        }
        String sql = "SELECT p.permission_name FROM users u "
                + "JOIN role_permissions rp ON rp.role_id=u.role_id AND rp.status=1 "
                + "JOIN permissions p ON p.id=rp.permission_id "
                + "WHERE u.id=@p1 AND u.active=1 AND u.status=1";
        Datasource.pool().preparedQuery(sql).execute(Tuple.of(Integer.parseInt(actorId.toString())))
                .onFailure(error -> response.setStatusCode(503).end(new JsonObject()
                        .put("responseCode", "503")
                        .put("responseMessage", "Unable to verify role permissions")
                        .toString()))
                .onSuccess(rows -> {
                    Set<String> granted = new HashSet<>();
                    rows.forEach(row -> granted.add(row.getString("permission_name")));
                    if (requiredPermissions.stream().noneMatch(granted::contains)) forbidden(response, requiredPermissions);
                    else dispatchGated(eventBus, processingCode, data, response);
                });
    }

    private static void tenantForbidden(HttpServerResponse response) {
        response.setStatusCode(403).end(new JsonObject()
                .put("responseCode", "403")
                .put("responseMessage", "The selected organisation is outside your assigned anchor")
                .toString());
    }

    private static void forbidden(HttpServerResponse response, Set<String> requiredPermissions) {
        response.setStatusCode(403).end(new JsonObject()
                .put("responseCode", "403")
                .put("responseMessage", "Your role does not allow this action")
                .put("requiredPermission", requiredPermissions.iterator().next())
                .toString());
    }

    /**
     * Subscription gate in front of {@link #dispatch}: when the caller's anchor is
     * ARCHIVED, SUSPENDED or CANCELLED, every data operation is refused with a 402
     * until they renew. Exempt codes ({@link #SUBSCRIPTION_EXEMPT_CODES}) and callers
     * with no anchor pass straight through, and any status other than ARCHIVED --
     * including a failed lookup, which resolves to NONE -- fails open so a transient
     * DB issue can never lock the whole platform out.
     */
    private static void dispatchGated(EventBus eventBus, String processingCode, JsonObject data,
            HttpServerResponse response) {
        if (SUBSCRIPTION_EXEMPT_CODES.contains(processingCode) || data.getBoolean("systemAdmin", false)) {
            dispatch(eventBus, processingCode, data, response);
            return;
        }
        Integer anchorId = null;
        Object anchorIdVal = data.getValue("anchorId");
        if (anchorIdVal != null) {
            try {
                anchorId = Integer.parseInt(anchorIdVal.toString());
            } catch (NumberFormatException ignored) {
                anchorId = null;
            }
        }
        if (anchorId == null) {
            dispatch(eventBus, processingCode, data, response);
            return;
        }
        final Integer gatedAnchorId = anchorId;
        Subscription.anchorActiveFor(Datasource.pool(), gatedAnchorId).onComplete(activeAr -> {
            if (activeAr.succeeded() && !activeAr.result()) {
                response.setStatusCode(423).end(new JsonObject()
                        .put("responseCode", "423")
                        .put("responseMessage", "This anchor has been deactivated. Contact your anchor.")
                        .toString());
                return;
            }
            Subscription.statusFor(Datasource.pool(), gatedAnchorId).onComplete(ar -> {
                if (ar.succeeded() && Set.of("ARCHIVED", "SUSPENDED", "CANCELLED").contains(ar.result())) {
                    String state = ar.result();
                    String message = "SUSPENDED".equals(state)
                            ? "Subscription suspended. Contact BioPay to restore access."
                            : "CANCELLED".equals(state)
                                    ? "Subscription cancelled. Renew to restore access."
                                    : "Subscription expired. Renew to restore access.";
                    response.setStatusCode(402).end(new JsonObject()
                            .put("responseCode", "402")
                            .put("responseMessage", message)
                            .toString());
                } else {
                    dispatch(eventBus, processingCode, data, response);
                }
            });
        });
    }

    private static void dispatch(EventBus eventBus, String processingCode, JsonObject data,
            HttpServerResponse response) {
        DeliveryOptions deliveryOptions = new DeliveryOptions().setSendTimeout(20000);
        eventBus.request(processingCode, data.toString(), deliveryOptions, sendToBus -> {
            if (sendToBus.succeeded()) {
                String responseBody = sendToBus.result().body().toString().trim();
                auditAuthenticatedActivity(processingCode, data, responseBody);
                response.end(responseBody);
            } else {
                Logging.applicationLog(Logging.logPreString() + "555-->" + processingCode
                        + " Fail. " + sendToBus.cause().getLocalizedMessage() + "\n\n", "", 3);
                response.end(new JsonObject()
                        .put("responseCode", "555")
                        .put("responseMessage", "System failed to process this request")
                        .toString());
            }
        });
    }

    /** Records meaningful dashboard changes centrally, so every service gets the same audit
     * coverage. Login and field-app business events are already recorded by Auth/Biometric. */
    private static void auditAuthenticatedActivity(String processingCode, JsonObject data, String responseBody) {
        String role = data.getString("actorRole", "");
        if ("SUPERVISOR".equalsIgnoreCase(role) || !isAuditableActivity(processingCode)) return;
        Object actorId = data.getValue("actorId");
        if (actorId == null) return;

        JsonObject result;
        try { result = new JsonObject(responseBody); }
        catch (Exception ignored) { result = new JsonObject(); }

        String requestedOrganization = data.getString("organisationCode");
        String organizationCode = requestedOrganization == null || requestedOrganization.isBlank()
                ? data.getString("partnerCode") : requestedOrganization;
        String actorType = data.getBoolean("systemAdmin", false) ? "SYSTEM"
                : "ORGANISATION".equalsIgnoreCase(role) ? "ORGANISATION_USER" : "ANCHOR_USER";
        JsonObject details = new JsonObject()
                .put("outcome", "000".equals(result.getString("responseCode")) ? "SUCCESS" : "FAILED")
                .put("responseMessage", result.getString("responseMessage"));
        for (String key : new String[] { "userId", "email", "organisationCode", "householdNumber",
                "cycleCode", "invoiceNumber", "type", "code", "status" }) {
            if (data.getValue(key) != null) details.put(key, data.getValue(key));
        }
        String entityId = firstNonBlank(data, "userId", "organisationCode", "householdNumber",
                "cycleCode", "invoiceNumber", "email", "code");
        String entityType = data.getValue("userId") != null ? "USER"
                : data.getValue("organisationCode") != null ? "ORGANIZATION"
                : data.getValue("householdNumber") != null ? "HOUSEHOLD"
                : data.getValue("cycleCode") != null ? "PAYMENT_CYCLE" : null;

        String sql = "INSERT INTO audit_logs (actor_type,actor_id,anchor_id,organization_code,action,"
                + "entity_type,entity_id,details,ip_address,channel,created_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,GETDATE())";
        Datasource.pool().preparedQuery(sql).execute(Tuple.of(actorType,
                        Integer.parseInt(actorId.toString()), integerOrNull(data.getValue("anchorId")),
                        organizationCode, processingCode, entityType, entityId, details.encode(),
                        data.getString("ipAddress"), data.getString("channel", "PORTAL")))
                .onFailure(error -> Logging.applicationLog(Logging.logPreString()
                        + "Audit write failed: " + error.getMessage() + "\n\n", "", 3));
    }

    private static boolean isAuditableActivity(String code) {
        return code.startsWith("CREATE_") || code.startsWith("UPDATE_") || code.startsWith("DELETE_")
                || code.startsWith("TOGGLE_") || code.startsWith("SET_") || code.startsWith("ASSIGN_")
                || code.startsWith("APPROVE_") || code.startsWith("REJECT_") || code.startsWith("DISBURSE_")
                || code.startsWith("VOID_") || code.startsWith("REDEEM_") || code.startsWith("BULK_")
                || Set.of("CHANGE_PASSWORD", "UPDATE_PROFILE", "TOTP_SETUP_CONFIRM", "TOTP_DISABLE",
                        "EMAIL_OTP_ENABLE", "EMAIL_OTP_DISABLE", "PAY_PAYMENT_ONLINE", "LOGOUT").contains(code);
    }

    private static String firstNonBlank(JsonObject data, String... keys) {
        for (String key : keys) {
            Object value = data.getValue(key);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }

    private static Integer integerOrNull(Object value) {
        if (value == null) return null;
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static JsonObject badRequest(String message) {
        return new JsonObject().put("responseCode", "901").put("responseMessage", message);
    }
}
