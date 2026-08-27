package com.biopay.utilities;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Branded HTML shell for transactional email. Table-based layout and inline styles throughout --
 * the only markup that renders consistently across Outlook's Word engine, Gmail and mobile mail
 * apps, none of which reliably support a linked/embedded stylesheet.
 *
 * <p>The logo ships as a {@code cid:} inline attachment (see {@link #logoInlineImages()}), not a
 * linked URL or a base64 data URI -- both were tried and both failed against a real inbox. A
 * linked URL depended on {@code FRONTEND_BASE_URL}, which is {@code localhost} in every
 * environment this has run in so far and unreachable by any recipient's mail server. A data URI
 * loads fine in a browser (data URIs are just HTML) but Gmail's and Outlook.com's HTML sanitizer
 * strips {@code src="data:..."} from mail specifically, so the image never rendered there even
 * though nothing else was wrong with it. {@code cid:} is the one embedding method the MIME email
 * spec itself defines, so every client supports it. {@link com.biopay.services.Notification}
 * forwards {@link #logoInlineImages()}'s JsonArray to ZeptoMail's {@code inline_images} field
 * verbatim.
 *
 * <p>The banner is a solid brand teal, not the app's dark navy -- deliberately not gradient and
 * not the exact hex the CSS elsewhere uses, but the literal color sampled back out of the
 * rasterized logo PNG, so the banner and the logo's own background meet with a truly invisible
 * seam instead of an off-by-a-shade one. Below the banner the template stays light-only with
 * explicit {@code color-scheme}/{@code bgcolor} hints, because a dark section inside an
 * otherwise-light email is exactly what triggers Gmail's/Outlook's forced dark-mode repaint into
 * a jarring block -- also confirmed against a real inbox, before the banner treatment existed.
 */
public final class EmailTemplates {

    private static final String LOGO_CID = "biopay-logo";
    private static final String BANNER_COLOR = "#0c9488";
    private static final String LOGO_BASE64 = loadLogoBase64();

    private EmailTemplates() {
    }

    private static String loadLogoBase64() {
        try (InputStream in = EmailTemplates.class.getResourceAsStream("/email/biopay-logo-banner.png")) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException ex) {
            return null;
        }
    }

    /** The {@code inline_images} array to attach alongside any email built with {@link #shell}. */
    public static JsonArray logoInlineImages() {
        if (LOGO_BASE64 == null) {
            return new JsonArray();
        }
        return new JsonArray().add(new JsonObject()
                .put("cid", LOGO_CID)
                .put("mime_type", "image/png")
                .put("content", LOGO_BASE64));
    }

    /**
     * A verification code, presented as one selectable block (not per-digit boxes) so a
     * double-click grabs the whole code as a single word on desktop, and a long-press does the
     * same on mobile -- CSS {@code letter-spacing} supplies the visual gaps without inserting
     * actual space characters that would split the selection.
     */
    public static String otpEmail(String code, int expiryMinutes) {
        String body =
                "<h1 style=\"margin:0 0 10px;font-size:21px;line-height:1.35;color:#0f172a;font-weight:700;\">"
                        + "Verify it's you</h1>"
                        + "<p style=\"margin:0 0 28px;font-size:14px;line-height:1.6;color:#64748b;\">"
                        + "Enter this code to finish signing in to BioPay. It's ready to copy below.</p>"
                        + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" align=\"center\" "
                        + "style=\"margin:0 auto 28px;\"><tr><td align=\"center\" bgcolor=\"#f0fdfa\" "
                        + "style=\"background-color:#f0fdfa;border:1.5px solid #99e6da;border-radius:12px;"
                        + "padding:18px 28px;\">"
                        + "<span style=\"display:inline-block;font-family:'Courier New',Courier,monospace;"
                        + "font-size:34px;font-weight:700;letter-spacing:10px;color:#0f172a;\">"
                        + code
                        + "</span>"
                        + "</td></tr></table>"
                        + "<p style=\"margin:0 0 6px;font-size:13px;line-height:1.6;color:#64748b;\">"
                        + "This code expires in " + expiryMinutes + " minutes and can only be used once.</p>"
                        + "<p style=\"margin:0;font-size:13px;line-height:1.6;color:#64748b;\">"
                        + "Didn't try to sign in? You can safely ignore this email &mdash; your password hasn't been "
                        + "shared, and no one can access your account without this code.</p>";

        return shell(body);
    }

    /** Wraps arbitrary body HTML in the branded header/footer used by every BioPay email. */
    private static String shell(String bodyHtml) {
        int year = java.time.Year.now().getValue();
        String logoImg = LOGO_BASE64 != null
                ? "<img src=\"cid:" + LOGO_CID + "\" width=\"260\" alt=\"BioPay\" "
                        + "style=\"display:block;width:260px;height:auto;border:0;outline:none;\">"
                : "<span style=\"font-size:24px;font-weight:800;color:#ffffff;\">BioPay</span>";

        return "<!doctype html>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
                + "<head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light\">"
                + "<meta name=\"supported-color-schemes\" content=\"light\">"
                + "<title>BioPay</title>"
                + "</head>"
                + "<body style=\"margin:0;padding:0;background-color:#f1f5f9;"
                + "font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#f1f5f9\" "
                + "style=\"background-color:#f1f5f9;\"><tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#ffffff\" "
                + "style=\"max-width:480px;width:100%;background-color:#ffffff;border:1px solid #e6ebf0;"
                + "border-radius:16px;overflow:hidden;\">"
                + "<tr><td align=\"center\" bgcolor=\"" + BANNER_COLOR + "\" "
                + "style=\"background-color:" + BANNER_COLOR + ";padding:32px 24px;\">" + logoImg + "</td></tr>"
                + "<tr><td style=\"padding:32px 32px 28px;text-align:center;\">" + bodyHtml + "</td></tr>"
                + "<tr><td style=\"padding:20px 32px;background-color:#f8fafc;border-top:1px solid #e6ebf0;"
                + "text-align:center;\" bgcolor=\"#f8fafc\">"
                + "<p style=\"margin:0 0 4px;font-size:12px;font-weight:600;color:#334155;\">BioPay</p>"
                + "<p style=\"margin:0;font-size:11px;line-height:1.6;color:#94a3b8;\">Biometric payment &amp; payroll solutions"
                + "<br>&copy; " + year + " BioPay. This is an automated message, please don't reply to it.</p>"
                + "</td></tr>"
                + "</table></td></tr></table>"
                + "</body></html>";
    }
}
