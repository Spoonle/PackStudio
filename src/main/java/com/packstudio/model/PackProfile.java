package com.packstudio.model;

import com.packstudio.core.PackStudioConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Schema-versioned resource pack profile stored in {@code config/packstudio/profiles/<id>.json}.
 * Uses portable {@code PackRepository} identifiers rather than machine-specific absolute paths.
 */
public final class PackProfile {
    public record ProfilePackEntry(
            String packId,
            String displayNameHint,
            boolean enabled
    ) {
        public ProfilePackEntry {
            packId = packId == null ? "" : packId.trim();
            displayNameHint = displayNameHint == null ? "" : displayNameHint.trim();
        }
    }

    private int schemaVersion = PackStudioConstants.PROFILE_SCHEMA_VERSION;
    private String id;
    private String name;
    private long createdAt;
    private long modifiedAt;
    private List<ProfilePackEntry> packs = new ArrayList<>();

    public PackProfile() {
    }

    public PackProfile(
            String id,
            String name,
            long createdAt,
            long modifiedAt,
            List<ProfilePackEntry> packs
    ) {
        this.schemaVersion = PackStudioConstants.PROFILE_SCHEMA_VERSION;
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.packs = new ArrayList<>(packs == null ? List.of() : packs);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long createdAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long modifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(long modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public List<ProfilePackEntry> packs() {
        return packs == null ? List.of() : List.copyOf(packs);
    }

    public void setPacks(List<ProfilePackEntry> packs) {
        this.packs = new ArrayList<>(packs == null ? List.of() : packs);
    }

    /**
     * Returns enabled pack IDs in exact saved top-to-bottom priority order.
     */
    public List<String> enabledPackIdsOrdered() {
        if (packs == null) {
            return List.of();
        }
        List<String> ordered = new ArrayList<>();
        for (ProfilePackEntry entry : packs) {
            if (entry != null && entry.enabled() && !entry.packId().isEmpty() && !ordered.contains(entry.packId())) {
                ordered.add(entry.packId());
            }
        }
        return List.copyOf(ordered);
    }

    public int enabledCount() {
        return enabledPackIdsOrdered().size();
    }
}
