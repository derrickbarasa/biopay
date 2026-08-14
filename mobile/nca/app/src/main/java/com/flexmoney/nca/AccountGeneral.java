package com.flexmoney.nca;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.flexmoney.nca.data.BiometricsContract;
import com.flexmoney.nca.sync.BiometricsSyncAdapter;


public class AccountGeneral {
    public static Account getAccount(Context context) {
        return new Account(context.getString(R.string.app_name), context.getString(R.string.sync_account_type));
    }
    public static void createSyncAccount(Context context) {
        boolean created = false;
        Account account = getAccount(context);
        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        Log.e("accountManager", account.toString());

        // If the password doesn't exist, the account doesn't exist
        if (accountManager.addAccountExplicitly(account, null, null)) {
            final String AUTHORITY = BiometricsContract.CONTENT_AUTHORITY;
            final long SYNC_FREQUENCY = 60 * 60; // 1 hour (seconds)

            // Inform the system that this account supports sync
            ContentResolver.setIsSyncable(account, AUTHORITY, 1);

            // Inform the system that this account is eligible for auto sync when the network is up
            ContentResolver.setSyncAutomatically(account, AUTHORITY, true);

            // Recommend a schedule for automatic synchronization. The system may modify this based
            // on other scheduled syncs and network utilization.
            ContentResolver.addPeriodicSync(account, AUTHORITY, new Bundle(), SYNC_FREQUENCY);

            created = true;
            Log.e("Create Sync", "Am here");
        }else{
            Log.e("AccountGeneral", "Account could not be created");
        }
        if (created) {
            Log.e("Created Sync", "Am here");
            BiometricsSyncAdapter.performSync(context);
        }
    }
}
