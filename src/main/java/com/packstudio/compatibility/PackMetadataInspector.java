package com.packstudio.compatibility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.packstudio.compat.MinecraftCompatProvider;
import com.packstudio.core.PackStudioConstants;
import com.packstudio.model.PackSourceKind;
import com.packstudio.util.TextFormatUtil;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.packs.repository.Pack;

/**
 * Reads genuine supplementary metadata from a pack's {@code pack.mcmeta} without ever fabricating
 * missing fields or modifying user pack archives.
 */
public final class PackMetadataInspector {
    public record InspectedMetadata(
            Optional<String> author,
            Optional<String> packFormatLabel,
            PackSourceKind sourceKind
    ) {
        public static InspectedMetadata empty(PackSourceKind kind) {
            return new InspectedMetadata(Optional.empty(), Optional.empty(), kind);
        }
    }

    private PackMetadataInspector() {
    }

    public static InspectedMetadata inspect(Pack pack, Path resourcePacksDir) {
        PackSourceKind kind = resolveSourceKind(pack.getId(), resourcePacksDir);
        try (InputStream mcmeta = MinecraftCompatProvider.get().openRootResource(pack, "pack.mcmeta")) {
            if (mcmeta == null) {
                return InspectedMetadata.empty(kind);
            }
            try (InputStreamReader reader = new InputStreamReader(mcmeta, StandardCharsets.UTF_8)) {
                JsonElement rootEl = JsonParser.parseReader(reader);
                if (rootEl == null || !rootEl.isJsonObject()) {
                    return InspectedMetadata.empty(kind);
                }
                JsonObject root = rootEl.getAsJsonObject();
                Optional<String> formatLabel = extractPackFormat(root);
                Optional<String> author = extractAuthor(root);
                return new InspectedMetadata(author, formatLabel, kind);
            }
        } catch (Exception e) {
            PackStudioConstants.LOGGER.debug("Could not inspect pack.mcmeta for '{}': {}", pack.getId(), e.getMessage());
            return InspectedMetadata.empty(kind);
        }
    }

    public static PackSourceKind resolveSourceKind(String packId, Path resourcePacksDir) {
        if (packId == null || !packId.startsWith("file/") || resourcePacksDir == null) {
            return PackSourceKind.BUILTIN;
        }
        String filePart = packId.substring("file/".length());
        try {
            Path candidate = resourcePacksDir.resolve(filePart).normalize();
            if (candidate.startsWith(resourcePacksDir.normalize())) {
                if (Files.isDirectory(candidate)) {
                    return PackSourceKind.FOLDER;
                }
                if (Files.isRegularFile(candidate)) {
                    return PackSourceKind.ZIP_ARCHIVE;
                }
            }
        } catch (Exception ignored) {
        }
        return filePart.toLowerCase().endsWith(".zip") ? PackSourceKind.ZIP_ARCHIVE : PackSourceKind.FOLDER;
    }

    private static Optional<String> extractPackFormat(JsonObject root) {
        if (!root.has("pack") || !root.get("pack").isJsonObject()) {
            return Optional.empty();
        }
        JsonObject packObj = root.getAsJsonObject("pack");

        if (packObj.has("min_format") && packObj.has("max_format")) {
            String min = formatVersionElement(packObj.get("min_format"));
            String max = formatVersionElement(packObj.get("max_format"));
            if (!min.isEmpty() && !max.isEmpty()) {
                return Optional.of(min.equals(max) ? min : (min + " – " + max));
            }
        }
        if (packObj.has("pack_format")) {
            String fmt = formatVersionElement(packObj.get("pack_format"));
            if (!fmt.isEmpty()) {
                if (packObj.has("supported_formats")) {
                    String range = formatSupportedRange(packObj.get("supported_formats"));
                    if (!range.isEmpty() && !range.equals(fmt)) {
                        return Optional.of(fmt + " (" + range + ")");
                    }
                }
                return Optional.of(fmt);
            }
        }
        return Optional.empty();
    }

    private static String formatVersionElement(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return "";
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return Integer.toString(el.getAsInt());
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() >= 2) {
                return arr.get(0).getAsInt() + "." + arr.get(1).getAsInt();
            }
            if (arr.size() == 1) {
                return arr.get(0).getAsInt() + ".0";
            }
        }
        return "";
    }

    private static String formatSupportedRange(JsonElement supported) {
        if (supported == null || supported.isJsonNull()) {
            return "";
        }
        if (supported.isJsonArray()) {
            JsonArray arr = supported.getAsJsonArray();
            if (arr.size() >= 2) {
                return arr.get(0).getAsInt() + "–" + arr.get(arr.size() - 1).getAsInt();
            }
        } else if (supported.isJsonObject()) {
            JsonObject obj = supported.getAsJsonObject();
            if (obj.has("min_inclusive") && obj.has("max_inclusive")) {
                return obj.get("min_inclusive").getAsInt() + "–" + obj.get("max_inclusive").getAsInt();
            }
        }
        return "";
    }

    /**
     * Extracts author information strictly when declared in {@code pack.mcmeta}; returns empty otherwise.
     */
    private static Optional<String> extractAuthor(JsonObject root) {
        Optional<String> fromRoot = readAuthorFields(root);
        if (fromRoot.isPresent()) {
            return fromRoot;
        }
        if (root.has("pack") && root.get("pack").isJsonObject()) {
            Optional<String> fromPack = readAuthorFields(root.getAsJsonObject("pack"));
            if (fromPack.isPresent()) {
                return fromPack;
            }
        }
        if (root.has("meta") && root.get("meta").isJsonObject()) {
            return readAuthorFields(root.getAsJsonObject("meta"));
        }
        return Optional.empty();
    }

    private static Optional<String> readAuthorFields(JsonObject obj) {
        for (String key : List.of("author", "authors", "creator")) {
            if (!obj.has(key)) {
                continue;
            }
            JsonElement el = obj.get(key);
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String cleaned = TextFormatUtil.cleanText(el.getAsString());
                if (!cleaned.isBlank()) {
                    return Optional.of(cleaned);
                }
            } else if (el.isJsonArray()) {
                List<String> names = new ArrayList<>();
                for (JsonElement item : el.getAsJsonArray()) {
                    if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                        String cleaned = TextFormatUtil.cleanText(item.getAsString());
                        if (!cleaned.isBlank()) {
                            names.add(cleaned);
                        }
                    }
                }
                if (!names.isEmpty()) {
                    return Optional.of(String.join(", ", names));
                }
            }
        }
        return Optional.empty();
    }
}
