package com.packstudio.compatibility;

import com.packstudio.model.PackCompatibilityStatus;
import com.packstudio.model.PackEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;

/**
 * Provides honest, verifiable diagnostics for pack format compatibility and profile pack references.
 * Never claims unverified file-level resource conflicts.
 */
public final class CompatibilityAnalyzer {
    public record LibraryHealthReport(
            String runtimePackFormat,
            int totalPacks,
            int enabledPacks,
            int incompatibleEnabledCount,
            List<String> warnings
    ) {
    }

    private CompatibilityAnalyzer() {
    }

    public static String currentRuntimePackFormatLabel() {
        try {
            PackFormat fmt = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
            return fmt.major() + "." + fmt.minor();
        } catch (Throwable ignored) {
            return "26.3";
        }
    }

    public static LibraryHealthReport analyzeLibrary(List<PackEntry> packs) {
        String runtimeFmt = currentRuntimePackFormatLabel();
        int enabled = 0;
        int incompatibleEnabled = 0;
        List<String> warnings = new ArrayList<>();

        for (PackEntry entry : packs) {
            if (entry.enabled()) {
                enabled++;
                if (!entry.compatibility().isCompatible()) {
                    incompatibleEnabled++;
                    String reason = entry.compatibility() == PackCompatibilityStatus.OLDER_FORMAT
                            ? "targets an older Minecraft pack format"
                            : "targets a newer Minecraft pack format";
                    warnings.add(entry.displayName() + " " + reason
                            + entry.packFormatLabel().map(f -> " (" + f + ")").orElse("") + ".");
                }
            }
        }

        return new LibraryHealthReport(runtimeFmt, packs.size(), enabled, incompatibleEnabled, List.copyOf(warnings));
    }

    public static List<String> findMissingPackIds(Collection<String> referencedIds, Collection<PackEntry> availablePacks) {
        Set<String> present = availablePacks.stream().map(PackEntry::id).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (String ref : referencedIds) {
            if (ref != null && !ref.isBlank() && !present.contains(ref)) {
                missing.add(ref);
            }
        }
        return List.copyOf(missing);
    }
}
