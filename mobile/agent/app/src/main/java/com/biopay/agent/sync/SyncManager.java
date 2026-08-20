package com.biopay.agent.sync;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.biopay.agent.data.AlternateDao;
import com.biopay.agent.data.AttendanceDao;
import com.biopay.agent.data.FingerprintDao;
import com.biopay.agent.data.FaceDao;
import com.biopay.agent.data.GeoDao;
import com.biopay.agent.data.HouseholdDao;
import com.biopay.agent.data.ImageDao;
import com.biopay.agent.data.PaymentDao;
import com.biopay.agent.data.VoucherDao;
import com.biopay.agent.network.ApiClient;
import com.biopay.agent.session.SessionManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One full offline-to-server sync pass, run synchronously on a background thread (see
 * {@link SyncWorker}). Every table syncs independently -- no ordering between them is required,
 * since household/alternate numbers are generated client-side and the backend has no foreign-key
 * constraints tying these tables together (see database/migrations/001's own comment on that
 * convention). A failed row is simply left PENDING for the next pass; nothing here retries within
 * a single run.
 */
public class SyncManager {

    private static final String TAG = "SyncManager";

    private final Context context;
    private final HouseholdDao householdDao;
    private final AlternateDao alternateDao;
    private final FingerprintDao fingerprintDao;
    private final FaceDao faceDao;
    private final ImageDao imageDao;
    private final AttendanceDao attendanceDao;
    private final PaymentDao paymentDao;
    private final VoucherDao voucherDao;
    private final GeoDao geoDao;

    public SyncManager(Context context) {
        this.context = context.getApplicationContext();
        householdDao = new HouseholdDao(this.context);
        alternateDao = new AlternateDao(this.context);
        fingerprintDao = new FingerprintDao(this.context);
        faceDao = new FaceDao(this.context);
        imageDao = new ImageDao(this.context);
        attendanceDao = new AttendanceDao(this.context);
        paymentDao = new PaymentDao(this.context);
        voucherDao = new VoucherDao(this.context);
        geoDao = new GeoDao(this.context);
    }

    /** @return true if every pending row across every table synced successfully this pass. */
    public boolean syncAll() {
        boolean allSucceeded = true;
        allSucceeded &= syncGeography();
        allSucceeded &= syncHouseholds();
        allSucceeded &= syncAlternates();
        allSucceeded &= syncFingerprints();
        allSucceeded &= syncFaces();
        allSucceeded &= syncImages();
        allSucceeded &= syncAttendances();
        allSucceeded &= syncFieldPayments();
        allSucceeded &= syncVoucherCatalogue();
        allSucceeded &= syncVoucherRedemptions();
        return allSucceeded;
    }

    private boolean syncVoucherCatalogue() {
        try {
            org.json.JSONArray rows=ApiClient.get(context).dispatchSync("SYNC_VOUCHERS",new HashMap<>()).optJSONArray("results");
            if(rows!=null)for(int i=0;i<rows.length();i++){org.json.JSONObject r=rows.getJSONObject(i);voucherDao.upsert(r.optString("voucherCode"),r.optString("householdNumber"),r.optDouble("amount"),r.optString("purpose",null),r.optString("expiresAt",null));}
            return true;
        } catch(Exception ex){Log.w(TAG,"Voucher catalogue sync failed: "+ex.getMessage());return false;}
    }

    private boolean syncVoucherRedemptions() {
        boolean ok=true;for(VoucherDao.Voucher v:voucherDao.listPendingRedemptions())try{Map<String,Object> p=new HashMap<>();p.put("voucherCode",v.code);p.put("matchedFingerprint",v.matchedFingerprintUuid);p.put("latitude",v.latitude);p.put("longitude",v.longitude);ApiClient.get(context).dispatchSync("REDEEM_VOUCHER",p);voucherDao.markRedemptionSynced(v.code);}catch(Exception ex){Log.w(TAG,"Voucher redemption sync failed for "+v.code+": "+ex.getMessage());ok=false;}return ok;
    }

    /**
     * Read-only pull of the anchor's configured state/county/location(payam)/village(boma)
     * hierarchy so the household form can offer name-based pickers offline (see GeoDao). Fails
     * open like the other catalogue pulls -- an anchor that hasn't configured a hierarchy yet
     * (or a device that's simply offline) must never block household registration, it just falls
     * back to the form's manual-code entry.
     */
    private boolean syncGeography() {
        Integer anchorId = new SessionManager(context).getAnchorId();
        if (anchorId == null) {
            return true;
        }
        try {
            Map<String, Object> anchorParam = new HashMap<>();
            anchorParam.put("anchorId", anchorId);

            org.json.JSONArray states = ApiClient.get(context).dispatchSync("GET_STATES", anchorParam).optJSONArray("results");
            if (states != null) {
                for (int i = 0; i < states.length(); i++) {
                    org.json.JSONObject r = states.getJSONObject(i);
                    geoDao.upsertState(r.optString("code"), r.optString("name"));
                }
            }

            org.json.JSONArray counties = ApiClient.get(context).dispatchSync("GET_COUNTIES", anchorParam).optJSONArray("results");
            if (counties != null) {
                for (int i = 0; i < counties.length(); i++) {
                    org.json.JSONObject r = counties.getJSONObject(i);
                    geoDao.upsertCounty(r.optString("code"), r.optString("stateCode"), r.optString("name"));
                }
            }

            // geo_locations = the household's "payam" column; countyCode is its immediate parent.
            org.json.JSONArray locations = ApiClient.get(context).dispatchSync("GET_LOCATIONS", anchorParam).optJSONArray("results");
            if (locations != null) {
                for (int i = 0; i < locations.length(); i++) {
                    org.json.JSONObject r = locations.getJSONObject(i);
                    geoDao.upsertPayam(r.optString("code"), r.optString("countyCode"), r.optString("name"));
                }
            }

            // geo_villages = the household's "boma" column; locationCode (payam) is its immediate parent.
            org.json.JSONArray villages = ApiClient.get(context).dispatchSync("GET_VILLAGES", anchorParam).optJSONArray("results");
            if (villages != null) {
                for (int i = 0; i < villages.length(); i++) {
                    org.json.JSONObject r = villages.getJSONObject(i);
                    geoDao.upsertBoma(r.optString("code"), r.optString("locationCode"), r.optString("name"));
                }
            }
            return true;
        } catch (Exception ex) {
            Log.w(TAG, "Geography sync failed: " + ex.getMessage());
            return false;
        }
    }

    private boolean syncHouseholds() {
        boolean allOk = true;
        for (HouseholdDao.Household h : householdDao.listPending()) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("householdNumber", h.householdNumber);
                params.put("householdName", h.householdName);
                params.put("age", h.age);
                params.put("gender", h.gender);
                params.put("phoneNumber", h.phoneNumber);
                params.put("householdSize", h.householdSize);
                params.put("stateCode", h.stateCode);
                params.put("countyCode", h.countyCode);
                params.put("payamCode", h.payamCode);
                params.put("bomaCode", h.bomaCode);
                params.put("duplicate", 0);
                params.put("registrationMethod", h.registrationMethod);
                ApiClient.get(context).dispatchSync("UPLOAD_HOUSEHOLD_BIO", params);
                householdDao.markSynced(h.householdNumber);
            } catch (Exception ex) {
                Log.w(TAG, "Household sync failed for " + h.householdNumber + ": " + ex.getMessage());
                allOk = false;
            }
        }
        return allOk;
    }

    private boolean syncAlternates() {
        boolean allOk = true;
        for (AlternateDao.Alternate a : alternateDao.listPending()) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("householdNumber", a.householdNumber);
                params.put("alternateNumber", a.alternateNumber);
                params.put("alternateName", a.alternateName);
                params.put("relationship", a.relationship);
                params.put("age", a.age);
                params.put("phoneNumber", a.phoneNumber);
                params.put("gender", a.gender);
                params.put("duplicate", 0);
                ApiClient.get(context).dispatchSync("UPLOAD_ALTERNATE_BIO", params);
                alternateDao.markSynced(a.alternateNumber);
            } catch (Exception ex) {
                Log.w(TAG, "Alternate sync failed for " + a.alternateNumber + ": " + ex.getMessage());
                allOk = false;
            }
        }
        return allOk;
    }

    private boolean syncFingerprints() {
        boolean allOk = true;
        for (FingerprintDao.PendingFingerprint p : fingerprintDao.listPending()) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("beneficiaryId", p.beneficiaryId);
                params.put("beneficiaryType", p.beneficiaryType);
                params.put("fingerNumber", p.fingerNumber);
                params.put("templateData", Base64.encodeToString(p.template, Base64.NO_WRAP));
                // The server assigns its own uuid to the enrolled row rather than accepting ours;
                // matched_fp on payments/attendances is a plain informational column (never
                // joined against fingerprints.uuid anywhere in the backend), so the local uuid
                // already recorded there is left as-is rather than reconciled against it.
                ApiClient.get(context).dispatchSync("ENROLL_FINGERPRINT", params);
                fingerprintDao.markSynced(p.uuid);
            } catch (Exception ex) {
                Log.w(TAG, "Fingerprint sync failed for " + p.uuid + ": " + ex.getMessage());
                allOk = false;
            }
        }
        return allOk;
    }

    private boolean syncFaces() {
        boolean allOk = true;
        for (FaceDao.FaceRecord face : faceDao.listPending()) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("uuid", face.uuid);
                params.put("beneficiaryId", face.beneficiaryId);
                params.put("beneficiaryType", face.beneficiaryType);
                params.put("embedding", new org.json.JSONArray(face.embedding));
                params.put("embeddingDimensions", face.dimensions);
                params.put("modelVersion", face.modelVersion);
                params.put("qualityScore", face.qualityScore);
                ApiClient.get(context).dispatchSync("ENROLL_FACE", params);
                faceDao.markSynced(face.uuid);
            } catch (Exception ex) {
                Log.w(TAG, "Face enrolment sync failed for " + face.uuid + ": " + ex.getMessage());
                allOk = false;
            }
        }

        try {
            org.json.JSONArray rows = ApiClient.get(context)
                    .dispatchSync("SYNC_FACES", new HashMap<>()).optJSONArray("results");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    org.json.JSONObject row = rows.getJSONObject(i);
                    faceDao.insertSynced(row.getInt("beneficiaryType"), row.getString("beneficiaryId"),
                            row.getString("uuid"), row.getJSONArray("embedding"),
                            row.getString("modelVersion"), row.optDouble("qualityScore", 0));
                }
            }
        } catch (Exception ex) {
            Log.w(TAG, "Face catalogue sync failed: " + ex.getMessage());
            allOk = false;
        }
        return allOk;
    }

    private boolean syncImages() {
        boolean allOk = true;
        for (ImageDao.PendingImage p : imageDao.listPending()) {
            try {
                String base64 = readFileAsBase64(p.localPath);
                Map<String, Object> params = new HashMap<>();
                params.put("beneficiaryId", p.beneficiaryId);
                params.put("beneficiaryType", p.beneficiaryType);
                params.put("imageBase64", base64);
                params.put("extension", extensionOf(p.localPath));
                ApiClient.get(context).dispatchSync("UPLOAD_IMAGE", params);
                imageDao.markSynced(p.id);
            } catch (Exception ex) {
                Log.w(TAG, "Image sync failed for id=" + p.id + ": " + ex.getMessage());
                allOk = false;
            }
        }
        return allOk;
    }

    private boolean syncAttendances() {
        boolean allOk = true;
        for (AttendanceDao.PendingAttendance p : attendanceDao.listPending()) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("beneficiaryId", p.beneficiaryId);
                params.put("beneficiaryType", p.beneficiaryType);
                params.put("householdNumber", p.householdNumber);
                params.put("clock", p.clock);
                params.put("workCode", p.workCode);
                params.put("latitude", p.latitude);
                params.put("longitude", p.longitude);
                params.put("biometricVerified", true);
                params.put("fingerprintUuid", p.matchedFingerprintUuid);
                ApiClient.get(context).dispatchSync("RECORD_ATTENDANCE", params);
                attendanceDao.markSynced(p.id);
            } catch (Exception ex) {
                Log.w(TAG, "Attendance sync failed for id=" + p.id + ": " + ex.getMessage());
                allOk = false;
            }
        }
        return allOk;
    }

    private boolean syncFieldPayments() {
        boolean allOk = true;
        for (PaymentDao.PendingFieldPayment p : paymentDao.listPendingFieldPayments()) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("householdNumber", p.householdNumber);
                params.put("amount", p.amount);
                params.put("biometricVerified", true);
                params.put("fingerprintUuid", p.matchedFingerprintUuid);
                params.put("latitude", p.latitude);
                params.put("longitude", p.longitude);
                ApiClient.get(context).dispatchSync("RECORD_FIELD_PAYMENT", params);
                paymentDao.markSynced(p.id);
            } catch (Exception ex) {
                Log.w(TAG, "Field payment sync failed for id=" + p.id + ": " + ex.getMessage());
                allOk = false;
            }
        }
        return allOk;
    }

    private static String extensionOf(String path) {
        int dot = path == null ? -1 : path.lastIndexOf('.');
        return dot < 0 ? "jpg" : path.substring(dot + 1);
    }

    private static String readFileAsBase64(String path) throws IOException {
        File file = new File(path);
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            int read;
            while (offset < bytes.length && (read = in.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += read;
            }
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
