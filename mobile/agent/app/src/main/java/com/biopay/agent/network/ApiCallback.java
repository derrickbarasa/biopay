package com.biopay.agent.network;

import org.json.JSONObject;

/** Result of a single {@link ApiClient#dispatch} call. */
public interface ApiCallback {

    void onSuccess(JSONObject response);

    /** {@code responseCode} is the backend's own code (e.g. "999"), null for a transport-level failure. */
    void onError(String message, String responseCode);
}
