package com.flexmoney.nca.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class BiometricsAuthenticatorService extends Service {
    // Instance field that stores the authenticator object
    private BiometricsAuthenticator bioAuthenticator;
    @Override
    public void onCreate() {
        // Create a new authenticator object
        bioAuthenticator = new BiometricsAuthenticator(this);
    }
    /*
     * When the system binds to this Service to make the RPC call
     * return the authenticator's IBinder.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return bioAuthenticator.getIBinder();
    }
}
