# PackStudio — Liquid Glass Resource Pack & Preset Manager

**Your resource packs. Your presets. Your world.**

**PackStudio** upgrades resource pack management in **Minecraft Java Edition 26.3 (Fabric)** with a sleek, responsive **Liquid Glass** workspace built natively inside the game client. Browse large pack collections effortlessly, save custom ordered presets, and swap your entire visual setup in one click.

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

4. **Lightweight Liquid Glass Aesthetic**
   - Three dark translucent themes: **Obsidian Cyan**, **Midnight Indigo**, and **Frosted Emerald**.
   - Configurable UI transparency, default Grid/List view, smooth transition toggle, and SDL3 shortcut rebinding.
   - **Zero Mixins & Zero Blur Shaders** — engineered on public 26.3 APIs for maximum stability alongside Sodium and other client mods.

---

### Installation

1. Install **Fabric Loader `0.20.1+`** for **Minecraft `26.3`** (Java 25).
2. Install **Fabric API**.
3. Drop `packstudio-1.0.0+mc26.3.jar` into your `.minecraft/mods/` folder.
4. Press **`P`** or click **`PackStudio`** on the Title / Options / Resource Packs menu!

*100% Client-Side — safe to use on all vanilla and modded multiplayer servers.*
