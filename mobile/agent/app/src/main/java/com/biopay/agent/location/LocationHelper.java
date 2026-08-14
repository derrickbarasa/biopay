package com.biopay.agent.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

/**
 * Snapshot location reads for field capture (household registration, attendance clock-in/out).
 * Nowhere in this app keeps a live GPS lock open -- a last-known-fix read, taking whichever of
 * GPS or network is more recent, is the right weight for "tag this record with where it happened."
 */
public final class LocationHelper {

    private LocationHelper() {
    }

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** @return the more recent of the last known GPS/network fix, or null if neither is available
     * (no permission, providers disabled, or no fix has ever been acquired). */
    public static Location getLastKnownLocation(Context context) {
        if (!hasPermission(context)) {
            return null;
        }
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return null;
        }

        Location best = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (!locationManager.isProviderEnabled(provider)) {
                    continue;
                }
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) {
                    best = candidate;
                }
            } catch (SecurityException ex) {
                // Permission revoked between the hasPermission() check above and here -- treat as unavailable.
            }
        }
        return best;
    }
}
