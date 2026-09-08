package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;

import com.biopay.agent.session.SessionManager;

/** Offline voucher catalogue and queued biometric redemptions. `vouchers` carries no
 *  partner_code of its own, so display reads are scoped to the logged-in officer's own
 *  organisation via the household each voucher belongs to (see {@link HouseholdDao}). */
public class VoucherDao {
    private final DatabaseHelper helper;
    private final String partnerCode;
    public VoucherDao(Context context){helper=DatabaseHelper.get(context);partnerCode=new SessionManager(context).getPartnerCode();}

    public void upsert(String code,String household,double amount,String purpose,String expiresAt,String status){
        String remoteStatus=status==null||status.trim().isEmpty()?"ISSUED":status.trim().toUpperCase(java.util.Locale.US);
        ContentValues v=new ContentValues();v.put("voucher_code",code);v.put("household_number",household);v.put("amount",amount);v.put("purpose",purpose);v.put("expires_at",expiresAt);v.put("status",remoteStatus);v.put("redemption_sync_status",DatabaseHelper.SYNC_SYNCED);
        android.database.sqlite.SQLiteDatabase db=helper.getWritableDatabase();
        long inserted=db.insertWithOnConflict("vouchers",null,v,android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE);
        if(inserted==-1){
            // Never let a catalogue pull downgrade a redemption that is still queued offline.
            ContentValues update=new ContentValues();update.put("household_number",household);update.put("amount",amount);update.put("purpose",purpose);update.put("expires_at",expiresAt);update.put("status",remoteStatus);
            db.update("vouchers",update,"voucher_code=? AND NOT (status='REDEEMED' AND redemption_sync_status=?)",new String[]{code,String.valueOf(DatabaseHelper.SYNC_PENDING)});
        }
    }
    public List<Voucher> listAll(){return query("EXISTS (SELECT 1 FROM households ph WHERE ph.household_number=v.household_number AND ph.partner_code=?)",new String[]{partnerCode});}
    public List<Voucher> listIssued(){return query("v.status='ISSUED' AND EXISTS (SELECT 1 FROM households ph WHERE ph.household_number=v.household_number AND ph.partner_code=?)",new String[]{partnerCode});}
    public List<Voucher> listPendingRedemptions(){return query("v.status='REDEEMED' AND v.redemption_sync_status=?",new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)});}
    // LEFT JOINed so the picker can show the household's name instead of its bare code -- see
    // VoucherRedemptionActivity, which mirrors GeneratePaymentActivity's household picker.
    private List<Voucher> query(String where,String[] args){List<Voucher> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT v.*, hh.household_name AS household_name FROM vouchers v LEFT JOIN households hh ON hh.household_number=v.household_number WHERE "+where+" ORDER BY v.created_at DESC",args)){while(c.moveToNext()){Voucher v=new Voucher();v.code=c.getString(c.getColumnIndexOrThrow("voucher_code"));v.householdNumber=c.getString(c.getColumnIndexOrThrow("household_number"));v.householdName=optional(c.getString(c.getColumnIndexOrThrow("household_name")));v.amount=c.getDouble(c.getColumnIndexOrThrow("amount"));v.purpose=optional(c.getString(c.getColumnIndexOrThrow("purpose")));v.expiresAt=optional(c.getString(c.getColumnIndexOrThrow("expires_at")));v.status=c.getString(c.getColumnIndexOrThrow("status"));v.matchedFingerprintUuid=c.getString(c.getColumnIndexOrThrow("matched_fingerprint_uuid"));v.latitude=c.getString(c.getColumnIndexOrThrow("latitude"));v.longitude=c.getString(c.getColumnIndexOrThrow("longitude"));out.add(v);}}return out;}
    private static String optional(String value){return value==null||value.trim().isEmpty()||"null".equalsIgnoreCase(value.trim())?null:value;}
    public void queueRedemption(String code,String fingerprint,String latitude,String longitude){ContentValues v=new ContentValues();v.put("status","REDEEMED");v.put("matched_fingerprint_uuid",fingerprint);v.put("latitude",latitude);v.put("longitude",longitude);v.put("redemption_sync_status",DatabaseHelper.SYNC_PENDING);helper.getWritableDatabase().update("vouchers",v,"voucher_code=?",new String[]{code});}
    public void markRedemptionSynced(String code){ContentValues v=new ContentValues();v.put("redemption_sync_status",DatabaseHelper.SYNC_SYNCED);helper.getWritableDatabase().update("vouchers",v,"voucher_code=?",new String[]{code});}
    public static class Voucher {public String code;public String householdNumber;public String householdName;public double amount;public String purpose;public String expiresAt;public String status;public String matchedFingerprintUuid;public String latitude;public String longitude;}
}
