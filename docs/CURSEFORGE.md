# PackStudio — Minimalist Resource Pack & Preset Manager

**Your resource packs. Your presets. Your world.**

**PackStudio** upgrades resource pack management in **Minecraft Java Edition 26.1 – 26.3 (Fabric)** with a sleek, responsive **minimalist** workspace built natively inside the game client. Browse large pack collections effortlessly, save custom ordered presets, and swap your entire visual setup in one click.


### Supported Minecraft Versions

PackStudio is published as **independent release files**, one per Minecraft version. Download only the file that
matches your game, together with the official Fabric API build for that same version.

| Minecraft | PackStudio file | Fabric API |
| :--- | :--- | :--- |
| `26.1` | `packstudio-fabric-26.1-1.0.0.jar` | `0.155.3+26.1.2` |
| `26.1.1` | `packstudio-fabric-26.1.1-1.0.0.jar` | `0.155.3+26.1.2` |
| `26.1.2` | `packstudio-fabric-26.1.2-1.0.0.jar` | `0.155.3+26.1.2` |
| `26.2` | `packstudio-fabric-26.2-1.0.0.jar` | `0.161.0+26.2` |
| `26.3` | `packstudio-fabric-26.3-1.0.0.jar` | `0.165.2+26.3` |

All five require **Fabric Loader `0.20.1+`** and **Java 25**. Each file declares an exact Minecraft dependency
range, so a mismatched install fails immediately instead of misbehaving.


---

### Key Features

1. **Modern Resource Pack Library (Grid & List Views)**
   - Works directly with Minecraft's native `PackRepository` for `.zip` and folder resource packs.
   - Search by name or description, filter by `Enabled`, `Disabled`, `Favorites`, or `Warnings`, and sort alphabetically, by priority, or by compatibility.
   - Reorder active pack priority cleanly (`#1` highest priority at top) without touching pinned required packs.

2. **Complete Preset / Profile System**
   - Create, update, rename, duplicate, delete, export, and import JSON profiles stored in `config/packstudio/profiles/`.
   - Portable profile schema with zero machine-specific absolute paths.
   - Explicit missing-pack detection: deleted packs are flagged clearly and never silently substituted.

3. **Transactional One-Click Switching**
   - Applies your chosen preset and triggers Minecraft's native resource reload.
   - Confirms success only after reload finishes — and safely rolls back to your prior pack configuration if reload fails.
   - Press **`O`** anytime to quick-switch between saved presets!

4. **Lightweight Minimalist Aesthetic**
   - Three dark themes: **Obsidian Cyan**, **Midnight Indigo**, and **Frosted Emerald**.
   - Configurable UI transparency, default Grid/List view, smooth transition toggle, and SDL3 shortcut rebinding.
   - **Zero Mixins & Zero Shaders** — engineered on public Minecraft and Fabric APIs for maximum stability alongside Sodium and other client mods.

---

### Installation

1. Install **Fabric Loader `0.20.1+`** for **Minecraft `26.3`** (Java 25).
2. Install **Fabric API**.
3. Drop `packstudio-1.0.0+mc26.3.jar` into your `.minecraft/mods/` folder.
4. Press **`P`** or click **`PackStudio`** on the Title / Options / Resource Packs menu!

*100% Client-Side — safe to use on all vanilla and modded multiplayer servers.*
