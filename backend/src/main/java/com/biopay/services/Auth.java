package com.biopay.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import com.biopay.databases.Datasource;
import com.biopay.utilities.Crypto;
import com.biopay.utilities.Env;
import com.biopay.utilities.Hashing;
import com.biopay.utilities.JwtSupport;
import com.biopay.utilities.Logging;
import com.biopay.utilities.OrgModules;
import com.biopay.utilities.Passwords;
import com.biopay.utilities.Rows;
import com.biopay.utilities.TotpSupport;
import com.biopay.utilities.Utilities;

/**
 * Login / session lifecycle for anchors, organisations (a single merged
 * portal -- the caller no longer says which one, it's read off the users
 * row) and supervisors (still separate; the officer app authenticates
 * against {@code LOGIN_SUPERVISOR} directly and isn't part of the OTP flow
 * below). A successful anchor/organisation password check doesn't issue a
 * session yet -- it issues a short-lived "pending" JWT (purpose=
 * LOGIN_OTP_PENDING) that only {@link #requestLoginOtp} / {@link
 * #verifyLoginOtp} accept, and only over the public /authentication route,
 * never the JWT-protected one -- so a pending token can't be replayed
 * against any real endpoint. The real session (access + refresh tokens) is
 * only minted once the OTP step succeeds, in {@link #finishLogin}. Development
 * and test environments may explicitly set {@code OTP_REQUIRED=false}; every
 * missing or unrecognised value keeps OTP enabled.
 */
public class Auth extends AbstractVerticle {

    private static final int REFRESH_TOKEN_DAYS = 7;
    private static final int OTP_PENDING_MINUTES = 5;
    private static final int RESET_TOKEN_MINUTES = 60;

    EventBus eventBus;
    MSSQLPool pool;
    OtpService otpService;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Auth =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();
        otpService = new OtpService(pool, eventBus);
        JwtSupport.init(vertx);

        eventBus.consumer("LOGIN_USER", this::loginUser);
        eventBus.consumer("LOGIN_SUPERVISOR", this::loginSupervisor);
        eventBus.consumer("REQUEST_LOGIN_OTP", this::requestLoginOtp);
        eventBus.consumer("VERIFY_LOGIN_OTP", this::verifyLoginOtp);
        eventBus.consumer("SIGNUP_ANCHOR", this::signupAnchor);
        eventBus.consumer("REQUEST_PASSWORD_RESET", this::requestPasswordReset);
        eventBus.consumer("RESET_PASSWORD", this::resetPassword);
        eventBus.consumer("REFRESH_TOKEN", this::refreshToken);
        eventBus.consumer("LOGOUT", this::logout);
        eventBus.consumer("CHANGE_PASSWORD", this::changePassword);
        eventBus.consumer("UPDATE_PROFILE", this::updateProfile);
        eventBus.consumer("ME", this::me);
        eventBus.consumer("TOTP_SETUP_INIT", this::totpSetupInit);
        eventBus.consumer("TOTP_SETUP_CONFIRM", this::totpSetupConfirm);
        eventBus.consumer("TOTP_DISABLE", this::totpDisable);
        startPromise.complete();
    }

    // ---- shared reply helpers -------------------------------------------------

    private static void reply(Message<Object> message, JsonObject obj) {
        message.reply(obj.toString().trim());
    }

    private static void replyError(Message<Object> message, String responseMessage) {
        reply(message, new JsonObject().put("responseCode", "999").put("responseMessage", responseMessage));
    }

    private void onDbError(Message<Object> message, Throwable err) {
        Logging.applicationLog(Logging.logPreString() + "Fail. " + err.getMessage() + "\n\n", "", 3);
        replyError(message, "Failed with an error");
    }

    private static Integer intOr(Row row, String column, Integer dflt) {
        Integer v = Rows.intVal(row, column);
        return v == null ? dflt : v;
    }

    private static String frontendBaseUrl() {
        return Env.get().get("FRONTEND_BASE_URL", "http://localhost:5173");
    }

    static boolean isOtpRequired(String configuredValue) {
        return configuredValue == null || !"false".equalsIgnoreCase(configuredValue.trim());
    }

    private static boolean otpRequired() {
        return isOtpRequired(Env.get().get("OTP_REQUIRED", "true"));
    }

    /** JWTOptions#setSubject serializes "sub" as a string per the JWT spec, overriding whatever
     *  type the claims JsonObject originally put there -- principal.getInteger("sub") throws a
     *  cast exception on that string, and since it's thrown synchronously inside a Future's
     *  onSuccess handler, it silently drops the reply instead of failing loudly (20s client
     *  timeout). Every other actorId in this codebase already parses it this way; do the same here. */
    private static int subjectId(JsonObject principal) {
        return Integer.parseInt(principal.getValue("sub").toString());
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** ANCHOR sessions implicitly have every module; ORGANISATION/SUPERVISOR sessions get
     *  their organisation's enabled set, letting the frontend gate nav/actions right after login. */
    private Future<JsonArray> enabledModulesFor(String scope, String partnerCode) {
        if ("ANCHOR".equalsIgnoreCase(scope)) {
            JsonArray arr = new JsonArray();
            OrgModules.ALL.forEach(arr::add);
            return Future.succeededFuture(arr);
        }
        if (partnerCode == null || partnerCode.isEmpty()) {
            return Future.succeededFuture(new JsonArray());
        }
        return OrgModules.enabledForAsArray(pool, partnerCode);
    }

    /** Lets the Android field app show only the household-registration methods (fingerprint/face)
     *  the officer's organisation has actually enabled, without a second round-trip after login.
     *  Anchor-scoped sessions and any lookup failure default to BIOMETRIC (fingerprint-only), the
     *  same default {@link Organization} applies when an org is created without specifying one. */
    private Future<String> verificationMethodFor(String partnerCode) {
        if (partnerCode == null || partnerCode.isEmpty()) {
            return Future.succeededFuture("BIOMETRIC");
        }
        return pool.preparedQuery("SELECT verification_method FROM partners WHERE partner_id=@p1")
                .execute(Tuple.of(partnerCode))
                .map(rows -> {
                    if (rows.size() == 0) return "BIOMETRIC";
                    String method = Rows.str(rows.iterator().next(), "verification_method");
                    return method == null || method.isEmpty() ? "BIOMETRIC" : method;
                })
                .otherwise("BIOMETRIC");
    }

    // ---- LOGIN_USER (users table -- anchor or organisation, merged) -------------

    private void loginUser(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String email = payload.getString("email", "").trim().toLowerCase();
        String password = payload.getString("password", "");
        String ip = payload.getString("ipAddress", "");

        if (email.isEmpty() || password.isEmpty()) {
            replyError(message, "Email and password are required");
            return;
        }

        pool.preparedQuery("SELECT * FROM users WHERE LOWER(email) = @p1")
                .execute(Tuple.of(email))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        audit(null, null, "USER", null, "LOGIN_FAILED", ip, new JsonObject().put("email", email).put("reason", "not_found"));
                        replyError(message, "Invalid email or password");
                        return;
                    }
                    Row r = rows.iterator().next();
                    int id = intOr(r, "id", 0);
                    String scope = Rows.str(r, "user_scope");
                    Integer active = intOr(r, "active", 0);
                    Integer status = intOr(r, "status", 0);
                    String storedHash = Rows.str(r, "password");

                    if (active == null || active != 1 || status == null || status != 1) {
                        audit(id, null, "USER", null, "LOGIN_FAILED", ip, new JsonObject().put("reason", "inactive"));
                        replyError(message, "Account is inactive. Contact your administrator");
                        return;
                    }
                    if (!Passwords.verify(password, storedHash)) {
                        audit(id, null, "USER", null, "LOGIN_FAILED", ip, new JsonObject().put("reason", "bad_password"));
                        replyError(message, "Invalid email or password");
                        return;
                    }

                    Integer anchorId = intOr(r, "anchor_id", null);
                    String partnerCode = "ORGANISATION".equalsIgnoreCase(scope) ? Rows.str(r, "partner_code") : null;
                    boolean totpEnabled = Boolean.TRUE.equals(r.getBoolean("totp_enabled"));
                    if (otpRequired()) {
                        startOtpChallenge(message, id, scope, anchorId, partnerCode, email, totpEnabled);
                    } else {
                        finishLogin(message, id, ip);
                    }
                });
    }

    /** Password verified -- mint the short-lived pending token and tell the caller which
     *  OTP methods are available, instead of a real session (see {@link #finishLogin}). */
    private void startOtpChallenge(Message<Object> message, int userId, String scope, Integer anchorId,
            String partnerCode, String email, boolean totpEnabled) {
        JsonObject pendingClaims = new JsonObject()
                .put("sub", userId)
                .put("purpose", "LOGIN_OTP_PENDING")
                .put("role", scope)
                .put("anchorId", anchorId)
                .put("partnerCode", partnerCode)
                .put("email", email);
        String pendingToken = JwtSupport.provider().generateToken(pendingClaims,
                new JWTOptions()
                        .setAlgorithm("RS256")
                        .setSubject(String.valueOf(userId))
                        .setIssuer("biopay")
                        .setExpiresInMinutes(OTP_PENDING_MINUTES));

        JsonArray methods = new JsonArray().add("EMAIL");
        if (totpEnabled) {
            methods.add("TOTP");
        }

        reply(message, new JsonObject()
                .put("responseCode", "000")
                .put("responseMessage", "Choose a verification method")
                .put("pendingToken", pendingToken)
                .put("methods", methods)
                .put("maskedEmail", maskEmail(email)));
    }

    /** Verifies a pending token's signature/expiry and that it was actually minted for the
     *  OTP step (never a real access token) before either OTP handler trusts its claims. */
    private Future<JsonObject> decodePendingToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Future.failedFuture("Verification session expired. Please log in again");
        }
        return JwtSupport.provider().authenticate(new TokenCredentials(token))
                .map(User::principal)
                .compose(principal -> {
                    if (!"LOGIN_OTP_PENDING".equals(principal.getString("purpose"))) {
                        return Future.failedFuture("Verification session expired. Please log in again");
                    }
                    return Future.succeededFuture(principal);
                })
                .recover(err -> Future.failedFuture("Verification session expired. Please log in again"));
    }

    // ---- REQUEST_LOGIN_OTP (public: sends the EMAIL code; TOTP needs no request) --

    private void requestLoginOtp(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String method = payload.getString("method", "EMAIL").toUpperCase();

        decodePendingToken(payload.getString("pendingToken", ""))
                .onFailure(err -> replyError(message, err.getMessage()))
                .onSuccess(principal -> {
                    if (!"EMAIL".equals(method)) {
                        replyError(message, "Nothing to send for this method");
                        return;
                    }
                    otpService.request("LOGIN", null, subjectId(principal), principal.getString("role"), principal.getString("email"))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(v -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Verification code sent")));
                });
    }

    // ---- VERIFY_LOGIN_OTP (public: completes login on success) ---------------------

    private void verifyLoginOtp(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String method = payload.getString("method", "EMAIL").toUpperCase();
        String code = payload.getString("code", "").trim();
        String ip = payload.getString("ipAddress", "");

        if (code.isEmpty()) {
            replyError(message, "code is required");
            return;
        }

        decodePendingToken(payload.getString("pendingToken", ""))
                .onFailure(err -> replyError(message, err.getMessage()))
                .onSuccess(principal -> {
                    int userId = subjectId(principal);
                    Integer anchorId = principal.getInteger("anchorId");
                    String partnerCode = principal.getString("partnerCode");

                    Future<Boolean> verifyF = "TOTP".equals(method)
                            ? verifyTotp(userId, code)
                            : otpService.verify("LOGIN", userId, code);

                    verifyF.onFailure(err -> onDbError(message, err))
                            .onSuccess(valid -> {
                                if (!Boolean.TRUE.equals(valid)) {
                                    audit(userId, anchorId, "USER", partnerCode, "LOGIN_FAILED", ip, new JsonObject().put("reason", "bad_otp"));
                                    replyError(message, "Incorrect or expired code");
                                    return;
                                }
                                finishLogin(message, userId, ip);
                            });
                });
    }

    private Future<Boolean> verifyTotp(int userId, String code) {
        return pool.preparedQuery("SELECT totp_secret FROM users WHERE id=@p1")
                .execute(Tuple.of(userId))
                .map(rows -> {
                    if (rows.size() == 0) {
                        return false;
                    }
                    String encrypted = Rows.str(rows.iterator().next(), "totp_secret");
                    return encrypted != null && TotpSupport.verify(Crypto.decrypt(encrypted), code);
                });
    }

    /** The only place a real session gets minted for a users-table (anchor/organisation)
     *  login -- reached from {@link #verifyLoginOtp} once the OTP checks out. */
    private void finishLogin(Message<Object> message, int userId, String ip) {
        pool.preparedQuery("SELECT * FROM users WHERE id=@p1")
                .execute(Tuple.of(userId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Account no longer available");
                        return;
                    }
                    Row r = rows.iterator().next();
                    String scope = Rows.str(r, "user_scope");
                    Integer anchorId = intOr(r, "anchor_id", null);
                    String partnerCode = "ORGANISATION".equalsIgnoreCase(scope) ? Rows.str(r, "partner_code") : null;
                    String email = Rows.str(r, "email");
                    boolean systemAdmin = Boolean.TRUE.equals(r.getBoolean("is_system_admin"));

                    JsonObject claims = new JsonObject()
                            .put("sub", userId)
                            .put("role", scope)
                            .put("anchorId", anchorId)
                            .put("partnerCode", partnerCode)
                            .put("email", email)
                            .put("systemAdmin", systemAdmin);

                    issueTokens("USER", userId, claims)
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(tokens -> {
                                audit(userId, anchorId, "USER", partnerCode, "LOGIN_SUCCESS", ip, new JsonObject());
                                Future<JsonArray> permsF = getPermissions(intOr(r, "role_id", null));
                                Future<JsonArray> modulesF = enabledModulesFor(scope, partnerCode);
                                Future.all(permsF, modulesF).onComplete(cf -> reply(message, new JsonObject()
                                        .put("responseCode", "000")
                                        .put("responseMessage", "Login successful")
                                        .put("accessToken", tokens.getString("accessToken"))
                                        .put("refreshToken", tokens.getString("refreshToken"))
                                        .put("expiresIn", tokens.getInteger("expiresIn"))
                                        .put("user", new JsonObject()
                                                .put("id", userId)
                                                .put("email", email)
                                                .put("firstName", Rows.str(r, "first_name"))
                                                .put("otherNames", Rows.str(r, "other_names"))
                                                .put("role", scope)
                                                .put("anchorId", anchorId)
                                                .put("partnerCode", partnerCode)
                                                .put("systemAdmin", systemAdmin)
                                                .put("totpEnabled", Boolean.TRUE.equals(r.getBoolean("totp_enabled")))
                                                .put("permissions", permsF.result() != null ? permsF.result() : new JsonArray())
                                                .put("enabledModules", modulesF.result() != null ? modulesF.result() : new JsonArray()))));
                            });
                });
    }

    // ---- SIGNUP_ANCHOR (public: self-service, then straight into the OTP step) -----

    private void signupAnchor(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String name = payload.getString("name", "").trim();
        String authorisedName = payload.getString("authorisedName", "").trim();
        String email = payload.getString("email", "").trim().toLowerCase();
        String phone = payload.getString("phone", "").trim();
        String address = payload.getString("address", "").trim();
        String password = payload.getString("password", "");

        if (name.isEmpty() || email.isEmpty() || password.length() < 8) {
            replyError(message, "Organisation name, email and a password of at least 8 characters are required");
            return;
        }

        pool.preparedQuery("SELECT 1 AS v FROM users WHERE LOWER(email)=@p1")
                .execute(Tuple.of(email))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(existing -> {
                    if (existing.size() > 0) {
                        replyError(message, "An account with this email already exists");
                        return;
                    }
                    createAnchorAccount(message, name, authorisedName, email, phone, address, password,
                            payload.getString("ipAddress", ""));
                });
    }

    private void createAnchorAccount(Message<Object> message, String name, String authorisedName, String email,
            String phone, String address, String password, String ip) {
        String anchorCode = Utilities.generateCode("ANC");
        String passwordHash = Passwords.hash(password);
        String displayName = authorisedName.isEmpty() ? name : authorisedName;

        pool.withTransaction(connection -> connection.preparedQuery(
                        "INSERT INTO anchors (anchor_code, name, authorised_name, authorised_email, authorised_contact, "
                                + "address, status, created_by, created_at) OUTPUT INSERTED.id "
                                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,1,@p7,GETDATE())")
                .execute(Tuple.of(anchorCode, name, authorisedName, email, phone, address, "SIGNUP"))
                .map(rows -> intOr(rows.iterator().next(), "id", 0))
                .compose(anchorId -> connection.preparedQuery(
                                "INSERT INTO users (partner_code, email, username, password, first_name, other_names, "
                                        + "role_id, active, status, anchor_id, user_scope, created_at, updated_at) OUTPUT INSERTED.id "
                                        + "VALUES (NULL, @p1, @p1, @p2, @p3, '', "
                                        + "(SELECT TOP 1 id FROM roles WHERE role_name='Anchor Administrator' AND status=1), "
                                        + "1, 1, @p4, 'ANCHOR', GETDATE(), GETDATE())")
                        .execute(Tuple.of(email, passwordHash, displayName, anchorId))
                        .map(rows -> new JsonObject()
                                .put("anchorId", anchorId)
                                .put("userId", intOr(rows.iterator().next(), "id", 0)))))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(ids -> {
                    int userId = ids.getInteger("userId");
                    int anchorId = ids.getInteger("anchorId");
                    audit(userId, anchorId, "USER", null, "SIGNUP_SUCCESS", ip, new JsonObject());
                    if (otpRequired()) {
                        startOtpChallenge(message, userId, "ANCHOR", anchorId, null, email, false);
                    } else {
                        finishLogin(message, userId, ip);
                    }
                });
    }

    // ---- UPDATE_PROFILE (authenticated) ----------------------------------------

    private void updateProfile(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String actorRole = payload.getString("actorRole", "");
        Object actorIdVal = payload.getValue("actorId");
        String firstName = payload.getString("firstName", "").trim();
        String lastName = payload.getString("lastName", "").trim();

        if (actorIdVal == null || firstName.isEmpty()) {
            replyError(message, "First name is required");
            return;
        }
        if (firstName.length() > 100 || lastName.length() > 150) {
            replyError(message, "Profile names are too long");
            return;
        }

        int actorId = Integer.parseInt(actorIdVal.toString());
        boolean supervisor = "SUPERVISOR".equalsIgnoreCase(actorRole);
        String sql = supervisor
                ? "UPDATE supervisors SET firstname=@p1, lastname=@p2 WHERE id=@p3"
                : "UPDATE users SET first_name=@p1, other_names=@p2, updated_at=GETDATE() WHERE id=@p3";

        pool.preparedQuery(sql)
                .execute(Tuple.of(firstName, lastName, actorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.rowCount() == 0) {
                        replyError(message, "Account not found");
                        return;
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "Profile updated")
                            .put("firstName", firstName)
                            .put("lastName", lastName));
                });
    }

    // ---- REQUEST_PASSWORD_RESET / RESET_PASSWORD (public) ---------------------------

    private void requestPasswordReset(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String email = payload.getString("email", "").trim().toLowerCase();
        if (email.isEmpty()) {
            replyError(message, "email is required");
            return;
        }

        // Always reply the same generic success, whether or not the email is registered,
        // so this endpoint can't be used to enumerate accounts.
        pool.preparedQuery("SELECT id FROM users WHERE LOWER(email)=@p1 AND status=1")
                .execute(Tuple.of(email))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() > 0) {
                        sendResetEmail(intOr(rows.iterator().next(), "id", 0), email);
                    }
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "If that email is registered, a reset link has been sent"));
                });
    }

    private void sendResetEmail(int userId, String email) {
        String tokenPlain = Utilities.newUuid() + Utilities.newUuid();
        String tokenHash = Hashing.sha256Hex(tokenPlain);

        pool.preparedQuery("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, used, created_at) "
                        + "VALUES (@p1, @p2, DATEADD(MINUTE, @p3, GETDATE()), 0, GETDATE())")
                .execute(Tuple.of(userId, tokenHash, RESET_TOKEN_MINUTES))
                .onFailure(err -> Logging.applicationLog(Logging.logPreString() + "Fail. " + err.getMessage() + "\n\n", "", 3))
                .onSuccess(v -> {
                    String link = frontendBaseUrl() + "/reset-password?token=" + tokenPlain;
                    eventBus.send("EMAIL", new JsonObject()
                            .put("mailTo", email)
                            .put("subject", "Reset your BioPay password")
                            .put("msg", "Click the link below to set a new password. This link expires in "
                                    + RESET_TOKEN_MINUTES + " minutes and can only be used once.<br><br>"
                                    + "<a href=\"" + link + "\">" + link + "</a>"));
                });
    }

    private void resetPassword(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String token = payload.getString("token", "").trim();
        String newPassword = payload.getString("newPassword", "");
        if (token.isEmpty() || newPassword.length() < 8) {
            replyError(message, "A reset link and a new password of at least 8 characters are required");
            return;
        }
        String tokenHash = Hashing.sha256Hex(token);

        pool.preparedQuery("SELECT * FROM password_reset_tokens WHERE token_hash=@p1")
                .execute(Tuple.of(tokenHash))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Invalid or expired reset link");
                        return;
                    }
                    Row r = rows.iterator().next();
                    int id = intOr(r, "id", 0);
                    int userId = intOr(r, "user_id", 0);
                    Boolean used = r.getBoolean("used");
                    java.time.LocalDateTime expiresAt = r.getLocalDateTime("expires_at");

                    if (Boolean.TRUE.equals(used) || expiresAt == null || expiresAt.isBefore(java.time.LocalDateTime.now())) {
                        replyError(message, "Invalid or expired reset link");
                        return;
                    }

                    String passwordHash = Passwords.hash(newPassword);
                    pool.preparedQuery("UPDATE users SET password=@p1 WHERE id=@p2")
                            .execute(Tuple.of(passwordHash, userId))
                            .compose(v -> pool.preparedQuery("UPDATE password_reset_tokens SET used=1 WHERE id=@p1").execute(Tuple.of(id)))
                            .compose(v -> pool.preparedQuery(
                                            "UPDATE refresh_tokens SET revoked=1 WHERE subject_type='USER' AND subject_id=@p1")
                                    .execute(Tuple.of(userId)))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(v -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Password reset successfully. Please log in")));
                });
    }

    // ---- LOGIN_SUPERVISOR (supervisors table -- Android app only, no OTP step) ------

    private void loginSupervisor(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String email = payload.getString("email", "").trim().toLowerCase();
        String password = payload.getString("password", "");
        String ip = payload.getString("ipAddress", "");

        if (email.isEmpty() || password.isEmpty()) {
            replyError(message, "Email and password are required");
            return;
        }

        pool.preparedQuery("SELECT * FROM supervisors WHERE LOWER(email) = @p1")
                .execute(Tuple.of(email))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        audit(null, null, "SUPERVISOR", null, "LOGIN_FAILED", ip, new JsonObject().put("email", email).put("reason", "not_found"));
                        replyError(message, "Invalid email or password");
                        return;
                    }
                    Row r = rows.iterator().next();
                    int id = intOr(r, "id", 0);
                    String active = Rows.str(r, "active");
                    String storedHash = Rows.str(r, "password");

                    if (active == null || !active.trim().equals("1")) {
                        audit(id, null, "SUPERVISOR", null, "LOGIN_FAILED", ip, new JsonObject().put("reason", "inactive"));
                        replyError(message, "Account is inactive. Contact your organisation administrator");
                        return;
                    }
                    if (!Passwords.verify(password, storedHash)) {
                        audit(id, null, "SUPERVISOR", null, "LOGIN_FAILED", ip, new JsonObject().put("reason", "bad_password"));
                        replyError(message, "Invalid email or password");
                        return;
                    }

                    Integer anchorId = intOr(r, "anchor_id", null);
                    String partnerCode = Rows.str(r, "partner_code");

                    JsonObject claims = new JsonObject()
                            .put("sub", id)
                            .put("role", "SUPERVISOR")
                            .put("anchorId", anchorId)
                            .put("partnerCode", partnerCode)
                            .put("email", email);

                    issueTokens("SUPERVISOR", id, claims)
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(tokens -> {
                                audit(id, anchorId, "SUPERVISOR", partnerCode, "LOGIN_SUCCESS", ip, new JsonObject());
                                Future<JsonArray> modulesF = enabledModulesFor("SUPERVISOR", partnerCode);
                                Future<String> verificationMethodF = verificationMethodFor(partnerCode);
                                Future.all(modulesF, verificationMethodF).onComplete(cf -> reply(message, new JsonObject()
                                        .put("responseCode", "000")
                                        .put("responseMessage", "Login successful")
                                        .put("accessToken", tokens.getString("accessToken"))
                                        .put("refreshToken", tokens.getString("refreshToken"))
                                        .put("expiresIn", tokens.getInteger("expiresIn"))
                                        .put("user", new JsonObject()
                                                .put("id", id)
                                                .put("email", email)
                                                .put("firstName", Rows.str(r, "firstname"))
                                                .put("lastName", Rows.str(r, "lastname"))
                                                .put("role", "SUPERVISOR")
                                                .put("anchorId", anchorId)
                                                .put("partnerCode", partnerCode)
                                                .put("enabledModules", modulesF.succeeded() ? modulesF.result() : new JsonArray())
                                                .put("verificationMethod", verificationMethodF.succeeded() ? verificationMethodF.result() : "BIOMETRIC"))));
                            });
                });
    }

    // ---- REFRESH_TOKEN ----------------------------------------------------------

    private void refreshToken(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String refreshToken = payload.getString("refreshToken", "").trim();
        if (refreshToken.isEmpty()) {
            replyError(message, "refreshToken is required");
            return;
        }
        String tokenHash = Hashing.sha256Hex(refreshToken);

        pool.preparedQuery("SELECT * FROM refresh_tokens WHERE token_hash = @p1")
                .execute(Tuple.of(tokenHash))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Invalid refresh token");
                        return;
                    }
                    Row r = rows.iterator().next();
                    int rowId = intOr(r, "id", 0);
                    Boolean revoked = r.getBoolean("revoked");
                    java.time.LocalDateTime expiresAt = r.getLocalDateTime("expires_at");
                    String subjectType = Rows.str(r, "subject_type");
                    int subjectId = intOr(r, "subject_id", 0);

                    if (Boolean.TRUE.equals(revoked)) {
                        // Reuse of an already-rotated/revoked token: treat as compromised and
                        // burn every other outstanding refresh token for this subject too.
                        revokeAllForSubject(subjectType, subjectId);
                        replyError(message, "Refresh token has been revoked. Please log in again");
                        return;
                    }
                    if (expiresAt == null || expiresAt.isBefore(java.time.LocalDateTime.now())) {
                        replyError(message, "Refresh token has expired. Please log in again");
                        return;
                    }

                    reloadClaims(subjectType, subjectId)
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(claims -> {
                                if (claims == null) {
                                    replyError(message, "Account no longer available");
                                    return;
                                }
                                issueTokens(subjectType, subjectId, claims)
                                        .onFailure(err -> onDbError(message, err))
                                        .onSuccess(tokens -> {
                                            revokeToken(rowId, null)
                                                    .onComplete(ar -> reply(message, new JsonObject()
                                                            .put("responseCode", "000")
                                                            .put("responseMessage", "Token refreshed")
                                                            .put("accessToken", tokens.getString("accessToken"))
                                                            .put("refreshToken", tokens.getString("refreshToken"))
                                                            .put("expiresIn", tokens.getInteger("expiresIn"))));
                                        });
                            });
                });
    }

    private Future<JsonObject> reloadClaims(String subjectType, int subjectId) {
        if ("SUPERVISOR".equalsIgnoreCase(subjectType)) {
            return pool.preparedQuery("SELECT * FROM supervisors WHERE id = @p1")
                    .execute(Tuple.of(subjectId))
                    .map(rows -> {
                        if (rows.size() == 0) {
                            return null;
                        }
                        Row r = rows.iterator().next();
                        return new JsonObject()
                                .put("sub", subjectId)
                                .put("role", "SUPERVISOR")
                                .put("anchorId", intOr(r, "anchor_id", null))
                                .put("partnerCode", Rows.str(r, "partner_code"))
                                .put("email", Rows.str(r, "email"));
                    });
        }
        return pool.preparedQuery("SELECT * FROM users WHERE id = @p1")
                .execute(Tuple.of(subjectId))
                .map(rows -> {
                    if (rows.size() == 0) {
                        return null;
                    }
                    Row r = rows.iterator().next();
                    String scope = Rows.str(r, "user_scope");
                    return new JsonObject()
                            .put("sub", subjectId)
                            .put("role", scope)
                            .put("anchorId", intOr(r, "anchor_id", null))
                            .put("partnerCode", "ORGANISATION".equalsIgnoreCase(scope) ? Rows.str(r, "partner_code") : null)
                            .put("email", Rows.str(r, "email"))
                            .put("systemAdmin", Boolean.TRUE.equals(r.getBoolean("is_system_admin")));
                });
    }

    // ---- LOGOUT (authenticated) --------------------------------------------------

    private void logout(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String refreshToken = payload.getString("refreshToken", "").trim();
        if (refreshToken.isEmpty()) {
            reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "Logged out"));
            return;
        }
        String tokenHash = Hashing.sha256Hex(refreshToken);
        pool.preparedQuery("UPDATE refresh_tokens SET revoked=1 WHERE token_hash=@p1")
                .execute(Tuple.of(tokenHash))
                .onComplete(ar -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Logged out")));
    }

    // ---- CHANGE_PASSWORD (authenticated) -----------------------------------------

    private void changePassword(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String actorRole = payload.getString("actorRole", "");
        Object actorIdVal = payload.getValue("actorId");
        String oldPassword = payload.getString("oldPassword", "");
        String newPassword = payload.getString("newPassword", "");

        if (actorIdVal == null || oldPassword.isEmpty() || newPassword.length() < 8) {
            replyError(message, "A new password of at least 8 characters is required");
            return;
        }
        int actorId = Integer.parseInt(actorIdVal.toString());
        boolean isSupervisor = "SUPERVISOR".equalsIgnoreCase(actorRole);
        String table = isSupervisor ? "supervisors" : "users";

        pool.preparedQuery("SELECT password FROM " + table + " WHERE id=@p1")
                .execute(Tuple.of(actorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0 || !Passwords.verify(oldPassword, Rows.str(rows.iterator().next(), "password"))) {
                        replyError(message, "Current password is incorrect");
                        return;
                    }
                    String newHash = Passwords.hash(newPassword);
                    pool.preparedQuery("UPDATE " + table + " SET password=@p1 WHERE id=@p2")
                            .execute(Tuple.of(newHash, actorId))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(up -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Password changed successfully")));
                });
    }

    // ---- ME (authenticated: refetch profile + permissions on app load) ----------

    private void me(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String actorRole = payload.getString("actorRole", "");
        Object actorIdVal = payload.getValue("actorId");
        if (actorIdVal == null) {
            replyError(message, "Not authenticated");
            return;
        }
        int actorId = Integer.parseInt(actorIdVal.toString());

        if ("SUPERVISOR".equalsIgnoreCase(actorRole)) {
            pool.preparedQuery("SELECT * FROM supervisors WHERE id=@p1")
                    .execute(Tuple.of(actorId))
                    .onFailure(err -> onDbError(message, err))
                    .onSuccess(rows -> {
                        if (rows.size() == 0) {
                            replyError(message, "Account not found");
                            return;
                        }
                        Row r = rows.iterator().next();
                        String partnerCode = Rows.str(r, "partner_code");
                        enabledModulesFor("SUPERVISOR", partnerCode).onComplete(modulesAr -> reply(message, new JsonObject()
                                .put("responseCode", "000")
                                .put("responseMessage", "OK")
                                .put("user", new JsonObject()
                                        .put("id", actorId)
                                        .put("email", Rows.str(r, "email"))
                                        .put("firstName", Rows.str(r, "firstname"))
                                        .put("lastName", Rows.str(r, "lastname"))
                                        .put("role", "SUPERVISOR")
                                        .put("anchorId", intOr(r, "anchor_id", null))
                                        .put("partnerCode", partnerCode)
                                        .put("enabledModules", modulesAr.succeeded() ? modulesAr.result() : new JsonArray()))));
                    });
            return;
        }

        pool.preparedQuery("SELECT * FROM users WHERE id=@p1")
                .execute(Tuple.of(actorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Account not found");
                        return;
                    }
                    Row r = rows.iterator().next();
                    String scope = Rows.str(r, "user_scope");
                    String partnerCode = "ORGANISATION".equalsIgnoreCase(scope) ? Rows.str(r, "partner_code") : null;
                    Future<JsonArray> permsF = getPermissions(intOr(r, "role_id", null));
                    Future<JsonArray> modulesF = enabledModulesFor(scope, partnerCode);
                    Future.all(permsF, modulesF).onComplete(cf -> reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "OK")
                            .put("user", new JsonObject()
                                    .put("id", actorId)
                                    .put("email", Rows.str(r, "email"))
                                    .put("firstName", Rows.str(r, "first_name"))
                                    .put("otherNames", Rows.str(r, "other_names"))
                                    .put("role", scope)
                                    .put("anchorId", intOr(r, "anchor_id", null))
                                    .put("partnerCode", partnerCode)
                                    .put("systemAdmin", Boolean.TRUE.equals(r.getBoolean("is_system_admin")))
                                    .put("totpEnabled", Boolean.TRUE.equals(r.getBoolean("totp_enabled")))
                                    .put("permissions", permsF.result() != null ? permsF.result() : new JsonArray())
                                    .put("enabledModules", modulesF.result() != null ? modulesF.result() : new JsonArray()))));
                });
    }

    // ---- TOTP_SETUP_INIT / TOTP_SETUP_CONFIRM / TOTP_DISABLE (authenticated) --------

    private void totpSetupInit(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Object actorIdVal = payload.getValue("actorId");
        if (actorIdVal == null || "SUPERVISOR".equalsIgnoreCase(payload.getString("actorRole", ""))) {
            replyError(message, "Not authorised");
            return;
        }
        int actorId = Integer.parseInt(actorIdVal.toString());

        pool.preparedQuery("SELECT email FROM users WHERE id=@p1")
                .execute(Tuple.of(actorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        replyError(message, "Account not found");
                        return;
                    }
                    String email = Rows.str(rows.iterator().next(), "email");
                    String secret = TotpSupport.generateSecret();

                    // Stored immediately (encrypted) but totp_enabled stays 0 until TOTP_SETUP_CONFIRM
                    // proves the user actually scanned it -- an abandoned setup is harmless either way.
                    pool.preparedQuery("UPDATE users SET totp_secret=@p1, totp_enabled=0 WHERE id=@p2")
                            .execute(Tuple.of(Crypto.encrypt(secret), actorId))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(v -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Scan the QR code, then confirm with a code from your app")
                                    .put("secret", secret)
                                    .put("qrCode", TotpSupport.qrCodeDataUri(secret, email))));
                });
    }

    private void totpSetupConfirm(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Object actorIdVal = payload.getValue("actorId");
        String code = payload.getString("code", "").trim();
        if (actorIdVal == null || code.isEmpty()) {
            replyError(message, "code is required");
            return;
        }
        int actorId = Integer.parseInt(actorIdVal.toString());

        pool.preparedQuery("SELECT totp_secret FROM users WHERE id=@p1")
                .execute(Tuple.of(actorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    String encrypted = rows.size() == 0 ? null : Rows.str(rows.iterator().next(), "totp_secret");
                    if (encrypted == null) {
                        replyError(message, "Start enrollment first");
                        return;
                    }
                    if (!TotpSupport.verify(Crypto.decrypt(encrypted), code)) {
                        replyError(message, "Incorrect code. Check your app and try again");
                        return;
                    }
                    pool.preparedQuery("UPDATE users SET totp_enabled=1 WHERE id=@p1")
                            .execute(Tuple.of(actorId))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(v -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Authenticator app enabled")));
                });
    }

    private void totpDisable(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        Object actorIdVal = payload.getValue("actorId");
        String password = payload.getString("password", "");
        if (actorIdVal == null || password.isEmpty()) {
            replyError(message, "Your current password is required to disable this");
            return;
        }
        int actorId = Integer.parseInt(actorIdVal.toString());

        pool.preparedQuery("SELECT password FROM users WHERE id=@p1")
                .execute(Tuple.of(actorId))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    if (rows.size() == 0 || !Passwords.verify(password, Rows.str(rows.iterator().next(), "password"))) {
                        replyError(message, "Current password is incorrect");
                        return;
                    }
                    pool.preparedQuery("UPDATE users SET totp_secret=NULL, totp_enabled=0 WHERE id=@p1")
                            .execute(Tuple.of(actorId))
                            .onFailure(err -> onDbError(message, err))
                            .onSuccess(v -> reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Authenticator app disabled")));
                });
    }

    // ---- shared: token issuance, permissions, audit ------------------------------

    private Future<JsonObject> issueTokens(String subjectType, int subjectId, JsonObject claims) {
        String accessToken = JwtSupport.provider().generateToken(claims,
                new JWTOptions()
                        .setAlgorithm("RS256")
                        .setSubject(String.valueOf(subjectId))
                        .setIssuer("biopay")
                        .setExpiresInMinutes(JwtSupport.accessTokenMinutes()));

        String refreshTokenPlain = Utilities.newUuid() + Utilities.newUuid();
        String refreshTokenHash = Hashing.sha256Hex(refreshTokenPlain);

        String sql = "INSERT INTO refresh_tokens (subject_type, subject_id, token_hash, expires_at, revoked, created_at) "
                + "VALUES (@p1, @p2, @p3, DATEADD(DAY, @p4, GETDATE()), 0, GETDATE())";
        return pool.preparedQuery(sql)
                .execute(Tuple.of(subjectType, subjectId, refreshTokenHash, REFRESH_TOKEN_DAYS))
                .map(rows -> new JsonObject()
                        .put("accessToken", accessToken)
                        .put("refreshToken", refreshTokenPlain)
                        .put("expiresIn", JwtSupport.accessTokenMinutes() * 60));
    }

    private Future<Void> revokeToken(int id, Integer replacedById) {
        return pool.preparedQuery("UPDATE refresh_tokens SET revoked=1, replaced_by_id=@p1 WHERE id=@p2")
                .execute(Tuple.of(replacedById, id))
                .map(rows -> (Void) null)
                .recover(err -> Future.succeededFuture());
    }

    private void revokeAllForSubject(String subjectType, int subjectId) {
        pool.preparedQuery("UPDATE refresh_tokens SET revoked=1 WHERE subject_type=@p1 AND subject_id=@p2")
                .execute(Tuple.of(subjectType, subjectId))
                .onFailure(err -> Logging.applicationLog(Logging.logPreString() + "Fail. " + err.getMessage() + "\n\n", "", 3));
    }

    private Future<JsonArray> getPermissions(Integer roleId) {
        JsonArray perms = new JsonArray();
        if (roleId == null) {
            return Future.succeededFuture(perms);
        }
        String sql = "SELECT p.permission_name FROM role_permissions rp "
                + "JOIN permissions p ON p.id = rp.permission_id "
                + "WHERE rp.role_id = @p1 AND rp.status = 1";
        return pool.preparedQuery(sql)
                .execute(Tuple.of(roleId))
                .map(rows -> {
                    for (Row r : rows) {
                        perms.add(Rows.str(r, "permission_name"));
                    }
                    return perms;
                })
                .recover(err -> Future.succeededFuture(perms));
    }

    /** Best-effort audit write -- never blocks or fails the caller's flow. */
    private void audit(Integer actorId, Integer anchorId, String actorType, String partnerCode,
            String action, String ip, JsonObject details) {
        String sql = "INSERT INTO audit_logs (actor_type, actor_id, anchor_id, partner_code, action, details, ip_address, created_at) "
                + "VALUES (@p1, @p2, @p3, @p4, @p5, @p6, @p7, GETDATE())";
        pool.preparedQuery(sql)
                .execute(Tuple.of(actorType, actorId, anchorId, partnerCode, action, details.encode(), ip))
                .onFailure(err -> Logging.applicationLog(Logging.logPreString() + "Audit write failed: " + err.getMessage() + "\n\n", "", 3));
    }
}
