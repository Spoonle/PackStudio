# PackStudio 26.X — Verification Record

Date: 2026-11-13 · Branch: `arena/a34a318e-packstudio` · Toolchain: JDK 25, Gradle 9.8.2, Fabric Loom 1.19-SNAPSHOT

## Result

All five Minecraft targets build, test and audit successfully in CI, and each is packaged as its own
independent release file.

| Target | API family | Fabric API (official) | Build | JUnit suite | Jar audit | Release file |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 26.1 | 26.1 | `0.155.3+26.1.2` | ✅ | ✅ | ✅ | `releases/26.1/packstudio-fabric-26.1-1.0.0.jar` |
| 26.1.1 | 26.1 | `0.155.3+26.1.2` | ✅ | ✅ | ✅ | `releases/26.1.1/packstudio-fabric-26.1.1-1.0.0.jar` |
| 26.1.2 | 26.1 | `0.155.3+26.1.2` | ✅ | ✅ | ✅ | `releases/26.1.2/packstudio-fabric-26.1.2-1.0.0.jar` |
| 26.2 | 26.2 | `0.161.0+26.2` | ✅ | ✅ | ✅ | `releases/26.2/packstudio-fabric-26.2-1.0.0.jar` |
| 26.3 | 26.3 | `0.165.2+26.3` | ✅ | ✅ | ✅ | `releases/26.3/packstudio-fabric-26.3-1.0.0.jar` |

Loader `0.20.1`, Java 25, `environment: client`, one client entrypoint, no Mixins, no shaders.

Each jar was additionally re-audited locally after download from the committed release tree
(`tools/verify_release_jar.sh <jar> <mc> <family>`) — 5/5 PASSED, and each jar's compiled compatibility
class references only the APIs of its own family:

| Jar | Version-specific symbols found in `MinecraftCompatImpl` |
| :--- | :--- |
| 26.1 | `PackResources`, `getToastManager`, `openUri` (no `Minecraft.gui`, no 26.3 APIs) |
| 26.2 | `PackResources`, `client/gui/Gui`, `openUri` |
| 26.3 | `PackMetadataResources`, `openMetadata`, `Blaze3D`, `client/gui/Gui` |

The 26.3 baseline keeps `minecraft: ~26.3` and `fabric-api: "*"`, exactly as it shipped before this work.

## Automated coverage (17 tests × 5 targets)

| Test | What it proves |
| :--- | :--- |
| `atomicConfigPersistenceAndCorruptRecovery` | atomic JSON writes, `.corrupt.<ts>.bak` quarantine |
| `pathSanitizationPreventsTraversal` | profile import/export cannot escape its directory |
| `managedIoExecutorRunsOffCallerThreadOnDaemonWorker` | icon/metadata work never blocks the render thread |
| `discoversRealZipAndFolderPacksAndAppliesOrRollsBackReloads` | real ZIP/folder discovery, apply, rollback |
| `profileCrudImportExportMissingPackAndTransactionalSwitching` | full profile lifecycle + missing-pack handling |
| `minimalistComponentsRenderAndHandleInputCleanly` | widget render + input dispatch without exceptions |
| `adaptiveLayoutNeverOverlapsAcrossGuiScales` | layout across GUI scales |
| `fullScreenRendersAllTabsViewsAndPersistsSettings` | all tabs/views render, settings persist |
| `clientEntrypointInitializesAndRegistersScreens` | entrypoint registration, screen construction |
| `animationToggleControlsTransitions` | transitions toggle behaviour |
| `classpathMinecraftMatchesDeclaredTarget` | compiled-against version == declared target |
| `declaredFabricVersionsAreTheOfficialOnesForThisTarget` | real, non-invented dependency versions |
| `exactlyOneCompatibilityLayerIsPresentAndLoads` | no accidental multi-family classpath |
| `sharedCodeLoadsAgainstThisMinecraftVersion` | shared classes link on every target |
| `modMetadataReportsTheTargetedMinecraftVersion` | `fabric.mod.json` version names the target |
| `metadataTargetsTheDeclaredMinecraftVersion` | Minecraft dependency range covers the target |
| `metadataIsClientOnlyJava25WithFabricApiDependency` | client-only, Java ≥ 25, real Fabric API bound |

## Not verified — requires a real Minecraft client

These cannot run headlessly and are **not** claimed as verified:

1. GPU rendering: Minimalist panels, texture upload, real window sizes / GUI scales.
2. Live resource-pack reload of a running game and the visual result.
3. Real key capture through SDL (P / O bindings, Controls screen rebinding).
4. OS folder opening and native system toasts.

Manual smoke test per target: install that Minecraft version + Fabric Loader `0.20.1` + the matching Fabric API
build + the matching `packstudio-fabric-<version>-1.0.0.jar`, then press `P`, switch tabs/views, apply a pack change, create
and apply a profile, and press `O`.

## Two defects this verification found and fixed

- `.gitignore` contained an unanchored `build/`, which silently excluded the Java package
  `src/test/java/com/packstudio/build/` — the new identity and metadata audits were never committed and never
  ran. Fixed by anchoring ignore rules to the repository root.
- `TargetIdentityTest` assumed Minecraft had been initialised (`SharedConstants.getCurrentVersion()` throws
  "Game version not set" in a bare JUnit run). It now triggers detection and falls back to the resolved jar's
  `version.json`, and the mod-container assertion no longer requires Fabric Loader to be bootstrapped.
