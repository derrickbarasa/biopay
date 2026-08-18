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
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import java.util.Arrays;
import java.util.Set;
import com.biopay.databases.Datasource;
import com.biopay.services.Auth;
import com.biopay.services.Administration;
import com.biopay.services.Biometric;
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
import com.biopay.utilities.JwtSupport;
import com.biopay.utilities.Logging;

/**
 * HTTP surface for biopay. Deliberately just two routes:
 *
 * <ul>
 *   <li>{@code /biopay/authentication} -- unauthenticated. Only accepts the
 *       session-establishing processing codes (login, refresh).</li>
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
            "GET_SUBSCRIPTION", "RENEW_SUBSCRIPTION",
            "ME", "LOGOUT", "CHANGE_PASSWORD", "UPDATE_PROFILE",
            "TOTP_SETUP_INIT", "TOTP_SETUP_CONFIRM", "TOTP_DISABLE",
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

        vertx.deployVerticle(EntryPoint.class.getName(), options);
        vertx.deployVerticle(Auth.class.getName(), options);
        vertx.deployVerticle(Organization.class.getName(), options);
        vertx.deployVerticle(Officer.class.getName(), options);
        vertx.deployVerticle(Household.class.getName(), options);
        vertx.deployVerticle(Payroll.class.getName(), options);
        vertx.deployVerticle(Payment.class.getName(), options);
        vertx.deployVerticle(Dashboard.class.getName(), options);
        vertx.deployVerticle(Biometric.class.getName(), options);
        vertx.deployVerticle(Notification.class.getName(), options);
        vertx.deployVerticle(Geography.class.getName(), options);
        vertx.deployVerticle(Voucher.class.getName(), options);
        vertx.deployVerticle(Subscription.class.getName(), options);
        vertx.deployVerticle(Administration.class.getName(), options);
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
              + "|https://([a-z0-9-]+\\.)*biopay\\.app";

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
                    data.put("ipAddress", remoteAddress);
                    data.put("actorId", principal.getValue("sub"));
                    data.put("actorRole", principal.getString("role"));
                    data.put("anchorId", principal.getValue("anchorId"));
                    data.put("partnerCode", principal.getValue("partnerCode"));

                    String processingCode = data.getString("processingCode", "").trim();
                    if (processingCode.isEmpty()) {
                        response.end(badRequest("processingCode is required").toString());
                        return;
                    }

                    dispatchGated(eventBus, processingCode, data, response);
                } catch (Exception ex) {
                    response.end(badRequest("Error occurred: " + ex.getMessage()).toString());
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
            try {
                byte[] bytes = com.biopay.utilities.FileStore.read(filename);
                String contentType = filename.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
                rtc.response().putHeader("Content-Type", contentType).end(io.vertx.core.buffer.Buffer.buffer(bytes));
            } catch (Exception ex) {
                rtc.response().setStatusCode(404).end();
            }
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

    /**
     * Subscription gate in front of {@link #dispatch}: when the caller's anchor is
     * ARCHIVED (grace period exhausted), every data operation is refused with a 402
     * until they renew. Exempt codes ({@link #SUBSCRIPTION_EXEMPT_CODES}) and callers
     * with no anchor pass straight through, and any status other than ARCHIVED --
     * including a failed lookup, which resolves to NONE -- fails open so a transient
     * DB issue can never lock the whole platform out.
     */
    private static void dispatchGated(EventBus eventBus, String processingCode, JsonObject data,
            HttpServerResponse response) {
        if (SUBSCRIPTION_EXEMPT_CODES.contains(processingCode)) {
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
        Subscription.statusFor(Datasource.pool(), anchorId).onComplete(ar -> {
            if (ar.succeeded() && "ARCHIVED".equals(ar.result())) {
                response.setStatusCode(402).end(new JsonObject()
                        .put("responseCode", "402")
                        .put("responseMessage", "Subscription expired. Renew to restore access.")
                        .toString());
            } else {
                dispatch(eventBus, processingCode, data, response);
            }
        });
    }

    private static void dispatch(EventBus eventBus, String processingCode, JsonObject data,
            HttpServerResponse response) {
        DeliveryOptions deliveryOptions = new DeliveryOptions().setSendTimeout(20000);
        eventBus.request(processingCode, data.toString(), deliveryOptions, sendToBus -> {
            if (sendToBus.succeeded()) {
                response.end(sendToBus.result().body().toString().trim());
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

    private static JsonObject badRequest(String message) {
        return new JsonObject().put("responseCode", "901").put("responseMessage", message);
    }
}
