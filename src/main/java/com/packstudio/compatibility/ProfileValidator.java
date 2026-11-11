package com.packstudio.compatibility;

import com.packstudio.core.PackStudioConstants;
import com.packstudio.model.PackEntry;
import com.packstudio.model.PackProfile;
import com.packstudio.model.PackProfile.ProfilePackEntry;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates {@link PackProfile} payloads from disk or JSON import, detecting missing packs,
 * mismatched formats, invalid schema versions, absolute-path misuse, and duplicate entries.
 */
public final class ProfileValidator {
    public record MissingPackNotice(String packId, String displayNameHint, boolean wasEnabled) {
        public String label() {
            if (displayNameHint != null && !displayNameHint.isBlank() && !displayNameHint.equals(packId)) {
                return displayNameHint + " (" + packId + ")";
            }
            return packId;
        }
    }

    public record ProfileHealth(
            boolean structurallyValid,
            List<String> structuralErrors,
            List<MissingPackNotice> missingPacks,
            List<String> incompatibleEnabledPacks
    ) {
        public boolean hasWarnings() {
            return !missingPacks.isEmpty() || !incompatibleEnabledPacks.isEmpty();
        }
    }

    private ProfileValidator() {
    }

    /**
     * Validates structural integrity of a deserialized {@link PackProfile} before accepting it into memory.
     */
    public static List<String> validateStructure(PackProfile profile) {
        List<String> errors = new ArrayList<>();
        if (profile == null) {
            errors.add("Profile object is null.");
            return List.copyOf(errors);
        }
        if (profile.schemaVersion() <= 0 || profile.schemaVersion() > PackStudioConstants.PROFILE_SCHEMA_VERSION) {
            errors.add("Unsupported profile schemaVersion: " + profile.schemaVersion());
        }
        if (profile.id() == null || profile.id().isBlank()) {
            errors.add("Profile is missing a unique 'id'.");
        }
        if (profile.name() == null || profile.name().isBlank()) {
            errors.add("Profile is missing a 'name'.");
        }
        if (profile.packs() == null) {
            errors.add("Profile 'packs' list is missing.");
        } else {
            Set<String> seenIds = new LinkedHashSet<>();
            for (ProfilePackEntry entry : profile.packs()) {
                if (entry == null || entry.packId().isBlank()) {
                    errors.add("Profile contains a pack entry with an empty packId.");
                    continue;
                }
                if (looksLikeAbsoluteFilesystemPath(entry.packId())) {
                    errors.add("Pack entry '" + entry.packId() + "' uses an absolute path instead of a PackRepository ID.");
                }
                if (!seenIds.add(entry.packId())) {
                    errors.add("Duplicate pack reference '" + entry.packId() + "' inside profile.");
                }
            }
        }
        return List.copyOf(errors);
    }

    /**
     * Evaluates a profile against currently discovered {@link PackEntry}s from {@code PackRepository}.
     */
    public static ProfileHealth evaluateAgainstLibrary(PackProfile profile, Collection<PackEntry> discoveredPacks) {
        List<String> structural = validateStructure(profile);
        if (!structural.isEmpty()) {
            return new ProfileHealth(false, structural, List.of(), List.of());
        }

        Map<String, PackEntry> byId = new LinkedHashMap<>();
        if (discoveredPacks != null) {
            for (PackEntry entry : discoveredPacks) {
                byId.put(entry.id(), entry);
            }
        }

        List<MissingPackNotice> missing = new ArrayList<>();
        List<String> incompatible = new ArrayList<>();

        for (ProfilePackEntry ref : profile.packs()) {
            PackEntry found = byId.get(ref.packId());
            if (found == null) {
                if (ref.enabled()) {
                    missing.add(new MissingPackNotice(ref.packId(), ref.displayNameHint(), true));
                }
            } else if (ref.enabled() && !found.compatibility().isCompatible()) {
                incompatible.add(found.displayName());
            }
        }

        return new ProfileHealth(true, List.of(), List.copyOf(missing), List.copyOf(incompatible));
    }

    private static boolean looksLikeAbsoluteFilesystemPath(String packId) {
        if (packId.startsWith("/") || packId.startsWith("\\\\") || packId.matches("^[A-Za-z]:[\\\\/].*")) {
            return true;
        }
        if (packId.startsWith("file/")) {
            return false;
        }
        try {
            return Paths.get(packId).isAbsolute();
        } catch (InvalidPathException ignored) {
            return false;
        }
    }
}
