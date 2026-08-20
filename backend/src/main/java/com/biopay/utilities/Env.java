package com.biopay.utilities;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Cached access to the parsed {@code .env} file. {@link Dotenv#load()} itself is not cached --
 * every call re-opens and re-parses the file from disk (see {@code DotenvBuilder.load()}) -- so
 * calling it per-request from an event-loop thread (as {@link Auth}, {@link Logging} and others
 * used to) blocks that thread on disk I/O for every login/error/audit write, stalling every other
 * request scheduled on the same event loop. Load once, reuse the immutable result.
 */
public final class Env {

    private static volatile Dotenv dotenv;

    private Env() {
    }

    public static Dotenv get() {
        if (dotenv == null) {
            synchronized (Env.class) {
                if (dotenv == null) {
                    dotenv = Dotenv.load();
                }
            }
        }
        return dotenv;
    }
}
