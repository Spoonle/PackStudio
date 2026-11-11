package com.packstudio.manager;

import com.packstudio.client.NotificationManager;
import com.packstudio.compatibility.ProfileValidator;
import com.packstudio.compatibility.ProfileValidator.MissingPackNotice;
import com.packstudio.compatibility.ProfileValidator.ProfileHealth;
import com.packstudio.core.PackStudioConstants;
import com.packstudio.model.PackProfile;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.language.I18n;

/**
 * Executes transactional one-click profile switching: validates the target profile, checks for missing
 * packs without silent substitution, stages exact priority order, triggers a native resource reload,
 * and confirms activation strictly after reload completion (restoring prior state on failure).
 */
public final class ProfileApplier {
    public record SwitchOutcome(
            boolean success,
            PackProfile profile,
            List<MissingPackNotice> missingPacks,
            String message
    ) {
    }

    private final ProfileManager profileManager;
    private final PackRepositoryManager repositoryManager;
    private final ConfigManager configManager;
    private final NotificationManager notifications;

    public ProfileApplier(
            ProfileManager profileManager,
            PackRepositoryManager repositoryManager,
            ConfigManager configManager,
            NotificationManager notifications
    ) {
        this.profileManager = Objects.requireNonNull(profileManager, "profileManager");
        this.repositoryManager = Objects.requireNonNull(repositoryManager, "repositoryManager");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
    }

    public CompletableFuture<SwitchOutcome> applyProfile(
            String profileId,
            boolean showSystemToast,
            Runnable onFinishedUiCallback
    ) {
        Optional<PackProfile> opt = profileManager.findById(profileId);
        if (opt.isEmpty()) {
            String msg = "Requested profile was not found.";
            notifications.error(PackStudioConstants.MOD_NAME, msg, showSystemToast);
            return CompletableFuture.completedFuture(new SwitchOutcome(false, null, List.of(), msg));
        }

        PackProfile profile = opt.get();
        repositoryManager.refreshFromRepository(false);
        ProfileHealth health = ProfileValidator.evaluateAgainstLibrary(profile, repositoryManager.allPacks());
        if (!health.structurallyValid()) {
            String err = health.structuralErrors().getFirst();
            notifications.error(PackStudioConstants.MOD_NAME, err, showSystemToast);
            return CompletableFuture.completedFuture(new SwitchOutcome(false, profile, List.of(), err));
        }

        String previousActiveProfileId = configManager.config().activeProfileId();
        List<String> previousStaged = repositoryManager.stagedEnabledTopDownIds();

        // Stage only genuinely present enabled packs in exact saved priority order — NEVER substitute missing packs.
        repositoryManager.stageOrderedEnabledIds(profile.enabledPackIdsOrdered());

        return repositoryManager.applyStagedChanges(onFinishedUiCallback).thenApply(reloadedOk -> {
            if (!reloadedOk) {
                repositoryManager.stageOrderedEnabledIds(previousStaged);
                configManager.config().setActiveProfileId(previousActiveProfileId);
                configManager.save();
                String failMsg = I18n.get("packstudio.error.reload_failed");
                notifications.error(PackStudioConstants.MOD_NAME, failMsg, showSystemToast);
                return new SwitchOutcome(false, profile, health.missingPacks(), failMsg);
            }

            configManager.config().setActiveProfileId(profile.id());
            configManager.save();

            List<MissingPackNotice> missingEnabled = health.missingPacks().stream()
                    .filter(MissingPackNotice::wasEnabled)
                    .toList();

            String summary;
            if (!missingEnabled.isEmpty()) {
                summary = I18n.get("packstudio.profiles.applied", profile.name())
                        + " (" + I18n.get("packstudio.profiles.missing_warning", missingEnabled.size()) + ")";
                notifications.warning(PackStudioConstants.MOD_NAME, summary);
                if (showSystemToast) {
                    notifications.success(PackStudioConstants.MOD_NAME, summary, true);
                }
            } else {
                summary = I18n.get("packstudio.profiles.applied", profile.name());
                notifications.success(PackStudioConstants.MOD_NAME, summary, showSystemToast);
            }
            return new SwitchOutcome(true, profile, health.missingPacks(), summary);
        });
    }

    /**
     * Cycles to the next saved profile when the user triggers the Quick Switch Profile keybind.
     */
    public CompletableFuture<SwitchOutcome> cycleNextProfile(boolean showSystemToast) {
        List<PackProfile> all = profileManager.profiles();
        if (all.isEmpty()) {
            String msg = "No saved PackStudio profiles yet.";
            notifications.info(PackStudioConstants.MOD_NAME, msg);
            return CompletableFuture.completedFuture(new SwitchOutcome(false, null, List.of(), msg));
        }
        String activeId = configManager.config().activeProfileId();
        int nextIdx = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(activeId)) {
                nextIdx = (i + 1) % all.size();
                break;
            }
        }
        return applyProfile(all.get(nextIdx).id(), showSystemToast, null);
    }
}
