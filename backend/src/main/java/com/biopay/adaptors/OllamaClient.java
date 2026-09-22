package com.biopay.adaptors;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Thin streaming client for a self-hosted Ollama instance's {@code /api/chat} endpoint. One
 * system + one user message per call, no native multi-turn concept -- callers flatten prior
 * turns into the user message themselves.
 *
 * <p>Every instance shares one JVM-wide concurrency gate ({@link #GATE_LOCK}/{@link #active}),
 * sized by {@code LLM_MAX_CONCURRENT_REQUESTS}. A single self-hosted model instance serialises
 * inference anyway, so this keeps concurrent callers from piling requests onto it at once --
 * excess calls queue in arrival order instead of failing or timing out mid-generation.
 */
public class OllamaClient {

    private static final Object GATE_LOCK = new Object();
    private static volatile int maxConcurrentRequests = 1;
    private static int active = 0;
    private static final Deque<Runnable> queued = new ArrayDeque<>();

    private final HttpClient httpClient;
    private final String model;
    private final long timeoutMs;
    private final int numPredict;
    private final String keepAlive;

    public OllamaClient(Vertx vertx, JsonObject config) {
        String url = config.getString("LLM_URL", "http://localhost:11434");
        boolean allowInsecure = "true".equalsIgnoreCase(config.getString("LLM_ALLOW_INSECURE_TRANSPORT", "false"));
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid LLM_URL: " + url, ex);
        }
        boolean ssl = "https".equalsIgnoreCase(uri.getScheme());
        if (!ssl && !allowInsecure) {
            throw new IllegalStateException(
                    "LLM_URL (" + url + ") is plain http -- set LLM_ALLOW_INSECURE_TRANSPORT=true "
                            + "to allow this deliberately (only for a trusted/private network)");
        }
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : (ssl ? 443 : 80);

        this.model = config.getString("LLM_MODEL", "qwen2.5:7b");
        this.timeoutMs = Long.parseLong(config.getString("LLM_TIMEOUT_MS", "60000"));
        this.numPredict = Integer.parseInt(config.getString("LLM_NUM_PREDICT", "500"));
        // Ollama unloads a model from memory after its keep_alive window of inactivity (default
        // 5m) and the next call then pays a full reload -- easily several seconds, which is most
        // of why a "cold" reply feels slow even for something as short as "hello". A generous
        // keep_alive plus Chat's own startup warm-up call (see Chat.java) is what actually fixes
        // that, not anything tunable per-request once the model's already loaded.
        this.keepAlive = config.getString("LLM_KEEP_ALIVE", "30m");
        maxConcurrentRequests = Math.max(1, Integer.parseInt(config.getString("LLM_MAX_CONCURRENT_REQUESTS", "1")));

        HttpClientOptions options = new HttpClientOptions()
                .setDefaultHost(host)
                .setDefaultPort(port)
                .setSsl(ssl)
                .setConnectTimeout((int) Math.min(timeoutMs, Integer.MAX_VALUE));
        if (ssl && allowInsecure) {
            // LLM_ALLOW_INSECURE_TRANSPORT also covers a self-signed cert on an https endpoint,
            // not just plain http -- either way it's an explicit opt-out of transport trust.
            options.setTrustAll(true).setVerifyHost(false);
        }
        this.httpClient = vertx.createHttpClient(options);
    }

    /**
     * Streams a chat completion. {@code onDelta} fires on the event-loop thread once per content
     * chunk Ollama emits; {@code onComplete} fires exactly once, {@code (true, null)} on a clean
     * finish or {@code (false, message)} on any failure (network, timeout, non-200, malformed
     * stream). Queues behind the shared concurrency gate if already at
     * {@code LLM_MAX_CONCURRENT_REQUESTS}.
     */
    public void chatStream(String systemPrompt, String userPrompt, Consumer<String> onDelta,
            BiConsumer<Boolean, String> onComplete) {
        Runnable task = () -> doChatStream(systemPrompt, userPrompt, onDelta, (success, message) -> {
            release();
            onComplete.accept(success, message);
        });
        acquireOrQueue(task);
    }

    private static void acquireOrQueue(Runnable task) {
        boolean runNow;
        synchronized (GATE_LOCK) {
            runNow = active < maxConcurrentRequests;
            if (runNow) {
                active++;
            } else {
                queued.addLast(task);
            }
        }
        if (runNow) {
            task.run();
        }
    }

    private static void release() {
        Runnable next;
        synchronized (GATE_LOCK) {
            next = queued.pollFirst();
            if (next == null) {
                active--;
            }
        }
        if (next != null) {
            next.run();
        }
    }

    private void doChatStream(String systemPrompt, String userPrompt, Consumer<String> onDelta,
            BiConsumer<Boolean, String> onComplete) {
        JsonObject body = new JsonObject()
                .put("model", model)
                .put("stream", true)
                .put("keep_alive", keepAlive)
                .put("options", new JsonObject().put("num_predict", numPredict))
                .put("messages", new JsonArray()
                        .add(new JsonObject().put("role", "system").put("content", systemPrompt))
                        .add(new JsonObject().put("role", "user").put("content", userPrompt)));

        RequestOptions requestOptions = new RequestOptions()
                .setMethod(HttpMethod.POST)
                .setURI("/api/chat")
                .setTimeout(timeoutMs);

        boolean[] finished = {false};
        httpClient.request(requestOptions)
                .onFailure(err -> finishOnce(finished, onComplete, false, err.getMessage()))
                .onSuccess(request -> {
                    request.putHeader("Content-Type", "application/json");
                    request.exceptionHandler(err -> finishOnce(finished, onComplete, false, err.getMessage()));
                    request.response()
                            .onFailure(err -> finishOnce(finished, onComplete, false, err.getMessage()))
                            .onSuccess(response -> {
                                if (response.statusCode() != 200) {
                                    finishOnce(finished, onComplete, false, "Ollama returned HTTP " + response.statusCode());
                                    return;
                                }
                                response.exceptionHandler(err -> finishOnce(finished, onComplete, false, err.getMessage()));
                                StringBuilder lineBuffer = new StringBuilder();
                                response.handler(buffer -> {
                                    lineBuffer.append(buffer.toString("UTF-8"));
                                    int newlineIndex;
                                    while ((newlineIndex = lineBuffer.indexOf("\n")) >= 0) {
                                        String line = lineBuffer.substring(0, newlineIndex).trim();
                                        lineBuffer.delete(0, newlineIndex + 1);
                                        if (!line.isEmpty()) {
                                            handleLine(line, onDelta);
                                        }
                                    }
                                });
                                response.endHandler(v -> finishOnce(finished, onComplete, true, null));
                            });
                    request.end(body.encode());
                });
    }

    /** Ollama's stream is newline-delimited JSON objects, each carrying one content token under
     *  {@code message.content}; a malformed line is skipped rather than failing the whole reply. */
    private static void handleLine(String line, Consumer<String> onDelta) {
        try {
            JsonObject json = new JsonObject(line);
            JsonObject message = json.getJsonObject("message");
            if (message != null) {
                String content = message.getString("content", "");
                if (!content.isEmpty()) {
                    onDelta.accept(content);
                }
            }
        } catch (Exception ignored) {
            // Skip -- the stream continues on the next line.
        }
    }

    private static void finishOnce(boolean[] finished, BiConsumer<Boolean, String> onComplete, boolean success, String message) {
        if (finished[0]) {
            return;
        }
        finished[0] = true;
        onComplete.accept(success, message);
    }
}
