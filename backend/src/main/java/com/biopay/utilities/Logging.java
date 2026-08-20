package com.biopay.utilities;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logging {

    private static String LOGS_PATH = "";

    public static void applicationLog(String details, String uniqueId, int logLevel) {
        LOGS_PATH = System.getProperty("user.dir") + File.separator + Env.get().get("LOGS_PATH", "logs").trim();

        String typeOfLog;
        switch (logLevel) {
            case 1: typeOfLog = "REQUESTS"; break;
            case 2: typeOfLog = "RESPONSES"; break;
            case 3: typeOfLog = "ERRORS"; break;
            case 4: typeOfLog = "SECURITY"; break;
            case 5: typeOfLog = "WEBHOOK_RESULTS"; break;
            default: typeOfLog = "OTHERS"; break;
        }

        Date today = new Date();
        String logDate = new SimpleDateFormat("yyyy-MM-dd").format(today);
        String logTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(today);
        File dir = new File(LOGS_PATH + File.separator + logDate + File.separator + typeOfLog);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = File.separator + typeOfLog + "-" + logDate + ".log";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dir + fileName, true))) {
            writer.write(logTime + " ~ " + details);
            writer.newLine();
        } catch (IOException e) {
            System.err.println(logPreString() + "ERROR IN LOGS:-" + e.getMessage());
        }
    }

    public static String logPreString() {
        return "BIOPAY | " + Thread.currentThread().getStackTrace()[2].getClassName() + " | "
                + Thread.currentThread().getStackTrace()[2].getLineNumber() + " | "
                + Thread.currentThread().getStackTrace()[2].getMethodName() + "() | ";
    }
}
