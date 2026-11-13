# PackStudio — Minecraft 26.X Compatibility Analysis & Multi-Version Architecture

Status: implementation in progress. Every claim in this document is derived from the
repository sources plus the upstream Fabric artifacts that are pinned for each target.

| Target | Minecraft | Java | Fabric Loader | Fabric API | API family |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `26.3` (baseline, unchanged) | `26.3` | 25 | `0.20.1` | `0.165.2+26.3` | `26.3` |
| `26.2` | `26.2` | 25 | `0.20.1` | `0.161.0+26.2` | `26.2` |
| `26.1.2` | `26.1.2` | 25 | `0.20.1` | `0.155.3+26.1.2` | `26.1` |
| `26.1.1` | `26.1.1` | 25 | `0.20.1` | `0.155.3+26.1.2` | `26.1` |
| `26.1` | `26.1` | 25 | `0.20.1` | `0.155.3+26.1.2` | `26.1` |

All 26.X targets are unobfuscated (Mojang official names), client-only, Fabric-only, Java 25 minimum,
and are built with the `net.fabricmc.fabric-loom` plugin.

---

## 1. What the 26.3 implementation actually depends on

Of the 49 production classes, only a small surface touches Minecraft. Everything else
(`manager/ProfileManager`, `storage/JsonStorage`, `compatibility/ProfileValidator`,
`compatibility/CompatibilityAnalyzer`, models, theme logic, `AdaptiveLayout`,
`AnimationUtil`, `TextFormatUtil`, …) is pure Java + Gson and therefore version-independent.

The Minecraft/Fabric surface actually used:

| Area | API |
| :--- | :--- |
| Entrypoint / lifecycle | `ClientModInitializer`, `ClientLifecycleEvents`, `ClientTickEvents` |
| Keybinds | `KeyMappingHelper.registerKeyMapping`, `KeyMapping.Category`, `InputConstants` |
| Screen injection | `ScreenEvents.AFTER_INIT`, `Screens.getWidgets`, `Button`, `Tooltip` |
| Screen navigation | `client.gui.screen()`, `client.gui.setScreen(…)` |
| Rendering | `GuiGraphicsExtractor` (`fill`, `fillGradient`, `outline`, `text`, `centeredText`, `blit(RenderPipelines.GUI_TEXTURED, …)`, scissor) |
| Widgets | `Screen.extractRenderState`, `Screen.extractBackground`, `AbstractWidget.extractRenderState`, `Button.extractContents`, `EditBox` |
| Input | `MouseButtonEvent`, `KeyEvent`, `CharacterEvent` |
| Pack discovery | `PackRepository`, `Pack` (`getId`, `getTitle`, `getDescription`, `getCompatibility`) |
| Pack metadata / icons | `Pack.openMetadata()` → `PackMetadataResources`, `pack.png` → `NativeImage` → `DynamicTexture` |
| Resource reload | `PackRepository.reload()`, `Minecraft.reloadResourcePacks()` |
| i18n / logging | `I18n.get`, `Component`, `Language`, slf4j |

## 2. API differences between 26.1 and 26.3

Determined by diffing Fabric API's own sources at the three API tags
(`0.155.3+26.1.2`, `0.161.0+26.2`, `0.165.2+26.3`). Fabric API compiles against each exact
Minecraft version, so a method that both Fabric and PackStudio need and that appears only in
newer tags is a genuine break.

### 2.1 Confirmed breaks

1. **Client GUI holder (26.1 → 26.2).**
   `Minecraft.gui` — a `Gui` object exposing `screen()`, `setScreen(…)`, `toastManager()` — does not
   exist before 26.2. Fabric 26.1 accesses the current screen as `client.screen` and navigates with
   `client.setScreen(…)`; from 26.2 onwards it is `client.gui.screen()` / `client.gui.setScreen(…)`.
   Every `client.gui.*` call in PackStudio (and in the test suite) is a 26.2+ construct.

2. **Pack metadata access (26.1/26.2 → 26.3).**
   - 26.1/26.2: `Pack.open()` returns a single `PackResources`, and `pack.mcmeta` / `pack.png` are read
     via `PackResources.getRootResource("pack.mcmeta")`.
   - 26.3: `Pack.open()` returns `Stream<PackResources>`, and metadata is exposed through the new
     `PackMetadataResources` type returned by `Pack.openMetadata()`.
   `PackMetadataResources` does not exist in 26.1 or 26.2, and `openMetadata()` does not exist there,
   so both `PackMetadataInspector` and `PackIconCache` fail to compile on the older targets.

3. **Per-widget render hook (26.1/26.2 → 26.3).**
   In 26.1 and 26.2 `AbstractWidget` renders through the `extractContents(…)` hook; 26.3 adds
   `extractWidgetRenderState(…)`, which is the *only* per-widget override point that rendered
   `GlassSearchField` in 26.3. `EditBox` paints its own text/selection/cursor through that hook, so
   simply deleting the override would make the search field's text invisible.

### 2.2 Confirmed *unchanged* (verified, not assumed)

- `Screens.getWidgets(Screen)` is byte-identical in 26.1.2 and 26.3 → screen injection is portable.
- `GuiGraphicsExtractor` exists in 26.1, 26.2 and 26.3 (it is the name Fabric uses for all three).
- `Screen.extractRenderState(…)` / `Screen.extractBackground(…)` overrides match in all three tags.
- `Button.builder(…).pos(…).size(…).tooltip(…).build()`, `new EditBox(font, x, y, w, h, message)`,
  `extractContents(…)` and `AbstractWidget.extractRenderState(…)` are all used identically by Fabric
  API on 26.1.2, 26.2 and 26.3.
- `net.minecraft.client.resources.language.I18n` exists on all three.
- `KeyMappingHelper` / `KeyMapping.Category` (fabric-key-mapping-api-v1 ≥ 2.0.4) are identical.
- `PackRepository`, `PackType`, `PackFormat.major()/minor()`, `PackCompatibility` are stable.
- `net.minecraft.resources.Identifier` (not `ResourceLocation`) is correct for 26.1 as well.

---

## 3. Build architecture

```
src/main/java/                          shared code, compiles against every 26.X target
  com/packstudio/compat/MinecraftCompat.java         version-neutral API used by shared code
src/main/families/26.1/java/…/MinecraftCompatImpl.java   pre-26.2 GUI API + PackResources metadata
src/main/families/26.2/java/…/MinecraftCompatImpl.java   client.gui API + PackResources metadata
src/main/families/26.3/java/…/MinecraftCompatImpl.java   client.gui API + openMetadata() (byte-for-byte
                                                        equivalent behaviour to the original code)
src/main/resources/                   shared assets + one templated fabric.mod.json
```

- One shared codebase. Exactly one small interface (`MinecraftCompat`) has per-family
  implementations, chosen at build time by the target table in `build.gradle`.
- `-Pminecraft_version=<version>` (default `26.3`) looks the version up in the `minecraftTargets` table, which
  supplies the API family, the official Loader/Fabric API versions, the `fabric.mod.json` Minecraft range, the
  Fabric API lower bound and the output jar name. Because the table is the single source of truth, a jar cannot
  be built with another version's dependencies.
- `fabric.mod.json` is one shared template: `processResources` expands `version`, `minecraft_version`,
  `loader_version`, `minecraft_range` and `fabric_api_range` for the selected target, so no target can end up
  with a missing or duplicated mod metadata file.
- Nothing is version-guarded at runtime: each jar contains exactly one implementation, so a jar
  cannot silently fall back to another version's API.
- **The 26.3 build keeps its original plugin id, loader, Loom, Fabric API versions, Java level
  and artifact name.** Adding a target never touches the 26.3 numbers: its entry in the target table is the
  original `~26.3` / `fabric-api: "*"` pair.

Per-target dependency metadata (`fabric.mod.json` `depends`):

| Target | `minecraft` predicate |
| :--- | :--- |
| 26.1 | `>=26.1 <26.1.1` |
| 26.1.1 | `>=26.1.1 <26.1.2` |
| 26.1.2 | `>=26.1.2 <26.2` |
| 26.2 | `>=26.2 <26.3` |
| 26.3 | `~26.3` (unchanged) |

These are exact-version predicates, so each jar refuses to load on the wrong game version instead of
pretending compatibility.

## 4. Verification (as implemented)

Per target, in CI (JDK 25 + Gradle `9.8.2` + Fabric Loom `1.19-SNAPSHOT`), five matrix jobs run in
parallel — one per Minecraft version:

1. `./gradlew clean build -Pminecraft_version=…` → dependency resolution against that version's official
   Minecraft, Fabric Loader and Fabric API; compilation of the shared sources plus that family's compatibility
   implementation; `fabric.mod.json` expansion.
2. JUnit 5 suite, shared across all targets, so every feature test runs once per target:
   profile persistence + corruption quarantine, real ZIP/folder pack discovery, `pack.mcmeta` + `pack.png`
   handling, search/filter/sort, priority ordering, profile CRUD, import/export, transactional apply with
   rollback, adaptive layout and keyboard navigation.
3. `TargetIdentityTest` — asserts at runtime that the compiled classes correspond to the declared Minecraft
   version and that the expected compatibility implementation is on the classpath.
4. `JarMetadataTest` — asserts the packaged `fabric.mod.json`: version suffix `+mc<target>`, Minecraft range
   covering the target, client-only environment, Java ≥ 25, real Fabric API bound and the client entrypoint.
5. `tools/verify_release_jar.sh <jar> <mc> <family>` — independent shell audit of the same properties plus the
   build-identity resource and bundled translations. It fails the job with an `::error` annotation naming the
   reason.
6. The sync job rebuilds each target, re-audits the staged file, writes
   `releases/<version>/PackStudio-<version>.jar` + `-sources.jar` + `SHA256SUMS.txt` and commits them.

Current status: **all five targets pass steps 1–6** (run of 2026-11-13).

### Verification matrix (what is and is not proven)

| Check | Verified headlessly | Needs a real client |
| :--- | :--- | :--- |
| Compiles against the target's real Minecraft + Fabric API | ✅ every target | — |
| Feature behaviour (profiles, packs, search, ordering, imports) | ✅ every target | — |
| Packaged metadata, dependency ranges, entrypoint | ✅ every target | — |
| Runtime target identity / correct compat layer | ✅ every target | — |
| GUI rendering, texture upload, scaling | ⚠️ smoke-level only | ✅ GPU |
| Resource-pack reload of a running game | ⚠️ rollback logic only | ✅ reload |
| Keyboard shortcuts (SDL capture, Controls screen) | ⚠️ key mapping creation only | ✅ input |
| OS folder dialogs / native toasts | ❌ | ✅ |

The `⚠️` rows exercise the underlying logic and API calls, but the visible/interactive result can only be
confirmed in a real Minecraft client. They are listed as manual smoke tests in `releases/RELEASE_NOTES_26X.md`
and are **not** claimed as verified.

## 5. Release packaging

Each target produces its own independent file; there is no universal JAR and no release carries two Minecraft
versions:

```
releases/26.1/PackStudio-26.1.jar          + -sources.jar + SHA256SUMS.txt + CHANGELOG.md
releases/26.1.1/PackStudio-26.1.1.jar      …
releases/26.1.2/PackStudio-26.1.2.jar      …
releases/26.2/PackStudio-26.2.jar          …
releases/26.3/PackStudio-26.3.jar          …
```

`.github/workflows/release.yml` publishes one of these per run: it derives the Minecraft version from the
release tag (`v1.0.0+mc26.2` → `26.2`) or takes it as a dispatch input, builds only that target, audits it and
attaches only that target's files to the corresponding GitHub Release. Each of those releases maps one-to-one
to an independent CurseForge file.
