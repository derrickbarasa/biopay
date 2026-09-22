package com.biopay.adaptors;

import java.util.function.Consumer;

/**
 * Splits a raw LLM token stream into the reply text (forwarded to {@code onDelta} as it arrives)
 * and the trailing {@code @@META@@<json>} block the chat system prompts ask the model to append.
 * A delimiter can land split across two separate deltas, so up to {@code longest delimiter length
 * - 1} trailing characters are always held back until either more text rules every delimiter out
 * or the stream ends -- never forwarded, never dropped.
 *
 * <p>The small model this project runs (qwen2.5:3b) doesn't always emit the exact {@code @@META@@}
 * token -- observed live, it sometimes wraps the same trailing {@code {}}/{@code {"loginIntent":
 * true}} payload in a Markdown fence ({@code ```json ... ```}) instead, which would otherwise leak
 * straight into the visible reply. {@code ```json} is accepted as a second, equivalent delimiter
 * for that reason; {@link #metaJson()} strips the closing fence before returning.
 */
public class DelimitedStreamSplitter {

    private static final String[] DELIMITERS = { "@@META@@", "```json" };
    private static final int MAX_DELIMITER_LENGTH =
            java.util.Arrays.stream(DELIMITERS).mapToInt(String::length).max().orElse(0);

    private final Consumer<String> onDelta;
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder reply = new StringBuilder();
    private final StringBuilder meta = new StringBuilder();
    private boolean metaStarted = false;

    public DelimitedStreamSplitter(Consumer<String> onDelta) {
        this.onDelta = onDelta;
    }

    public void accept(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        if (metaStarted) {
            meta.append(delta);
            return;
        }
        pending.append(delta);
        int delimiterIndex = -1;
        int delimiterLength = 0;
        for (String delimiter : DELIMITERS) {
            int index = pending.indexOf(delimiter);
            if (index >= 0 && (delimiterIndex == -1 || index < delimiterIndex)) {
                delimiterIndex = index;
                delimiterLength = delimiter.length();
            }
        }
        if (delimiterIndex >= 0) {
            String before = pending.substring(0, delimiterIndex);
            emit(before);
            meta.append(pending.substring(delimiterIndex + delimiterLength));
            metaStarted = true;
            pending.setLength(0);
            return;
        }
        int safeLength = Math.max(0, pending.length() - (MAX_DELIMITER_LENGTH - 1));
        if (safeLength > 0) {
            emit(pending.substring(0, safeLength));
            pending.delete(0, safeLength);
        }
    }

    /** Call once the underlying stream has ended -- flushes anything still held back, which
     *  matters when the model never actually emitted a {@code @@META@@} line. */
    public void finish() {
        if (!metaStarted && pending.length() > 0) {
            emit(pending.toString());
            pending.setLength(0);
        }
    }

    private void emit(String text) {
        if (text.isEmpty()) {
            return;
        }
        reply.append(text);
        onDelta.accept(text);
    }

    public String reply() {
        return reply.toString().trim();
    }

    public String metaJson() {
        String text = meta.toString().trim();
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3).trim();
        }
        return text;
    }
}
