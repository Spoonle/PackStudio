package com.packstudio.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.packstudio.core.PackStudioConstants;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Thread-safe JSON persistence helper with atomic file replacement and corrupted-file quarantine.
 */
public final class JsonStorage {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private JsonStorage() {
    }

    public static Gson gson() {
        return GSON;
    }

    public static String toJson(Object value) {
        return GSON.toJson(Objects.requireNonNull(value, "value"));
    }

    public static <T> T fromJsonString(String rawJson, Class<T> type) throws JsonStorageException {
        if (rawJson == null || rawJson.isBlank()) {
            throw new JsonStorageException("JSON payload is empty.");
        }
        try {
            JsonElement parsed = JsonParser.parseString(rawJson);
            if (!parsed.isJsonObject()) {
                throw new JsonStorageException("Expected a JSON object root.");
            }
            T instance = GSON.fromJson(parsed, type);
            if (instance == null) {
                throw new JsonStorageException("Deserialized JSON produced null.");
            }
            return instance;
        } catch (JsonParseException | IllegalStateException e) {
            throw new JsonStorageException("Malformed JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Reads a JSON file into {@code type}. Throws {@link JsonStorageException} if unreadable or malformed.
     */
    public static <T> T readFile(Path file, Class<T> type) throws JsonStorageException {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw new JsonStorageException("File does not exist: " + file);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new JsonStorageException("Invalid JSON root in " + file.getFileName());
            }
            T result = GSON.fromJson(parsed, type);
            if (result == null) {
                throw new JsonStorageException("Empty JSON object in " + file.getFileName());
            }
            return result;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            throw new JsonStorageException("Failed to read JSON file " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Atomically writes {@code value} as UTF-8 JSON to {@code targetFile} via a sibling temporary file.
     */
    public static synchronized void writeAtomic(Path targetFile, Object value) throws JsonStorageException {
        Objects.requireNonNull(targetFile, "targetFile");
        Objects.requireNonNull(value, "value");

        Path parent = targetFile.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new JsonStorageException("Cannot resolve parent directory for " + targetFile);
        }

        Path tempFile = null;
        try {
            Files.createDirectories(parent);
            String json = GSON.toJson(value) + System.lineSeparator();
            tempFile = Files.createTempFile(parent, targetFile.getFileName().toString() + ".", ".tmp");
            Files.writeString(
                    tempFile,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            try {
                Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupError) {
                    PackStudioConstants.LOGGER.debug("Failed to clean up temp file {}", tempFile, cleanupError);
                }
            }
            throw new JsonStorageException("Failed to write JSON file " + targetFile.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Moves a corrupted file aside to a timestamped {@code .corrupt.bak} backup so user data is never silently destroyed.
     */
    public static Path backupCorruptedFile(Path brokenFile) {
        if (brokenFile == null || !Files.exists(brokenFile)) {
            return null;
        }
        Path backup = brokenFile.resolveSibling(
                brokenFile.getFileName().toString() + ".corrupt." + System.currentTimeMillis() + ".bak"
        );
        try {
            return Files.move(brokenFile, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            PackStudioConstants.LOGGER.warn("Could not quarantine corrupted file {}", brokenFile, e);
            return null;
        }
    }

    /**
     * Sanitizes an untrusted filename stem so paths cannot escape the target directory.
     */
    public static String sanitizeFileStem(String raw) {
        if (raw == null || raw.isBlank()) {
            return "profile";
        }
        String cleaned = raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_");
        }
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("_")) {
            return "profile";
        }
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    public static final class JsonStorageException extends Exception {
        private static final long serialVersionUID = 1L;
        public JsonStorageException(String message) {
            super(message);
        }

        public JsonStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
