package com.biopay.adaptors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DelimitedStreamSplitterTest {

    private static void feed(DelimitedStreamSplitter splitter, String... deltas) {
        for (String delta : deltas) {
            splitter.accept(delta);
        }
        splitter.finish();
    }

    @Test void splitsOnTheMetaDelimiter() {
        DelimitedStreamSplitter splitter = new DelimitedStreamSplitter(d -> { });
        feed(splitter, "We have 12 households.", "@@META@@", "{}");

        assertEquals("We have 12 households.", splitter.reply());
        assertEquals("{}", splitter.metaJson());
    }

    @Test void delimiterSplitAcrossDeltasIsStillDetected() {
        DelimitedStreamSplitter splitter = new DelimitedStreamSplitter(d -> { });
        feed(splitter, "Hello", "@@ME", "TA@@", "{\"loginIntent\":true}");

        assertEquals("Hello", splitter.reply());
        assertEquals("{\"loginIntent\":true}", splitter.metaJson());
    }

    @Test void treatsAMarkdownJsonFenceAsAnEquivalentDelimiter() {
        // Observed live against qwen2.5:3b: instead of the requested "@@META@@" token, the model
        // sometimes wraps the same trailing payload in a ```json fence. Regression test for the
        // 2026-09-22 bug where that fence leaked straight into the visible dashboard chat reply.
        DelimitedStreamSplitter splitter = new DelimitedStreamSplitter(d -> { });
        feed(splitter, "We have 12 households.", "\n\n```json\n{}\n```");

        assertEquals("We have 12 households.", splitter.reply());
        assertEquals("{}", splitter.metaJson());
    }

    @Test void fallsBackToPlainReplyWhenNoDelimiterIsEverSent() {
        DelimitedStreamSplitter splitter = new DelimitedStreamSplitter(d -> { });
        feed(splitter, "Just a plain reply with no meta block.");

        assertEquals("Just a plain reply with no meta block.", splitter.reply());
        assertEquals("", splitter.metaJson());
    }
}
