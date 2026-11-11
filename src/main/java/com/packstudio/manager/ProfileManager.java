package com.packstudio.manager;

import com.packstudio.compatibility.ProfileValidator;
import com.packstudio.core.PackStudioConstants;
import com.packstudio.model.PackEntry;
import com.packstudio.model.PackProfile;
import com.packstudio.model.PackProfile.ProfilePackEntry;
import com.packstudio.storage.JsonStorage;
import com.packstudio.storage.JsonStorage.JsonStorageException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Manages persistent resource pack profiles in {@code config/packstudio/profiles/}, supporting
 * creation, renaming, duplication, deletion, JSON import/export, duplicate detection, and
 * corrupted-profile quarantine.
 */
public final class ProfileManager {
    public record CorruptedProfileNotice(String fileName, String reason) {
    }

    public record ImportOutcome(
            boolean success,
            PackProfile profile,
            String errorMessage,
            boolean renamedDueToDuplicate
    ) {
    }

    private final Supplier<Path> profilesDirSupplier;
    private final Supplier<Path> exportsDirSupplier;
    private final ConfigManager configManager;

    private final Map<String, PackProfile> profilesById = new LinkedHashMap<>();
    private final List<CorruptedProfileNotice> corruptedNotices = new ArrayList<>();

    public ProfileManager(ConfigManager configManager) {
        this(PackStudioConstants::profilesDirectory, PackStudioConstants::exportsDirectory, configManager);
    }

    public ProfileManager(
            Supplier<Path> profilesDirSupplier,
            Supplier<Path> exportsDirSupplier,
            ConfigManager configManager
    ) {
        this.profilesDirSupplier = Objects.requireNonNull(profilesDirSupplier, "profilesDirSupplier");
        this.exportsDirSupplier = Objects.requireNonNull(exportsDirSupplier, "exportsDirSupplier");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
    }

    public synchronized void loadAll() {
        profilesById.clear();
        corruptedNotices.clear();

        Path dir = profilesDirSupplier.get();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            PackStudioConstants.LOGGER.error("Failed to create profiles directory {}", dir, e);
            return;
        }

        List<Path> jsonFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    jsonFiles.add(p);
                }
            }
        } catch (IOException e) {
            PackStudioConstants.LOGGER.error("Failed to enumerate profile files in {}", dir, e);
            return;
        }

        jsonFiles.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));

        for (Path file : jsonFiles) {
            try {
                PackProfile loaded = JsonStorage.readFile(file, PackProfile.class);
                List<String> errors = ProfileValidator.validateStructure(loaded);
                if (!errors.isEmpty()) {
                    corruptedNotices.add(new CorruptedProfileNotice(file.getFileName().toString(), errors.getFirst()));
                    JsonStorage.backupCorruptedFile(file);
                    continue;
                }
                if (profilesById.containsKey(loaded.id())) {
                    corruptedNotices.add(new CorruptedProfileNotice(
                            file.getFileName().toString(),
                            "Duplicate profile ID '" + loaded.id() + "' skipped."
                    ));
                    continue;
                }
                profilesById.put(loaded.id(), loaded);
            } catch (JsonStorageException e) {
                corruptedNotices.add(new CorruptedProfileNotice(file.getFileName().toString(), e.getMessage()));
                JsonStorage.backupCorruptedFile(file);
            }
        }
    }

    public synchronized List<PackProfile> profiles() {
        List<PackProfile> list = new ArrayList<>(profilesById.values());
        list.sort(Comparator.comparing((PackProfile p) -> p.name().toLowerCase(Locale.ROOT))
                .thenComparingLong(PackProfile::createdAt));
        return List.copyOf(list);
    }

    public synchronized Optional<PackProfile> findById(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profilesById.get(profileId));
    }

    public synchronized boolean hasProfileWithName(String candidateName, String ignoreProfileId) {
        if (candidateName == null || candidateName.isBlank()) {
            return false;
        }
        String normalized = candidateName.trim().toLowerCase(Locale.ROOT);
        for (PackProfile p : profilesById.values()) {
            if (ignoreProfileId != null && ignoreProfileId.equals(p.id())) {
                continue;
            }
            if (p.name().trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public synchronized PackProfile createFromCurrentSelection(String rawName, PackRepositoryManager repoManager) {
        String cleanName = sanitizeDisplayName(rawName);
        if (hasProfileWithName(cleanName, null)) {
            cleanName = disambiguateName(cleanName);
        }
        long now = System.currentTimeMillis();
        String id = generateUniqueId(cleanName);
        List<ProfilePackEntry> snapshot = buildSnapshotFromRepository(repoManager);

        PackProfile created = new PackProfile(id, cleanName, now, now, snapshot);
        profilesById.put(id, created);
        saveProfile(created);
        return created;
    }

    public synchronized boolean updateProfileFromCurrentSelection(String profileId, PackRepositoryManager repoManager) {
        PackProfile existing = profilesById.get(profileId);
        if (existing == null) {
            return false;
        }
        existing.setPacks(buildSnapshotFromRepository(repoManager));
        existing.setModifiedAt(System.currentTimeMillis());
        return saveProfile(existing);
    }

    public synchronized Optional<String> renameProfile(String profileId, String newName) {
        PackProfile existing = profilesById.get(profileId);
        if (existing == null) {
            return Optional.of("Profile no longer exists.");
        }
        String cleaned = newName == null ? "" : newName.trim();
        if (cleaned.isEmpty()) {
            return Optional.of("Profile name cannot be empty.");
        }
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64).trim();
        }
        if (hasProfileWithName(cleaned, profileId)) {
            return Optional.of("A profile named '" + cleaned + "' already exists.");
        }
        existing.setName(cleaned);
        existing.setModifiedAt(System.currentTimeMillis());
        if (!saveProfile(existing)) {
            return Optional.of("Failed to write updated profile to disk.");
        }
        return Optional.empty();
    }

    public synchronized Optional<PackProfile> duplicateProfile(String sourceProfileId) {
        PackProfile source = profilesById.get(sourceProfileId);
        if (source == null) {
            return Optional.empty();
        }
        String copyName = disambiguateName(source.name() + " (Copy)");
        long now = System.currentTimeMillis();
        String newId = generateUniqueId(copyName);
        PackProfile copy = new PackProfile(newId, copyName, now, now, source.packs());
        profilesById.put(newId, copy);
        saveProfile(copy);
        return Optional.of(copy);
    }

    public synchronized boolean deleteProfile(String profileId) {
        PackProfile removed = profilesById.remove(profileId);
        if (removed == null) {
            return false;
        }
        Path file = profileFilePath(profileId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            PackStudioConstants.LOGGER.warn("Failed to delete profile file {}", file, e);
        }
        if (profileId.equals(configManager.config().activeProfileId())) {
            configManager.config().setActiveProfileId("");
            configManager.save();
        }
        return true;
    }

    public synchronized Optional<String> exportProfileToJson(String profileId) {
        PackProfile profile = profilesById.get(profileId);
        if (profile == null) {
            return Optional.empty();
        }
        return Optional.of(JsonStorage.toJson(profile));
    }

    public synchronized Optional<Path> exportProfileToFile(String profileId) {
        PackProfile profile = profilesById.get(profileId);
        if (profile == null) {
            return Optional.empty();
        }
        Path exportDir = exportsDirSupplier.get();
        String stem = JsonStorage.sanitizeFileStem(profile.name() + "-" + profile.id().substring(0, Math.min(8, profile.id().length())));
        Path outFile = exportDir.resolve(stem + ".json");
        try {
            JsonStorage.writeAtomic(outFile, profile);
            return Optional.of(outFile);
        } catch (JsonStorageException e) {
            PackStudioConstants.LOGGER.error("Failed to export profile '{}' to {}", profile.name(), outFile, e);
            return Optional.empty();
        }
    }

    public synchronized ImportOutcome importProfileFromPath(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new ImportOutcome(false, null, "Import file was not found.", false);
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            return importProfileFromJson(raw);
        } catch (IOException e) {
            return new ImportOutcome(false, null, "Could not read file: " + e.getMessage(), false);
        }
    }

    public synchronized ImportOutcome importProfileFromJson(String rawJson) {
        PackProfile candidate;
        try {
            candidate = JsonStorage.fromJsonString(rawJson, PackProfile.class);
        } catch (JsonStorageException e) {
            return new ImportOutcome(false, null, e.getMessage(), false);
        }

        List<String> structuralErrors = ProfileValidator.validateStructure(candidate);
        if (!structuralErrors.isEmpty()) {
            return new ImportOutcome(false, null, structuralErrors.getFirst(), false);
        }

        boolean renamedOrReId = false;
        String finalName = sanitizeDisplayName(candidate.name());
        if (hasProfileWithName(finalName, null)) {
            finalName = disambiguateName(finalName + " (Imported)");
            renamedOrReId = true;
        }

        String finalId = JsonStorage.sanitizeFileStem(candidate.id());
        if (profilesById.containsKey(finalId)) {
            finalId = generateUniqueId(finalName);
            renamedOrReId = true;
        }

        long now = System.currentTimeMillis();
        long createdAt = candidate.createdAt() > 0L ? candidate.createdAt() : now;
        PackProfile imported = new PackProfile(finalId, finalName, createdAt, now, candidate.packs());
        profilesById.put(finalId, imported);
        if (!saveProfile(imported)) {
            profilesById.remove(finalId);
            return new ImportOutcome(false, null, "Failed to save imported profile to disk.", false);
        }
        return new ImportOutcome(true, imported, null, renamedOrReId);
    }

    public synchronized List<CorruptedProfileNotice> corruptedNotices() {
        return List.copyOf(corruptedNotices);
    }

    public Path profilesDirectory() {
        return profilesDirSupplier.get();
    }

    public Path exportsDirectory() {
        return exportsDirSupplier.get();
    }

    private boolean saveProfile(PackProfile profile) {
        Path file = profileFilePath(profile.id());
        try {
            JsonStorage.writeAtomic(file, profile);
            return true;
        } catch (JsonStorageException e) {
            PackStudioConstants.LOGGER.error("Failed to save profile '{}' to {}", profile.name(), file, e);
            return false;
        }
    }

    private Path profileFilePath(String profileId) {
        String safeStem = JsonStorage.sanitizeFileStem(profileId);
        return profilesDirSupplier.get().resolve(safeStem + ".json");
    }

    private List<ProfilePackEntry> buildSnapshotFromRepository(PackRepositoryManager repoManager) {
        List<PackEntry> allPacks = repoManager.allPacks();
        Map<String, PackEntry> byId = new LinkedHashMap<>();
        for (PackEntry p : allPacks) {
            byId.put(p.id(), p);
        }

        List<ProfilePackEntry> entries = new ArrayList<>();
        Set<String> recorded = new LinkedHashSet<>();

        // 1. Enabled packs in exact top-to-bottom priority order
        for (String enabledId : repoManager.stagedEnabledTopDownIds()) {
            PackEntry meta = byId.get(enabledId);
            String titleHint = meta != null ? meta.displayName() : enabledId;
            entries.add(new ProfilePackEntry(enabledId, titleHint, true));
            recorded.add(enabledId);
        }

        // 2. Remaining known disabled packs for explicit disabled-state tracking
        for (PackEntry entry : allPacks) {
            if (recorded.add(entry.id())) {
                entries.add(new ProfilePackEntry(entry.id(), entry.displayName(), false));
            }
        }
        return entries;
    }

    private String generateUniqueId(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) {
            slug = "preset";
        }
        if (slug.length() > 24) {
            slug = slug.substring(0, 24);
        }
        String candidate;
        do {
            candidate = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        } while (profilesById.containsKey(candidate));
        return candidate;
    }

    private String sanitizeDisplayName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "New Preset";
        }
        String trimmed = rawName.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64).trim() : trimmed;
    }

    private String disambiguateName(String baseName) {
        if (!hasProfileWithName(baseName, null)) {
            return baseName;
        }
        int suffix = 2;
        while (true) {
            String attempt = baseName + " " + suffix;
            if (!hasProfileWithName(attempt, null)) {
                return attempt;
            }
            suffix++;
        }
    }
}
