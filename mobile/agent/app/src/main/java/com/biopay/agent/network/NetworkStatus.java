package com.biopay.agent.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;

/**
 * Real device connectivity, straight from {@link ConnectivityManager} -- distinct from "did the
 * last API call succeed," which conflates a genuinely offline device with one that's online but
 * simply can't reach the configured {@code BIOPAY_API_BASE_URL} (wrong deploy config, server
 * down, DNS). {@link com.biopay.agent.login.LoginActivity} uses this to tell those two cases
 * apart instead of showing the offline-credentials message either way.
 */
public final class NetworkStatus {

    private NetworkStatus() {
    }

    /** True only for a network with an active internet-capable, validated (i.e. actually reached
     *  a captive-portal-free internet, not just a router) connection. */
    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        // getActiveNetwork()/NetworkCapabilities.NET_CAPABILITY_VALIDATED only exist from API 23 --
        // the morphoSmart615 flavor's field tablets run API 21/22, so they need the older,
        // deprecated-but-still-present NetworkInfo path instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
