package com.biopay.utilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local disk store for the downloadable Android agent APKs, served publicly (no login required --
 * a field officer needs to install the app before they can ever sign in) from a fixed, well-known
 * filename per device flavor. Drop a freshly built APK in this directory under the matching name
 * below to publish a new release; there is no upload endpoint, by design.
 *
 * <p>Each APK has an optional {@code <filename>.version.json} sidecar (e.g.
 * {@code biopay-agent-morpho642.apk.version.json}) that the installed app polls to learn a newer
 * build exists -- see {@code AppUpdateManager} on the mobile side. It is served verbatim, so its
 * shape is whatever the app expects: {@code {"versionCode":3,"versionName":"1.2","notes":"..."}}.
 * Missing means "no update information published yet", not an error.
 */
public final class AppReleaseStore {

    /** Keyed by the mobile Gradle module's `flavorDimensions("biometricDevice")` product flavors. */
    public static final String MORPHO_642_FILENAME = "biopay-agent-morpho642.apk";
    public static final String MORPHO_615_FILENAME = "biopay-agent-morpho615.apk";

    private AppReleaseStore() {
    }

    private static Path releasesDir() {
        Path dir = Path.of(System.getProperty("user.dir"), Env.get().get("APP_RELEASES_PATH", "app-releases"));
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create app-releases directory", ex);
        }
        return dir;
    }

    /** Resolves a requested filename to bytes, refusing anything outside the releases directory. */
    public static byte[] read(String filename) throws IOException {
        Path safe = releasesDir().resolve(filename).normalize();
        if (!safe.startsWith(releasesDir())) {
            throw new SecurityException("Invalid filename");
        }
        return Files.readAllBytes(safe);
    }

    public static boolean exists(String filename) {
        Path safe = releasesDir().resolve(filename).normalize();
        return safe.startsWith(releasesDir()) && Files.isRegularFile(safe);
    }

    private static String versionInfoFilename(String apkFilename) {
        return apkFilename + ".version.json";
    }

    public static boolean versionInfoExists(String apkFilename) {
        return exists(versionInfoFilename(apkFilename));
    }

    public static byte[] readVersionInfo(String apkFilename) throws IOException {
        return read(versionInfoFilename(apkFilename));
    }
}
