package com.biopay.utilities;

import io.vertx.core.Future;
import io.vertx.mssqlclient.MSSQLPool;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

public class Utilities {

    public static String nowDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public static boolean isValidDate(String format, String value) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            sdf.setLenient(false);
            Date date = sdf.parse(value);
            return value.equals(sdf.format(date));
        } catch (Exception ex) {
            return false;
        }
    }

    public static String generateRandomPassword(int len) {
        String capitalCaseLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCaseLetters = "abcdefghijklmnopqrstuvwxyz";
        String specialCharacters = "!@#$*";
        String numbers = "1234567890";
        String combinedChars = lowerCaseLetters + capitalCaseLetters + numbers + specialCharacters;
        Random random = new Random();
        char[] password = new char[len];

        password[0] = lowerCaseLetters.charAt(random.nextInt(lowerCaseLetters.length()));
        password[1] = capitalCaseLetters.charAt(random.nextInt(capitalCaseLetters.length()));
        password[2] = specialCharacters.charAt(random.nextInt(specialCharacters.length()));
        password[3] = numbers.charAt(random.nextInt(numbers.length()));

        for (int i = 4; i < len; i++) {
            password[i] = combinedChars.charAt(random.nextInt(combinedChars.length()));
        }
        return String.valueOf(password);
    }

    /** 6-digit numeric OTP for email/TOTP challenges. */
    public static String generateOtp() {
        Random random = new Random();
        return String.valueOf(100000 + random.nextInt(900000));
    }

    public static String newUuid() {
        return UUID.randomUUID().toString();
    }

    /** e.g. prefix "PAYROLL" -> "PAYROLL-20260812-4F3A9C" */
    public static String generateCode(String prefix) {
        String datePart = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String randPart = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return prefix + "-" + datePart + "-" + randPart;
    }

    /** Next sequential anchor code, e.g. "ANC001", "ANC002" -- an anchor's own
     *  identity now lives on its Anchor Administrator's row in `users`
     *  (anchor_code), not in a separate anchors table. */
    public static Future<String> nextAnchorCode(MSSQLPool pool) {
        return pool.query("SELECT MAX(TRY_CAST(SUBSTRING(anchor_code, 4, 10) AS INT)) AS mx FROM users WHERE anchor_code LIKE 'ANC%'")
                .execute()
                .map(rows -> {
                    Integer max = rows.size() == 0 ? null : rows.iterator().next().getInteger("mx");
                    int next = (max == null ? 0 : max) + 1;
                    return String.format("ANC%03d", next);
                });
    }
}
