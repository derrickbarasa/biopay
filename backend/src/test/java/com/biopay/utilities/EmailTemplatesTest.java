package com.biopay.utilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplatesTest {

    @Test
    void approvalEmailEscapesUserControlledContentAndKeepsTheActionLink() {
        String html = EmailTemplates.approvalRequestEmail(
                "Amina <Reviewer>",
                "Approve & release",
                "PAY<123>",
                "5 households & one alternate",
                "https://example.test/approval?token=abc&source=email",
                24);

        assertTrue(html.contains("Amina &lt;Reviewer&gt;"));
        assertTrue(html.contains("Approve &amp; release"));
        assertTrue(html.contains("PAY&lt;123&gt;"));
        assertTrue(html.contains("5 households &amp; one alternate"));
        assertTrue(html.contains("https://example.test/approval?token=abc&amp;source=email"));
        assertTrue(html.contains("Review and approve"));
        assertTrue(html.contains("expires in 24 hours"));
        assertFalse(html.contains("Amina <Reviewer>"));
    }
}
