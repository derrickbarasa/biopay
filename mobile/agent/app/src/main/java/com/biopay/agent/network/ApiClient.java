package com.biopay.agent.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.biopay.agent.BuildConfig;
import com.biopay.agent.session.SessionManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Route;

/**
 * Single entry point for talking to the BioPay backend's dispatch routes --
 * mirrors the web dashboard's {@code api/client.ts}: one envelope shape
 * ({@code {processingCode, ...params}}), one of two URLs depending on
 * whether the code is session-establishing, and transparent access-token
 * refresh-and-retry on a 401 (the backend rotates refresh tokens on every
 * use -- see biopay-backend's Auth#refreshToken -- so a stale token is
 * expected during normal operation, not an error state).
 *
 * <p>Built on OkHttp rather than Volley: {@link #dispatch} is the async,
 * main-thread-delivered form UI callers use; {@link #dispatchSync} is a
 * blocking form for background callers (see {@code SyncManager}) that would
 * otherwise need to bridge an async callback into a blocking wait
 * themselves. Token refresh is handled once, for both forms, by the
 * {@link TokenAuthenticator} installed on the shared client -- OkHttp
 * invokes it for any call that comes back 401, so there's no separate
 * retry/isRetry plumbing per call site.
 */
public class ApiClient {

    private static final String TAG = "ApiClient";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final Set<String> PUBLIC_CODES = new HashSet<>();
    static {
        PUBLIC_CODES.add("LOGIN_ANCHOR");
        PUBLIC_CODES.add("LOGIN_ORGANIZATION");
        PUBLIC_CODES.add("LOGIN_SUPERVISOR");
        PUBLIC_CODES.add("REFRESH_TOKEN");
    }

    private static ApiClient instance;

    private final OkHttpClient client;
    private final Context appContext;
    private final SessionManager sessionManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ApiClient(Context context) {
        appContext = context.getApplicationContext();
        sessionManager = new SessionManager(appContext);
        client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .authenticator(new TokenAuthenticator())
                .build();
    }

    public static synchronized ApiClient get(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    public static String getBaseUrl() {
        return BuildConfig.BIOPAY_API_BASE_URL;
    }

    public void dispatch(String processingCode, Map<String, Object> params, ApiCallback callback) {
        Request request;
        try {
            request = buildRequest(processingCode, params);
        } catch (JSONException ex) {
            postError("Failed to build request", null, callback);
            return;
        }

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException ex) {
                Log.w(TAG, processingCode + " failed: " + ex.getMessage());
                postError(connectionErrorMessage(ex), null, callback);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    JSONObject body = parseBody(r);
                    String responseCode = body.optString("responseCode", "000");
                    if (!"000".equals(responseCode)) {
                        postError(body.optString("responseMessage", "Request failed"), responseCode, callback);
                        return;
                    }
                    postSuccess(body, callback);
                } catch (IOException ex) {
                    postError(connectionErrorMessage(ex), null, callback);
                }
            }
        });
    }

    /** Blocking variant for background callers (e.g. {@code SyncWorker}) that already run off the
     * main thread and don't need a callback bridge. Throws on any transport or backend-level
     * failure -- the caller decides whether that means "leave this row pending, try next pass." */
    public JSONObject dispatchSync(String processingCode, Map<String, Object> params) throws IOException {
        Request request;
        try {
            request = buildRequest(processingCode, params);
        } catch (JSONException ex) {
            throw new IOException("Failed to build request", ex);
        }

        try (Response response = client.newCall(request).execute()) {
            JSONObject body = parseBody(response);
            String responseCode = body.optString("responseCode", "000");
            if (!"000".equals(responseCode)) {
                throw new IOException(processingCode + " failed: " + body.optString("responseMessage", "Request failed"));
            }
            return body;
        }
    }

    private Request buildRequest(String processingCode, Map<String, Object> params) throws JSONException {
        boolean isPublic = PUBLIC_CODES.contains(processingCode);
        String url = getBaseUrl() + (isPublic ? "/authentication" : "/api/v1/req");

        JSONObject body = new JSONObject();
        body.put("processingCode", processingCode);
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                body.put(entry.getKey(), entry.getValue() == null ? JSONObject.NULL : entry.getValue());
            }
        }

        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON))
                .tag(processingCode);
        if (!isPublic && sessionManager.getAccessToken() != null) {
            builder.header("Authorization", "Bearer " + sessionManager.getAccessToken());
        }
        return builder.build();
    }

    private static JSONObject parseBody(Response response) throws IOException {
        ResponseBody responseBody = response.body();
        String raw = responseBody != null ? responseBody.string() : "{}";
        try {
            return raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (JSONException ex) {
            throw new IOException("Invalid response body", ex);
        }
    }

    private String connectionErrorMessage(IOException error) {
        return "BioPay is not responding at " + getBaseUrl()
                + ". Start the backend, allow port 7730 through the computer firewall, "
                + "and keep the phone and computer on the same Wi-Fi.";
    }

    private void postSuccess(JSONObject response, ApiCallback callback) {
        mainHandler.post(() -> callback.onSuccess(response));
    }

    private void postError(String message, String responseCode, ApiCallback callback) {
        mainHandler.post(() -> callback.onError(message, responseCode));
    }

    /**
     * Transparent refresh-and-retry on 401, shared by every call through {@link #client}
     * (async and sync alike). {@code synchronized} on this instance so a 401 arriving while a
     * refresh from another request is already in flight doesn't trigger a second refresh -- it
     * just retries with whatever token the first refresh already installed.
     */
    private class TokenAuthenticator implements Authenticator {
        @Override
        public Request authenticate(Route route, Response response) {
            if (responseCount(response) >= 3) {
                return null;
            }
            String failedAuthHeader = response.request().header("Authorization");
            if (failedAuthHeader == null) {
                // A public/auth-route call (or one made before any login) -- nothing to refresh.
                return null;
            }

            synchronized (this) {
                String currentToken = sessionManager.getAccessToken();
                if (currentToken != null && !failedAuthHeader.equals("Bearer " + currentToken)) {
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + currentToken)
                            .build();
                }

                String refreshToken = sessionManager.getRefreshToken();
                if (refreshToken == null) {
                    sessionManager.clear();
                    return null;
                }

                try {
                    JSONObject refreshBody = new JSONObject()
                            .put("processingCode", "REFRESH_TOKEN")
                            .put("refreshToken", refreshToken);
                    Request refreshRequest = new Request.Builder()
                            .url(getBaseUrl() + "/authentication")
                            .post(RequestBody.create(refreshBody.toString(), JSON))
                            .build();
                    try (Response refreshResponse = client.newCall(refreshRequest).execute()) {
                        JSONObject json = parseBody(refreshResponse);
                        if (!"000".equals(json.optString("responseCode", "000"))) {
                            sessionManager.clear();
                            return null;
                        }
                        String newAccessToken = json.optString("accessToken");
                        sessionManager.updateTokens(newAccessToken, json.optString("refreshToken"));
                        return response.request().newBuilder()
                                .header("Authorization", "Bearer " + newAccessToken)
                                .build();
                    }
                } catch (Exception ex) {
                    Log.w(TAG, "Token refresh failed: " + ex.getMessage());
                    return null;
                }
            }
        }

        private int responseCount(Response response) {
            int count = 1;
            Response prior = response.priorResponse();
            while (prior != null) {
                count++;
                prior = prior.priorResponse();
            }
            return count;
        }
    }
}
