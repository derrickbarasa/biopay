package com.biopay.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;

/** Offline voucher catalogue and queued biometric redemptions. */
public class VoucherDao {
    private final DatabaseHelper helper;
    public VoucherDao(Context context){helper=DatabaseHelper.get(context);}

    public void upsert(String code,String household,double amount,String purpose,String expiresAt){
        ContentValues v=new ContentValues();v.put("voucher_code",code);v.put("household_number",household);v.put("amount",amount);v.put("purpose",purpose);v.put("expires_at",expiresAt);v.put("status","ISSUED");v.put("redemption_sync_status",DatabaseHelper.SYNC_SYNCED);
        helper.getWritableDatabase().insertWithOnConflict("vouchers",null,v,android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE);
    }
    public List<Voucher> listIssued(){return query("status='ISSUED'",null);}
    public List<Voucher> listPendingRedemptions(){return query("status='REDEEMED' AND redemption_sync_status=?",new String[]{String.valueOf(DatabaseHelper.SYNC_PENDING)});}
    private List<Voucher> query(String where,String[] args){List<Voucher> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().query("vouchers",null,where,args,null,null,"created_at DESC")){while(c.moveToNext()){Voucher v=new Voucher();v.code=c.getString(c.getColumnIndexOrThrow("voucher_code"));v.householdNumber=c.getString(c.getColumnIndexOrThrow("household_number"));v.amount=c.getDouble(c.getColumnIndexOrThrow("amount"));v.purpose=c.getString(c.getColumnIndexOrThrow("purpose"));v.expiresAt=c.getString(c.getColumnIndexOrThrow("expires_at"));v.matchedFingerprintUuid=c.getString(c.getColumnIndexOrThrow("matched_fingerprint_uuid"));v.latitude=c.getString(c.getColumnIndexOrThrow("latitude"));v.longitude=c.getString(c.getColumnIndexOrThrow("longitude"));out.add(v);}}return out;}
    public void queueRedemption(String code,String fingerprint,String latitude,String longitude){ContentValues v=new ContentValues();v.put("status","REDEEMED");v.put("matched_fingerprint_uuid",fingerprint);v.put("latitude",latitude);v.put("longitude",longitude);v.put("redemption_sync_status",DatabaseHelper.SYNC_PENDING);helper.getWritableDatabase().update("vouchers",v,"voucher_code=?",new String[]{code});}
    public void markRedemptionSynced(String code){ContentValues v=new ContentValues();v.put("redemption_sync_status",DatabaseHelper.SYNC_SYNCED);helper.getWritableDatabase().update("vouchers",v,"voucher_code=?",new String[]{code});}
    public static class Voucher {public String code;public String householdNumber;public double amount;public String purpose;public String expiresAt;public String matchedFingerprintUuid;public String latitude;public String longitude;@Override public String toString(){return code+"  •  "+householdNumber+"\n"+String.format(java.util.Locale.getDefault(),"%.2f",amount)+(purpose==null?"":"  •  "+purpose);}}
}
