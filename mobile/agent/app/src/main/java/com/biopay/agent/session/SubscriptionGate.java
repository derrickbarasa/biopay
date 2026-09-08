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
 * <p>Checked after every online login. A transport failure may use the last successful check,
 * but only while its bounded 60-day offline authorization is still valid. A known archived
 * result never fails open and can only be replaced by a successful online renewal check.
 */
public final class SubscriptionGate {

    public interface Callback {
        void onAllowed();
        void onLocked();
        void onOnlineRequired();
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
                OfflineAccessManager offlineAccess = new OfflineAccessManager(context);
                offlineAccess.recordSubscriptionCheck(status);
                if ("ARCHIVED".equals(status)) {
                    callback.onLocked();
                } else {
                    callback.onAllowed();
                }
            }

            @Override
            public void onError(String message, String responseCode) {
                OfflineAccessManager.Decision decision =
                        new OfflineAccessManager(context).evaluateCachedAuthorization();
                if (decision == OfflineAccessManager.Decision.LOCKED) {
                    callback.onLocked();
                } else if (decision == OfflineAccessManager.Decision.ALLOWED) {
                    callback.onAllowed();
                } else {
                    callback.onOnlineRequired();
                }
            }
        });
    }
}
