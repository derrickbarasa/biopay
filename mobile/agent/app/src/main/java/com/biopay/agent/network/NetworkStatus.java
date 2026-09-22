package com.biopay.agent.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

/**
 * Real device connectivity, straight from {@link ConnectivityManager} -- distinct from "did the
 * last API call succeed," which conflates a genuinely offline device with one that's online but
 * simply can't reach the configured {@code BIOPAY_API_BASE_URL} (wrong deploy config, server
 * down, DNS). {@link com.biopay.agent.login.LoginActivity} uses this to tell those two cases
 * apart instead of showing the offline-credentials message either way.
 *
 * <p>{@link com.biopay.agent.ui.BaseActivity} also uses {@link #observe} so every screen -- not
 * just the login screen -- notices a connectivity change while the officer is using the device.
 */
public final class NetworkStatus {

    /** Notified with the current {@link #isOnline} value whenever it may have changed. */
    public interface Listener {
        void onConnectivityChanged(boolean online);
    }

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

    /**
     * Registers {@code listener} for live connectivity changes, firing once immediately with the
     * current state and again on every transition after that. {@code registerNetworkCallback} is
     * available across this app's full API 21+ range (unlike the validated-capability check above,
     * which needs API 23), so this doesn't need the legacy fallback {@link #isOnline} does.
     *
     * <p>Every callback re-derives the answer from {@link #isOnline} rather than trusting the
     * triggering event alone, since a single lost/gained network isn't the same thing as the
     * device's overall online state when multiple transports are available.
     *
     * <p>Caller owns the returned handle and must pass it to {@link #stopObserving} (e.g. in
     * {@code onStop}) to avoid leaking the registration past the caller's lifecycle.
     */
    public static ConnectivityManager.NetworkCallback observe(Context context, Listener listener) {
        Context appContext = context.getApplicationContext();
        ConnectivityManager cm =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                listener.onConnectivityChanged(isOnline(appContext));
            }

            @Override
            public void onLost(Network network) {
                listener.onConnectivityChanged(isOnline(appContext));
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                listener.onConnectivityChanged(isOnline(appContext));
            }
        };
        if (cm != null) {
            cm.registerNetworkCallback(request, callback);
        }
        listener.onConnectivityChanged(isOnline(context));
        return callback;
    }

    public static void stopObserving(Context context, ConnectivityManager.NetworkCallback callback) {
        if (callback == null) {
            return;
        }
        ConnectivityManager cm = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        try {
            cm.unregisterNetworkCallback(callback);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered (or never successfully registered) -- nothing to undo.
        }
    }
}
