package com.biopay.agent.session;

import android.content.Context;

import com.biopay.agent.network.ApiCallback;
import com.biopay.agent.network.ApiClient;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors the web dashboard's grace/archived gate ({@code DefaultLayout.vue} +
 * backend {@code Subscription.statusFor}) for the field app: once an anchor's
 * subscription has lapsed past its grace period, agents should be told clearly
 * up front rather than discovering it mid-field-visit when every write starts
 * failing with a 402.
 *
 * <p>Checked once, right after login -- {@link com.biopay.agent.login.SplashActivity}'s
 * own javadoc notes the app clears its session as soon as it leaves the
 * foreground, so login is the only place a stale in-memory status could ever
 * matter. Deliberately fails open on any transport error (offline, timeout):
 * a field officer working without signal must not be locked out by a check
 * that itself couldn't reach the server -- the backend's own per-request 402
 * gate is what actually enforces this once a connection exists again.
 */
public final class SubscriptionGate {

    public interface Callback {
        void onAllowed();
        void onLocked();
    }

    private SubscriptionGate() {
    }

    public static void check(Context context, Integer anchorId, Callback callback) {
        if (anchorId == null) {
            // No anchor on this session (shouldn't happen for a supervisor login, but
            // there's nothing to gate on) -- let them through.
            callback.onAllowed();
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("anchorId", anchorId);
        ApiClient.get(context).dispatch("GET_SUBSCRIPTION", params, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONObject results = response.optJSONObject("results");
                String status = results != null ? results.optString("status", "NONE") : "NONE";
                if ("ARCHIVED".equals(status)) {
                    callback.onLocked();
                } else {
                    callback.onAllowed();
                }
            }

            @Override
            public void onError(String message, String responseCode) {
                // Offline or the check itself failed -- fail open (see class javadoc).
                callback.onAllowed();
            }
        });
    }
}
