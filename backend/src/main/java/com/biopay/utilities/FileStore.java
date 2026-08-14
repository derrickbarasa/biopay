package com.biopay.utilities;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;

/**
 * Local disk store for beneficiary images (spec: validate JPG/PNG, max 5MB,
 * randomized filename, served only through the API -- never a direct file
 * path). Swappable for object storage later behind the same two methods.
 */
public final class FileStore {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private FileStore() {
    }

    private static Path uploadsDir() {
        Dotenv dotenv = Dotenv.load();
        Path dir = Path.of(System.getProperty("user.dir"), dotenv.get("UPLOADS_PATH", "uploads"));
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create uploads directory", ex);
        }
        return dir;
    }

    /**
     * Decodes and stores a base64 image, returning the randomized filename
     * to persist in {@code photo_url} (never the client-supplied name).
     */
    public static String saveBase64Image(String base64Data, String declaredExtension) {
        String ext = declaredExtension == null ? "" : declaredExtension.trim().toLowerCase().replace(".", "");
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Only JPG/PNG images are allowed");
        }
        Dotenv dotenv = Dotenv.load();
        long maxBytes = Long.parseLong(dotenv.get("MAX_UPLOAD_BYTES", "5242880"));

        byte[] bytes;
        try {
            String cleaned = base64Data.contains(",") ? base64Data.substring(base64Data.indexOf(',') + 1) : base64Data;
            bytes = Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid base64 image data");
        }
        if (bytes.length == 0 || bytes.length > maxBytes) {
            throw new IllegalArgumentException("Image must be non-empty and at most " + (maxBytes / (1024 * 1024)) + "MB");
        }

        String filename = Utilities.newUuid().replace("-", "") + "." + ext;
        try {
            Files.write(uploadsDir().resolve(filename), bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store image", ex);
        }
        return filename;
    }

    /** Resolves a stored filename back to bytes for the authenticated file-serving route. */
    public static byte[] read(String filename) throws IOException {
        Path safe = uploadsDir().resolve(filename).normalize();
        if (!safe.startsWith(uploadsDir())) {
            throw new SecurityException("Invalid filename");
        }
        return Files.readAllBytes(safe);
    }
}
