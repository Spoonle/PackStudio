# PackStudio `1.0.0+mc26.1` — for Minecraft `26.1`

> **Your resource packs. Your presets. Your world.**

Installable client mod for **Minecraft Java Edition `26.1`** (Fabric). Client-side only: works on singleplayer
and on any multiplayer server without server installation.

## Requirements

| Component | Version |
| :--- | :--- |
| Minecraft | `26.1` (dependency range `>=26.1 <26.1.1`) |
| Fabric Loader | `>= 0.20.1` |
| Fabric API | `0.155.3+26.1.2` or newer |
| Java | `25` |

## Installation

1. Install Fabric Loader `0.20.1+` for Minecraft `26.1`.
2. Put **Fabric API `0.155.3+26.1.2`** in `.minecraft/mods/`.
3. Put **`packstudio-fabric-26.1-1.0.0.jar`** in `.minecraft/mods/`.
4. Launch the game and press **`P`** (rebindable), or click **PackStudio** on the Title / Options / Resource
   Packs screen.

Do not install the JARs for other Minecraft versions: each one declares an exact Minecraft range.

## What is different in this build

- First 26.X target built on the pre-26.2 client API (`Minecraft.screen` / `Minecraft.setScreen`).
- Resource-pack `pack.mcmeta`/`pack.png` read through `Pack.open()` → `PackResources` (no `PackMetadataResources` yet).
- Keyboard bindings use `InputConstants.Type.KEYSYM`; folders open via `Util.getPlatform().openUri`.
- Fabric API `0.155.3+26.1.2` is the official build for 26.1 / 26.1.1 / 26.1.2.

Everything else is the same Minimalist experience on every target: resource pack library (grid/list, search,
filters, favourites, priority ordering), pack inspector with honest `pack.mcmeta` metadata, schema-versioned
profile manager (create/rename/duplicate/delete/export/import), one-click profile switching with rollback,
compatibility warnings, adaptive layout, and keyboard navigation.

## Verification

Built with Fabric Loom `1.19-SNAPSHOT`, Gradle `9.8.2` and Java `25`, then verified in CI for this exact
Minecraft version: compilation, the full JUnit 5 suite, target-identity checks and a jar metadata audit.
Interactive checks that need a real client (GPU rendering, live resource reload, real key capture, OS file
dialogs) are listed as manual smoke tests and were not executed headlessly.
