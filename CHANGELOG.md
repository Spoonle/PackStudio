# Changelog

All notable changes to **PackStudio** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0+mc26.x] - 2026-11-13

### Added
- **Minecraft 26.1, 26.1.1, 26.1.2 and 26.2 support** alongside the existing 26.3 release, as four additional
  independent distribution JARs (`releases/<version>/PackStudio-<version>.jar`).
- **Multi-version build**: `-Pminecraft_version=<version>` selects the Minecraft version, the official Fabric
  Loader / Fabric API versions, the API compatibility layer and the `fabric.mod.json` dependency range.
- **`com.packstudio.compat` compatibility layer** with one implementation per Minecraft API family, so the shared
  codebase no longer references version-specific APIs.
- **Per-target verification in CI**: compilation, the full JUnit 5 suite, target-identity assertions and a jar
  metadata audit (`tools/verify_release_jar.sh`) run for every supported Minecraft version.

### Changed
- Version-dependent Minecraft APIs are now isolated behind `MinecraftCompat`:
  - client screen access (`Minecraft.screen`/`setScreen` before 26.2, `Minecraft.gui` from 26.2),
  - resource-pack metadata (`Pack.open()` → `PackResources` before 26.3, `Pack.openMetadata()` on 26.3),
  - keyboard bindings (`InputConstants.Type.KEYSYM` before 26.3, `KEYBOARD` on 26.3),
  - directory opening (`Util.getPlatform().openUri` before 26.3, `Blaze3D.openPath` on 26.3).
- The search field paints its Liquid Glass shell from the screen background pass, so the UI no longer depends on
  which per-widget render hook a given Minecraft version exposes.
- Version strings shown in the UI (compatibility badge, overview health line) now come from Fabric Loader instead
  of a hardcoded `26.3`.
- `fabric.mod.json` is a single template; each target expands its own exact Minecraft range and Fabric API lower
  bound.

### Unchanged
- **Minecraft 26.3 remains the default target** with the same Minecraft, Fabric Loader (`0.20.1`), Fabric API
  (`0.165.2+26.3`), Loom (`1.19-SNAPSHOT`) and Java 25 toolchain, the same `~26.3` dependency predicate and the
  same artifact naming. No feature was removed to reach the older versions.

## [1.0.0+mc26.3] - 2026-11-11

### Added
- **Minecraft 26.3 Client Mod Foundation**: Native unobfuscated Java 25 Fabric mod (`fabricloader >=0.20.1`, `fabric-api 0.165.2+26.3`) with zero Mixins and full client-only multiplayer compatibility.
- **Liquid Glass Design System**: Dark translucent UI built exclusively on Minecraft 26.3's `GuiGraphicsExtractor` with three curated themes (*Obsidian Cyan*, *Midnight Indigo*, *Frosted Emerald*), adjustable opacity (`45%`–`96%`), optional smooth hover transitions, and zero blur-shader overhead.
- **Resource Pack Library (`All Packs` & `Favorites`)**:
  - Real discovery from `Minecraft#getResourcePackRepository()` supporting `.zip` archives, folder packs, and built-in packs.
  - Background `pack.png` `DynamicTexture` loading and `pack.mcmeta` inspection on `Util.backgroundExecutor()`.
  - Real-time search, status/compatibility filter pills, 5 sort modes, responsive Grid & List views, favorite pinning, and top-to-bottom priority reordering.
- **Pack Inspector (`PackDetailsPanel`)**: Honest metadata display for title, description, author (strictly when declared in `pack.mcmeta`), pack format, compatibility badge, state, and priority rank.
- **Schema-Versioned Profile Manager (`config/packstudio/profiles/`)**:
  - Create, update, rename, duplicate, delete, export, and import resource pack presets using portable `PackRepository` identifiers.
  - Explicit missing-pack detection that warns without silently substituting missing packs.
  - Atomic JSON writes (`JsonStorage`) and automatic `.corrupt.<timestamp>.bak` backup quarantine for damaged JSON files.
- **Transactional One-Click Profile Switching**:
  - Applies ordered presets via native `PackRepository#setSelected` + `Minecraft#reloadResourcePacks()`.
  - Confirms profile activation strictly after reload completion and rolls back to the prior configuration on reload failure.
  - Global SDL3 shortcuts for **Open PackStudio** (`P`) and **Quick-Switch Next Preset** (`O`), plus optional launch buttons on Title, Options, and Resource Packs screens.
- **Automated JUnit 5 Verification Suite**: Tests covering atomic config persistence, corruption quarantine, adaptive multi-scale layout, real ZIP/folder discovery, reload rollback, and profile CRUD/import/export.
