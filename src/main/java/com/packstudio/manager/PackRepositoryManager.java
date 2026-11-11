package com.packstudio.manager;

import com.packstudio.compatibility.PackMetadataInspector;
import com.packstudio.compatibility.PackMetadataInspector.InspectedMetadata;
import com.packstudio.core.PackStudioConstants;
import com.packstudio.model.FilterMode;
import com.packstudio.model.PackCompatibilityStatus;
import com.packstudio.model.PackEntry;
import com.packstudio.model.PackSourceKind;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.model.SortMode;
import com.packstudio.util.TextFormatUtil;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.Util;

/**
 * Coordinates real resource pack discovery against Minecraft's {@link PackRepository}, stages pending
 * enable/disable and priority-order edits in UI top-to-bottom order, and applies or rolls back changes
 * cleanly on the client thread.
 */
public final class PackRepositoryManager {
    private final ConfigManager configManager;
    private final PackIconCache iconCache;
    private final Map<String, InspectedMetadata> metadataCache = new ConcurrentHashMap<>();

    // Discovered user-visible packs keyed by native PackRepository ID
    private final Map<String, Pack> discoveredPacks = new LinkedHashMap<>();
    // Hidden/internal required packs (e.g. Fabric internal mod packs) preserved during apply
    private final List<String> hiddenSelectedBaseIds = new ArrayList<>();

    // Baseline enabled pack IDs in UI top-to-bottom priority order (#1 = highest priority)
    private List<String> committedEnabledTopDown = List.of();
    // Staged working enabled pack IDs in UI top-to-bottom priority order
    private final List<String> stagedEnabledTopDown = new ArrayList<>();

    private volatile boolean applyingReload = false;

    public PackRepositoryManager(ConfigManager configManager, PackIconCache iconCache) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.iconCache = Objects.requireNonNull(iconCache, "iconCache");
    }

    /**
     * Scans Minecraft's {@link PackRepository} on the client thread and schedules non-blocking
     * {@code pack.mcmeta} enrichment on {@link Util#backgroundExecutor()}.
     */
    public synchronized void refreshFromRepository(boolean preserveStagedEdits) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getResourcePackRepository() == null) {
            return;
        }
        PackRepository repository = client.getResourcePackRepository();
        boolean hadPendingChanges = preserveStagedEdits && hasUnsavedChanges();
        List<String> previousStaged = List.copyOf(stagedEnabledTopDown);

        repository.reload();

        discoveredPacks.clear();
        hiddenSelectedBaseIds.clear();

        for (Pack pack : repository.getAvailablePacks()) {
            if (isFabricHiddenPack(pack)) {
                continue;
            }
            discoveredPacks.put(pack.getId(), pack);
        }

        // Native PackRepository.getSelectedPacks() is bottom-to-top (lowest to highest priority).
        // Reverse visible packs so index 0 (#1) in UI is the highest-priority override pack.
        List<String> visibleBottomUp = new ArrayList<>();
        for (Pack selected : repository.getSelectedPacks()) {
            if (isFabricHiddenPack(selected)) {
                hiddenSelectedBaseIds.add(selected.getId());
            } else if (discoveredPacks.containsKey(selected.getId())) {
                visibleBottomUp.add(selected.getId());
            }
        }
        Collections.reverse(visibleBottomUp);
        this.committedEnabledTopDown = List.copyOf(visibleBottomUp);

        this.stagedEnabledTopDown.clear();
        if (hadPendingChanges) {
            for (String id : previousStaged) {
                if (discoveredPacks.containsKey(id)) {
                    stagedEnabledTopDown.add(id);
                }
            }
            ensureRequiredPacksPresent(stagedEnabledTopDown);
        } else {
            this.stagedEnabledTopDown.addAll(this.committedEnabledTopDown);
            ensureRequiredPacksPresent(stagedEnabledTopDown);
        }

        scheduleBackgroundMetadataInspection(client.getResourcePackDirectory(), List.copyOf(discoveredPacks.values()));
    }

    private void scheduleBackgroundMetadataInspection(Path resourcePacksDir, List<Pack> packs) {
        CompletableFuture.runAsync(() -> {
            for (Pack pack : packs) {
                metadataCache.computeIfAbsent(
                        pack.getId(),
                        _ -> PackMetadataInspector.inspect(pack, resourcePacksDir)
                );
            }
        }, Util.backgroundExecutor());
    }

    /**
     * Synchronously inspects any uncached {@code pack.mcmeta} files (useful for deterministic unit tests).
     */
    public synchronized void inspectMetadataSync() {
        Minecraft client = Minecraft.getInstance();
        Path dir = client != null ? client.getResourcePackDirectory() : null;
        for (Pack pack : discoveredPacks.values()) {
            metadataCache.computeIfAbsent(pack.getId(), _ -> PackMetadataInspector.inspect(pack, dir));
        }
    }

    public synchronized List<PackEntry> allPacks() {
        PackStudioConfig cfg = configManager.config();
        List<PackEntry> entries = new ArrayList<>(discoveredPacks.size());
        Map<String, Integer> priorityIndex = new LinkedHashMap<>();
        for (int i = 0; i < stagedEnabledTopDown.size(); i++) {
            priorityIndex.put(stagedEnabledTopDown.get(i), i + 1);
        }

        Path packsDir = Minecraft.getInstance() != null ? Minecraft.getInstance().getResourcePackDirectory() : null;
        for (Pack pack : discoveredPacks.values()) {
            String id = pack.getId();
            Integer pos = priorityIndex.get(id);
            boolean enabled = pos != null;
            InspectedMetadata meta = metadataCache.get(id);
            PackSourceKind kind = meta != null
                    ? meta.sourceKind()
                    : PackMetadataInspector.resolveSourceKind(id, packsDir);

            entries.add(new PackEntry(
                    id,
                    TextFormatUtil.cleanText(pack.getTitle().getString()),
                    TextFormatUtil.cleanText(pack.getDescription().getString()),
                    meta != null ? meta.author() : Optional.empty(),
                    meta != null ? meta.packFormatLabel() : Optional.empty(),
                    PackCompatibilityStatus.fromMinecraft(pack.getCompatibility()),
                    kind,
                    pack.isRequired(),
                    pack.isFixedPosition(),
                    enabled,
                    enabled ? pos : -1,
                    cfg.isFavorite(id),
                    pack
            ));
        }
        return entries;
    }

    public synchronized List<PackEntry> queryPacks(String searchQuery, FilterMode filterMode, SortMode sortMode) {
        List<PackEntry> all = allPacks();
        String needle = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        FilterMode effectiveFilter = filterMode == null ? FilterMode.ALL : filterMode;
        SortMode effectiveSort = sortMode == null ? SortMode.PRIORITY : sortMode;

        List<PackEntry> filtered = new ArrayList<>();
        for (PackEntry entry : all) {
            if (!needle.isEmpty()) {
                boolean matchesName = entry.displayName().toLowerCase(Locale.ROOT).contains(needle);
                boolean matchesId = entry.id().toLowerCase(Locale.ROOT).contains(needle);
                boolean matchesDesc = entry.description().toLowerCase(Locale.ROOT).contains(needle);
                if (!matchesName && !matchesId && !matchesDesc) {
                    continue;
                }
            }
            boolean keep = switch (effectiveFilter) {
                case ALL -> true;
                case ENABLED -> entry.enabled();
                case DISABLED -> !entry.enabled();
                case FAVORITES -> entry.favorite();
                case INCOMPATIBLE -> !entry.compatibility().isCompatible();
            };
            if (keep) {
                filtered.add(entry);
            }
        }

        Comparator<PackEntry> byPriorityThenName = Comparator
                .comparing((PackEntry e) -> !e.enabled())
                .thenComparingInt(e -> e.enabled() ? e.priorityPosition() : Integer.MAX_VALUE)
                .thenComparing(e -> e.displayName().toLowerCase(Locale.ROOT));

        Comparator<PackEntry> comparator = switch (effectiveSort) {
            case PRIORITY -> byPriorityThenName;
            case ALPHABETICAL_ASC -> Comparator.comparing((PackEntry e) -> e.displayName().toLowerCase(Locale.ROOT))
                    .thenComparing(PackEntry::id);
            case ALPHABETICAL_DESC -> Comparator.comparing((PackEntry e) -> e.displayName().toLowerCase(Locale.ROOT), Comparator.reverseOrder())
                    .thenComparing(PackEntry::id);
            case ENABLED_FIRST -> byPriorityThenName;
            case COMPATIBILITY -> Comparator.comparingInt((PackEntry e) -> e.compatibility().ordinal())
                    .thenComparing(byPriorityThenName);
        };

        filtered.sort(comparator);
        return List.copyOf(filtered);
    }

    public synchronized Optional<PackEntry> findPack(String packId) {
        if (packId == null) {
            return Optional.empty();
        }
        return allPacks().stream().filter(p -> p.id().equals(packId)).findFirst();
    }

    public synchronized boolean setPackEnabled(String packId, boolean enable) {
        Pack pack = discoveredPacks.get(packId);
        if (pack == null) {
            return false;
        }
        if (enable) {
            if (stagedEnabledTopDown.contains(packId)) {
                return false;
            }
            if (pack.getDefaultPosition() == Pack.Position.BOTTOM) {
                int insertAt = findBottomInsertIndex();
                stagedEnabledTopDown.add(insertAt, packId);
            } else {
                stagedEnabledTopDown.addFirst(packId);
            }
            ensureRequiredPacksPresent(stagedEnabledTopDown);
            return true;
        } else {
            if (pack.isRequired()) {
                return false;
            }
            return stagedEnabledTopDown.remove(packId);
        }
    }

    public synchronized boolean togglePackEnabled(String packId) {
        boolean currentlyEnabled = stagedEnabledTopDown.contains(packId);
        return setPackEnabled(packId, !currentlyEnabled);
    }

    /**
     * Moves an enabled pack up ({@code delta = -1}, higher priority) or down ({@code delta = +1}, lower priority)
     * without crossing pinned/fixed-position packs such as {@code vanilla}.
     */
    public synchronized boolean movePackPriority(String packId, int delta) {
        int currentIndex = stagedEnabledTopDown.indexOf(packId);
        if (currentIndex < 0 || delta == 0) {
            return false;
        }
        Pack moving = discoveredPacks.get(packId);
        if (moving == null || moving.isFixedPosition()) {
            return false;
        }
        int targetIndex = currentIndex + (delta < 0 ? -1 : 1);
        if (targetIndex < 0 || targetIndex >= stagedEnabledTopDown.size()) {
            return false;
        }
        Pack neighbor = discoveredPacks.get(stagedEnabledTopDown.get(targetIndex));
        if (neighbor != null && neighbor.isFixedPosition()) {
            return false;
        }
        Collections.swap(stagedEnabledTopDown, currentIndex, targetIndex);
        return true;
    }

    public synchronized void discardStagedChanges() {
        stagedEnabledTopDown.clear();
        stagedEnabledTopDown.addAll(committedEnabledTopDown);
        ensureRequiredPacksPresent(stagedEnabledTopDown);
    }

    /**
     * Resets staged pack selection to required base packs only (e.g., Vanilla Default + required mod packs).
     */
    public synchronized void stageResetToDefaults() {
        stagedEnabledTopDown.clear();
        ensureRequiredPacksPresent(stagedEnabledTopDown);
    }

    /**
     * Stages an ordered list of enabled pack IDs (top-to-bottom priority), preserving required base packs.
     */
    public synchronized void stageOrderedEnabledIds(List<String> orderedTopDownIds) {
        stagedEnabledTopDown.clear();
        if (orderedTopDownIds != null) {
            for (String id : orderedTopDownIds) {
                if (id != null && discoveredPacks.containsKey(id) && !stagedEnabledTopDown.contains(id)) {
                    stagedEnabledTopDown.add(id);
                }
            }
        }
        ensureRequiredPacksPresent(stagedEnabledTopDown);
    }

    public synchronized boolean hasUnsavedChanges() {
        return !stagedEnabledTopDown.equals(committedEnabledTopDown);
    }

    public synchronized List<String> stagedEnabledTopDownIds() {
        return List.copyOf(stagedEnabledTopDown);
    }

    public synchronized List<String> committedEnabledTopDownIds() {
        return List.copyOf(committedEnabledTopDown);
    }

    public boolean isApplyingReload() {
        return applyingReload;
    }

    /**
     * Applies the current staged pack selection to Minecraft's {@link PackRepository}, triggers
     * {@link Minecraft#reloadResourcePacks()}, persists {@code options.txt} on completion, and rolls back
     * to the prior committed pack configuration if reload fails.
     */
    public CompletableFuture<Boolean> applyStagedChanges(Runnable onFinishedUiCallback) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getResourcePackRepository() == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (applyingReload) {
            return CompletableFuture.completedFuture(false);
        }

        PackRepository repository = client.getResourcePackRepository();
        List<String> previousNativeBottomUp = List.copyOf(repository.getSelectedIds());
        List<String> previousCommittedTopDown;
        List<String> targetTopDown;
        List<String> targetNativeBottomUp;

        synchronized (this) {
            previousCommittedTopDown = List.copyOf(this.committedEnabledTopDown);
            ensureRequiredPacksPresent(this.stagedEnabledTopDown);
            targetTopDown = List.copyOf(this.stagedEnabledTopDown);
            targetNativeBottomUp = buildNativeBottomUpSelection(targetTopDown);
            this.applyingReload = true;
        }

        try {
            repository.setSelected(targetNativeBottomUp);
            CompletableFuture<Void> reloadFuture = client.reloadResourcePacks();
            return reloadFuture.handleAsync((_, throwable) -> {
                synchronized (PackRepositoryManager.this) {
                    applyingReload = false;
                    if (throwable != null) {
                        PackStudioConstants.LOGGER.error("Resource pack reload failed; restoring previous selection.", throwable);
                        repository.setSelected(previousNativeBottomUp);
                        committedEnabledTopDown = previousCommittedTopDown;
                        stagedEnabledTopDown.clear();
                        stagedEnabledTopDown.addAll(previousCommittedTopDown);
                        try {
                            client.reloadResourcePacks();
                        } catch (Exception rollbackErr) {
                            PackStudioConstants.LOGGER.error("Rollback reload threw exception", rollbackErr);
                        }
                        if (onFinishedUiCallback != null) {
                            onFinishedUiCallback.run();
                        }
                        return false;
                    }

                    client.options.updateResourcePacks(repository);
                    committedEnabledTopDown = targetTopDown;
                    stagedEnabledTopDown.clear();
                    stagedEnabledTopDown.addAll(targetTopDown);
                    if (onFinishedUiCallback != null) {
                        onFinishedUiCallback.run();
                    }
                    return true;
                }
            }, client);
        } catch (Throwable immediateError) {
            synchronized (this) {
                applyingReload = false;
                repository.setSelected(previousNativeBottomUp);
                committedEnabledTopDown = previousCommittedTopDown;
                stagedEnabledTopDown.clear();
                stagedEnabledTopDown.addAll(previousCommittedTopDown);
            }
            PackStudioConstants.LOGGER.error("Immediate error while applying resource packs", immediateError);
            if (onFinishedUiCallback != null) {
                onFinishedUiCallback.run();
            }
            return CompletableFuture.completedFuture(false);
        }
    }

    private List<String> buildNativeBottomUpSelection(List<String> topDownVisibleIds) {
        LinkedHashSet<String> bottomUp = new LinkedHashSet<>(hiddenSelectedBaseIds);
        List<String> reversedVisible = new ArrayList<>(topDownVisibleIds);
        Collections.reverse(reversedVisible);
        bottomUp.addAll(reversedVisible);
        return List.copyOf(bottomUp);
    }

    private void ensureRequiredPacksPresent(List<String> topDownList) {
        for (Pack pack : discoveredPacks.values()) {
            if (pack.isRequired() && !topDownList.contains(pack.getId())) {
                if (pack.getDefaultPosition() == Pack.Position.BOTTOM || pack.isFixedPosition()) {
                    topDownList.add(pack.getId());
                } else {
                    topDownList.addFirst(pack.getId());
                }
            }
        }
    }

    private int findBottomInsertIndex() {
        for (int i = stagedEnabledTopDown.size() - 1; i >= 0; i--) {
            Pack p = discoveredPacks.get(stagedEnabledTopDown.get(i));
            if (p == null || !p.isFixedPosition()) {
                return i + 1;
            }
        }
        return 0;
    }

    public PackIconCache iconCache() {
        return iconCache;
    }

    private static boolean isFabricHiddenPack(Pack pack) {
        if (pack == null) {
            return false;
        }
        try {
            Method m = pack.getClass().getMethod("fabric$isHidden");
            Object res = m.invoke(pack);
            return res instanceof Boolean b && b;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
