package com.biopay.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mssqlclient.MSSQLPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import com.biopay.databases.Datasource;
import com.biopay.utilities.Crypto;
import com.biopay.utilities.FileStore;
import com.biopay.utilities.Logging;
import com.biopay.utilities.Rows;
import com.biopay.utilities.Utilities;

/**
 * Everything the Android field app talks to after logging in: enrolling and
 * syncing fingerprint templates, uploading beneficiary images, recording a
 * field payment after a biometric match, and the one-shot login bundle that
 * seeds the device's offline store.
 *
 * <p>{@link #verify} is a placeholder matcher (exact template equality after
 * decryption) -- there is no fingerprint-matching SDK wired into this
 * backend. It exists to prove out the request/response/audit contract a
 * real vendor SDK integration (native/JNI minutiae matching with a score
 * threshold) would slot into; do not treat it as production-grade matching.
 */
public class Biometric extends AbstractVerticle {

    EventBus eventBus;
    MSSQLPool pool;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        System.out.println("deploymentId Biometric =" + vertx.getOrCreateContext().deploymentID());
        eventBus = vertx.eventBus();
        pool = Datasource.pool();

        eventBus.consumer("UPLOAD_HOUSEHOLD_BIO", this::uploadHouseholdBio);
        eventBus.consumer("UPLOAD_ALTERNATE_BIO", this::uploadAlternateBio);
        eventBus.consumer("ENROLL_FINGERPRINT", this::enrollFingerprint);
        eventBus.consumer("VERIFY_FINGERPRINT", this::verify);
        eventBus.consumer("SYNC_FINGERPRINTS", this::syncFingerprints);
        eventBus.consumer("UPLOAD_IMAGE", this::uploadImage);
        eventBus.consumer("SYNC_IMAGES", this::syncImages);
        eventBus.consumer("SYNC_PAYMENTS", this::syncPayments);
        eventBus.consumer("RECORD_FIELD_PAYMENT", this::recordFieldPayment);
        eventBus.consumer("RECORD_ATTENDANCE", this::recordAttendance);
        eventBus.consumer("GET_ATTENDANCE", this::getAttendance);
        eventBus.consumer("BIOMETRIC_LOGIN", this::biometricLogin);
        startPromise.complete();
    }

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

    private static String partnerCodeOf(JsonObject payload) {
        return payload.getString("partnerCode", "");
    }

    // ---- UPLOAD_HOUSEHOLD_BIO (field capture, with duplicate-check fields) --------

    private void uploadHouseholdBio(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = partnerCodeOf(payload);
        if (partnerCode.isEmpty()) {
            replyError(message, "This action requires a supervisor session");
            return;
        }
        String householdNumber = payload.getString("householdNumber", Utilities.generateCode("HH"));

        String sql = "INSERT INTO households (supervisor_id, partner_code, household_number, household_name, age, "
                + "gender, phone_number, household_size, boma_code, latitude, longitude, duplicate, duplicate_number, "
                + "matching_score, status, created_by, created_at, updated_at, stored_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,@p11,@p12,@p13,@p14,1,@p15,GETDATE(),GETDATE(),GETDATE())";

        pool.preparedQuery(sql)
                .execute(Tuple.of(
                        String.valueOf(payload.getValue("actorId")), partnerCode, householdNumber,
                        payload.getString("householdName", "").trim(), payload.getInteger("age"),
                        payload.getString("gender"), payload.getString("phoneNumber"),
                        payload.getInteger("householdSize"), payload.getString("bomaCode"),
                        payload.getString("latitude"), payload.getString("longitude"),
                        payload.getInteger("duplicate", 0), payload.getString("duplicateNumber"),
                        payload.getString("matchingScore"), String.valueOf(payload.getValue("actorId"))))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Household synced successfully")
                        .put("householdNumber", householdNumber)));
    }

    // ---- UPLOAD_ALTERNATE_BIO -----------------------------------------------------

    private void uploadAlternateBio(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = partnerCodeOf(payload);
        String householdNumber = payload.getString("householdNumber", "").trim();
        if (partnerCode.isEmpty() || householdNumber.isEmpty()) {
            replyError(message, "This action requires a supervisor session and a householdNumber");
            return;
        }
        String alternateNumber = payload.getString("alternateNumber", Utilities.generateCode("ALT"));

        String sql = "INSERT INTO alternates (supervisor_id, household_number, partner_code, alternate_number, "
                + "alternate_name, relationship, age, phone_number, gender, duplicate, duplicate_number, matching_score, "
                + "status, created_by, created_at, stored_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,@p11,@p12,1,@p13,GETDATE(),GETDATE())";

        pool.preparedQuery(sql)
                .execute(Tuple.of(
                        String.valueOf(payload.getValue("actorId")), householdNumber, partnerCode, alternateNumber,
                        payload.getString("alternateName", "").trim(), payload.getString("relationship"),
                        payload.getInteger("age"), payload.getString("phoneNumber"), payload.getString("gender"),
                        payload.getInteger("duplicate", 0), payload.getString("duplicateNumber"),
                        payload.getString("matchingScore"), String.valueOf(payload.getValue("actorId"))))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Alternate synced successfully")
                        .put("alternateNumber", alternateNumber)));
    }

    // ---- ENROLL_FINGERPRINT (encrypted at rest) ------------------------------------

    private void enrollFingerprint(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = partnerCodeOf(payload);
        String beneficiaryId = payload.getString("beneficiaryId", "").trim();
        Integer beneficiaryType = payload.getInteger("beneficiaryType", 1);
        Integer fingerNumber = payload.getInteger("fingerNumber");
        String templateData = payload.getString("templateData", "");

        if (partnerCode.isEmpty() || beneficiaryId.isEmpty() || fingerNumber == null || templateData.isEmpty()) {
            replyError(message, "beneficiaryId, fingerNumber and templateData are required");
            return;
        }

        final String encryptedTemplate;
        try {
            encryptedTemplate = Crypto.encrypt(templateData);
        } catch (Exception ex) {
            Logging.applicationLog(Logging.logPreString() + "Encryption failed: " + ex.getMessage() + "\n\n", "", 3);
            replyError(message, "Failed to secure fingerprint template");
            return;
        }

        String sql = "INSERT INTO fingerprints (supervisor_id, beneficiary_type, beneficiary_id, fingerprint_number, "
                + "uuid, fingerprint, partner_code, status, created_by, created_at, stored_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,1,@p8,GETDATE(),GETDATE())";
        pool.preparedQuery(sql)
                .execute(Tuple.of(String.valueOf(payload.getValue("actorId")), beneficiaryType, beneficiaryId,
                        fingerNumber, Utilities.newUuid(), encryptedTemplate, partnerCode,
                        String.valueOf(payload.getValue("actorId"))))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Fingerprint enrolled successfully")));
    }

    // ---- VERIFY_FINGERPRINT (placeholder matcher -- see class javadoc) ------------

    private void verify(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = partnerCodeOf(payload);
        String templateData = payload.getString("templateData", "");
        String householdHint = payload.getString("householdNumber", null);

        if (partnerCode.isEmpty() || templateData.isEmpty()) {
            replyError(message, "templateData is required");
            return;
        }

        String sql = householdHint == null
                ? "SELECT * FROM fingerprints WHERE partner_code=@p1 AND status=1"
                : "SELECT * FROM fingerprints f WHERE f.partner_code=@p1 AND f.status=1 AND ("
                        + "f.beneficiary_id=@p2 OR f.beneficiary_id IN (SELECT alternate_number FROM alternates WHERE household_number=@p2))";
        Tuple params = householdHint == null ? Tuple.of(partnerCode) : Tuple.of(partnerCode, householdHint);

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    for (Row r : rows) {
                        String stored;
                        try {
                            stored = Crypto.decrypt(Rows.str(r, "fingerprint"));
                        } catch (Exception ex) {
                            continue;
                        }
                        if (stored.equals(templateData)) {
                            String beneficiaryId = Rows.str(r, "beneficiary_id");
                            audit(payload, "BIOMETRIC_VERIFIED", "FINGERPRINT", beneficiaryId,
                                    new JsonObject().put("matched", true));
                            reply(message, new JsonObject()
                                    .put("responseCode", "000")
                                    .put("responseMessage", "Match found")
                                    .put("matched", true)
                                    .put("beneficiaryId", beneficiaryId)
                                    .put("beneficiaryType", Rows.intVal(r, "beneficiary_type"))
                                    .put("fingerprintUuid", Rows.str(r, "uuid")));
                            return;
                        }
                    }
                    audit(payload, "BIOMETRIC_VERIFIED", "FINGERPRINT", null, new JsonObject().put("matched", false));
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "No match found")
                            .put("matched", false));
                });
    }

    // ---- SYNC_FINGERPRINTS (decrypted for on-device offline matching) -------------

    private void syncFingerprints(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = partnerCodeOf(payload);

        pool.preparedQuery("SELECT * FROM fingerprints WHERE partner_code=@p1 AND status=1")
                .execute(Tuple.of(partnerCode))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        String template;
                        try {
                            template = Crypto.decrypt(Rows.str(r, "fingerprint"));
                        } catch (Exception ex) {
                            continue;
                        }
                        results.add(new JsonObject()
                                .put("beneficiaryId", Rows.str(r, "beneficiary_id"))
                                .put("beneficiaryType", Rows.intVal(r, "beneficiary_type"))
                                .put("fingerNumber", Rows.intVal(r, "fingerprint_number"))
                                .put("templateData", template));
                    }
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", results));
                });
    }

    // ---- UPLOAD_IMAGE ---------------------------------------------------------------

    private void uploadImage(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = partnerCodeOf(payload);
        String beneficiaryId = payload.getString("beneficiaryId", "").trim();
        Integer beneficiaryType = payload.getInteger("beneficiaryType", 1);
        String imageBase64 = payload.getString("imageBase64", "");
        String extension = payload.getString("extension", "jpg");

        if (partnerCode.isEmpty() || beneficiaryId.isEmpty() || imageBase64.isEmpty()) {
            replyError(message, "beneficiaryId and imageBase64 are required");
            return;
        }

        final String filename;
        try {
            filename = FileStore.saveBase64Image(imageBase64, extension);
        } catch (IllegalArgumentException ex) {
            replyError(message, ex.getMessage());
            return;
        } catch (Exception ex) {
            Logging.applicationLog(Logging.logPreString() + "Image store failed: " + ex.getMessage() + "\n\n", "", 3);
            replyError(message, "Failed to store image");
            return;
        }

        String sql = "INSERT INTO images (supervisor_id, beneficiary_type, beneficiary_id, photo_url, partner_code, "
                + "status, created_by, created_at, stored_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,1,@p6,GETDATE(),GETDATE())";
        pool.preparedQuery(sql)
                .execute(Tuple.of(String.valueOf(payload.getValue("actorId")), beneficiaryType, beneficiaryId,
                        filename, partnerCode, String.valueOf(payload.getValue("actorId"))))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Image uploaded successfully")
                        .put("fileUrl", "/biopay/api/v1/files/" + filename)));
    }

    // ---- SYNC_IMAGES ------------------------------------------------------------------

    private void syncImages(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = partnerCodeOf(payload);

        pool.preparedQuery("SELECT * FROM images WHERE partner_code=@p1 AND status=1")
                .execute(Tuple.of(partnerCode))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "OK")
                        .put("results", imagesArray(rows))));
    }

    private static JsonArray imagesArray(RowSet<Row> rows) {
        JsonArray results = new JsonArray();
        for (Row r : rows) {
            results.add(new JsonObject()
                    .put("beneficiaryId", Rows.str(r, "beneficiary_id"))
                    .put("beneficiaryType", Rows.intVal(r, "beneficiary_type"))
                    .put("fileUrl", "/biopay/api/v1/files/" + Rows.str(r, "photo_url")));
        }
        return results;
    }

    // ---- SYNC_PAYMENTS (offline reference: already generated/paid) ------------------

    private void syncPayments(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = partnerCodeOf(payload);

        pool.preparedQuery("SELECT * FROM payments WHERE partner_code=@p1 ORDER BY created_at DESC")
                .execute(Tuple.of(partnerCode))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        results.add(new JsonObject()
                                .put("id", Rows.intVal(r, "id"))
                                .put("householdNumber", Rows.str(r, "household_number"))
                                .put("amount", Rows.dbl(r, "amount"))
                                .put("status", Rows.intVal(r, "status"))
                                .put("cycle", Rows.str(r, "cycle")));
                    }
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", results));
                });
    }

    // ---- RECORD_FIELD_PAYMENT (supervisor, post biometric verification) -------------

    private void recordFieldPayment(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!"SUPERVISOR".equalsIgnoreCase(payload.getString("actorRole", ""))) {
            replyError(message, "Only a field officer can record a field payment");
            return;
        }
        String partnerCode = partnerCodeOf(payload);
        String householdNumber = payload.getString("householdNumber", "").trim();
        Double amount = payload.getDouble("amount");
        Boolean biometricVerified = payload.getBoolean("biometricVerified", false);

        if (householdNumber.isEmpty() || amount == null || !Boolean.TRUE.equals(biometricVerified)) {
            replyError(message, "householdNumber, amount and a successful biometric verification are required");
            return;
        }

        Object anchorIdVal = payload.getValue("anchorId");
        String sql = "INSERT INTO payments (supervisor_id, household_number, partner_code, anchor_id, amount, "
                + "matched_fp, latitude, longitude, uuid, status, approved, created_by, created_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,1,1,@p10,GETDATE())";

        pool.preparedQuery(sql)
                .execute(Tuple.of(
                        String.valueOf(payload.getValue("actorId")), householdNumber, partnerCode,
                        anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString()), amount,
                        payload.getString("fingerprintUuid"), payload.getString("latitude"), payload.getString("longitude"),
                        Utilities.newUuid(), Integer.parseInt(payload.getValue("actorId").toString())))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    audit(payload, "PAYMENT_CREATED", "HOUSEHOLD", householdNumber,
                            new JsonObject().put("amount", amount).put("channel", "FIELD"));
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "Payment recorded successfully"));
                });
    }

    // ---- RECORD_ATTENDANCE (supervisor, biometric-verified clock-in/out) -----------
    //
    // Feeds cash/food-for-work payroll: payments.attendance * payments.wage is how
    // those cycles compute an amount, so this table is the source of truth for
    // "did this beneficiary actually show up" independent of the payment record.

    private void recordAttendance(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        if (!"SUPERVISOR".equalsIgnoreCase(payload.getString("actorRole", ""))) {
            replyError(message, "Only a field officer can record attendance");
            return;
        }
        String partnerCode = partnerCodeOf(payload);
        String beneficiaryId = payload.getString("beneficiaryId", "").trim();
        Integer beneficiaryType = payload.getInteger("beneficiaryType", 1);
        String clock = payload.getString("clock", "I").trim().toUpperCase(); // I = in, O = out
        Boolean biometricVerified = payload.getBoolean("biometricVerified", false);

        if (beneficiaryId.isEmpty() || !Boolean.TRUE.equals(biometricVerified)) {
            replyError(message, "beneficiaryId and a successful biometric verification are required");
            return;
        }

        Object anchorIdVal = payload.getValue("anchorId");
        String sql = "INSERT INTO attendances (supervisor_id, household_number, beneficiary_type, beneficiary_id, "
                + "matched_fp, uuid, clock, time, attendance_date, work_code, latitude, longitude, partner_code, "
                + "anchor_id, status, created_by, created_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,GETDATE(),CAST(GETDATE() AS DATE),@p8,@p9,@p10,@p11,@p12,1,@p13,GETDATE())";

        pool.preparedQuery(sql)
                .execute(Tuple.of(
                        String.valueOf(payload.getValue("actorId")),
                        payload.getString("householdNumber", beneficiaryId), beneficiaryType, beneficiaryId,
                        payload.getString("fingerprintUuid"), Utilities.newUuid(), clock,
                        payload.getString("workCode"), payload.getString("latitude"), payload.getString("longitude"),
                        partnerCode, anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString()),
                        String.valueOf(payload.getValue("actorId"))))
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    audit(payload, "ATTENDANCE_RECORDED", "BENEFICIARY", beneficiaryId,
                            new JsonObject().put("clock", clock).put("workCode", payload.getString("workCode")));
                    reply(message, new JsonObject()
                            .put("responseCode", "000")
                            .put("responseMessage", "Attendance recorded successfully"));
                });
    }

    // ---- GET_ATTENDANCE (dashboard/app: date, organisation and clock-type filters) ----

    private void getAttendance(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        boolean isAnchor = "ANCHOR".equalsIgnoreCase(payload.getString("actorRole", ""));
        String date = payload.getString("attendanceDate", null);
        String clock = payload.getString("clock", null);

        String sql;
        Tuple params;
        if (isAnchor) {
            // Anchor sessions carry no partner_code (see Auth#loginUser) -- scope by anchor_id
            // instead, same pattern as Payment/Dashboard, with organisationCode narrowing further.
            Object anchorIdVal = payload.getValue("anchorId");
            Integer anchorId = anchorIdVal == null ? null : Integer.parseInt(anchorIdVal.toString());
            String organisationFilter = payload.getString("organisationCode", null);
            sql = "SELECT * FROM attendances WHERE anchor_id=@p1 AND (@p2 IS NULL OR partner_code=@p2) "
                    + "AND (@p3 IS NULL OR attendance_date=@p3) AND (@p4 IS NULL OR clock=@p4) ORDER BY time DESC";
            params = Tuple.of(anchorId, organisationFilter, date, clock);
        } else {
            String partnerCode = partnerCodeOf(payload);
            sql = "SELECT * FROM attendances WHERE partner_code=@p1 "
                    + "AND (@p2 IS NULL OR attendance_date=@p2) AND (@p3 IS NULL OR clock=@p3) ORDER BY time DESC";
            params = Tuple.of(partnerCode, date, clock);
        }

        pool.preparedQuery(sql)
                .execute(params)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(rows -> {
                    JsonArray results = new JsonArray();
                    for (Row r : rows) {
                        results.add(new JsonObject()
                                .put("householdNumber", Rows.str(r, "household_number"))
                                .put("beneficiaryId", Rows.str(r, "beneficiary_id"))
                                .put("beneficiaryType", Rows.intVal(r, "beneficiary_type"))
                                .put("clock", Rows.str(r, "clock"))
                                .put("time", Rows.str(r, "time"))
                                .put("attendanceDate", Rows.str(r, "attendance_date"))
                                .put("workCode", Rows.str(r, "work_code")));
                    }
                    reply(message, new JsonObject().put("responseCode", "000").put("responseMessage", "OK").put("results", results));
                });
    }

    // ---- BIOMETRIC_LOGIN (full offline bundle) --------------------------------------

    private void biometricLogin(Message<Object> message) {
        JsonObject payload = new JsonObject(message.body().toString());
        String partnerCode = partnerCodeOf(payload);
        if (partnerCode.isEmpty()) {
            replyError(message, "This action requires a supervisor session");
            return;
        }

        Future<JsonArray> households = pool.preparedQuery("SELECT * FROM households WHERE partner_code=@p1 AND status=1")
                .execute(Tuple.of(partnerCode))
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        arr.add(new JsonObject()
                                .put("householdNumber", Rows.str(r, "household_number"))
                                .put("householdName", Rows.str(r, "household_name"))
                                .put("bomaCode", Rows.str(r, "boma_code")));
                    }
                    return arr;
                });
        Future<JsonArray> alternates = pool.preparedQuery("SELECT * FROM alternates WHERE partner_code=@p1 AND status=1")
                .execute(Tuple.of(partnerCode))
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        arr.add(new JsonObject()
                                .put("householdNumber", Rows.str(r, "household_number"))
                                .put("alternateNumber", Rows.str(r, "alternate_number"))
                                .put("alternateName", Rows.str(r, "alternate_name")));
                    }
                    return arr;
                });
        Future<JsonArray> fingerprints = pool.preparedQuery("SELECT * FROM fingerprints WHERE partner_code=@p1 AND status=1")
                .execute(Tuple.of(partnerCode))
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        try {
                            arr.add(new JsonObject()
                                    .put("beneficiaryId", Rows.str(r, "beneficiary_id"))
                                    .put("beneficiaryType", Rows.intVal(r, "beneficiary_type"))
                                    .put("fingerNumber", Rows.intVal(r, "fingerprint_number"))
                                    .put("templateData", Crypto.decrypt(Rows.str(r, "fingerprint"))));
                        } catch (Exception ignored) {
                            // Skip a template that fails to decrypt rather than fail the whole sync.
                        }
                    }
                    return arr;
                });
        Future<JsonArray> images = pool.preparedQuery("SELECT * FROM images WHERE partner_code=@p1 AND status=1")
                .execute(Tuple.of(partnerCode))
                .map(Biometric::imagesArray);
        Future<JsonArray> payments = pool.preparedQuery("SELECT * FROM payments WHERE partner_code=@p1 ORDER BY created_at DESC")
                .execute(Tuple.of(partnerCode))
                .map(rows -> {
                    JsonArray arr = new JsonArray();
                    for (Row r : rows) {
                        arr.add(new JsonObject()
                                .put("id", Rows.intVal(r, "id"))
                                .put("householdNumber", Rows.str(r, "household_number"))
                                .put("amount", Rows.dbl(r, "amount"))
                                .put("status", Rows.intVal(r, "status")));
                    }
                    return arr;
                });

        Future.all(households, alternates, fingerprints, images, payments)
                .onFailure(err -> onDbError(message, err))
                .onSuccess(cf -> reply(message, new JsonObject()
                        .put("responseCode", "000")
                        .put("responseMessage", "Sync bundle ready")
                        .put("results", new JsonObject()
                                .put("households", (JsonArray) cf.resultAt(0))
                                .put("alternates", (JsonArray) cf.resultAt(1))
                                .put("fingerprints", (JsonArray) cf.resultAt(2))
                                .put("images", (JsonArray) cf.resultAt(3))
                                .put("payments", (JsonArray) cf.resultAt(4)))));
    }

    // ---- audit ------------------------------------------------------------------------

    private void audit(JsonObject payload, String action, String entityType, String entityId, JsonObject details) {
        Object actorId = payload.getValue("actorId");
        Object anchorId = payload.getValue("anchorId");
        String sql = "INSERT INTO audit_logs (actor_type, actor_id, anchor_id, partner_code, action, entity_type, entity_id, details, created_at) "
                + "VALUES (@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,GETDATE())";
        pool.preparedQuery(sql)
                .execute(Tuple.of("SUPERVISOR", actorId == null ? null : Integer.parseInt(actorId.toString()),
                        anchorId == null ? null : Integer.parseInt(anchorId.toString()),
                        partnerCodeOf(payload), action, entityType, entityId, details.encode()))
                .onFailure(err -> Logging.applicationLog(Logging.logPreString() + "Audit write failed: " + err.getMessage() + "\n\n", "", 3));
    }
}
