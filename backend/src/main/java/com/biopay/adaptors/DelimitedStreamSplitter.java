package com.biopay.adaptors;

import java.util.function.Consumer;

/**
 * Splits a raw LLM token stream into the reply text (forwarded to {@code onDelta} as it arrives)
 * and the trailing {@code @@META@@<json>} block the chat system prompts ask the model to append.
 * The delimiter can land split across two separate deltas, so up to {@code DELIMITER.length() - 1}
 * trailing characters are always held back until either more text rules the delimiter out or the
 * stream ends -- never forwarded, never dropped.
 */
public class DelimitedStreamSplitter {

    private static final String DELIMITER = "@@META@@";

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
        int delimiterIndex = pending.indexOf(DELIMITER);
        if (delimiterIndex >= 0) {
            String before = pending.substring(0, delimiterIndex);
            emit(before);
            meta.append(pending.substring(delimiterIndex + DELIMITER.length()));
            metaStarted = true;
            pending.setLength(0);
            return;
        }
        int safeLength = Math.max(0, pending.length() - (DELIMITER.length() - 1));
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
        return meta.toString().trim();
    }
}
