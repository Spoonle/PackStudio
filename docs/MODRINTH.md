# PackStudio

**Your resource packs. Your presets. Your world.**

**PackStudio** brings a fast, refined **minimalist** resource pack and preset studio directly into **Minecraft Java Edition 26.1 – 26.3 (Fabric)**. Stop dragging dozens of packs back and forth every time you switch between PvP, building, cinematics, or high-FPS play — organize your packs visually and switch presets in a single click.


### Supported Minecraft Versions

PackStudio is published as **independent release files**, one per Minecraft version. Download only the file that
matches your game, together with the official Fabric API build for that same version.

| Minecraft | PackStudio file | Fabric API |
| :--- | :--- | :--- |
| `26.1` | `PackStudio-26.1.jar` | `0.155.3+26.1.2` |
| `26.1.1` | `PackStudio-26.1.1.jar` | `0.155.3+26.1.2` |
| `26.1.2` | `PackStudio-26.1.2.jar` | `0.155.3+26.1.2` |
| `26.2` | `PackStudio-26.2.jar` | `0.161.0+26.2` |
| `26.3` | `PackStudio-26.3.jar` | `0.165.2+26.3` |

All five require **Fabric Loader `0.20.1+`** and **Java 25**. Each file declares an exact Minecraft dependency
range, so a mismatched install fails immediately instead of misbehaving.


---

## Highlights

- **Real Resource Pack Library**: Automatically discovers `.zip` archives, folder packs, and built-in packs from Minecraft's native `PackRepository` with async icon caching, instant search, filters (`All`, `Enabled`, `Disabled`, `Favorites`, `Warnings`), sorting, and **Grid / List** views.
- **One-Click Profile Switching**: Save complete resource pack stacks and priority orders (`PvP`, `Vanilla+`, `Builder`, `Performance`) and apply them in one click — or press **`O`** in-game to cycle presets on the fly.
- **Safe & Honest by Design**:
  - Never modifies or overwrites your resource pack files.
  - Uses portable pack identifiers (`file/PackName.zip`) rather than absolute paths.
  - Clearly warns when a profile references a missing pack — never silently replaces it.
  - Automatically restores your previous pack stack if a resource reload fails.
- **Pack Inspector**: View real `pack.mcmeta` descriptions, authors (when declared), pack format versions, compatibility badges, and exact priority rank (`#1` = highest override).
- **Minimalist UI (Zero Shaders, Zero Mixins)**: Choose between *Obsidian Cyan*, *Midnight Indigo*, and *Frosted Emerald*, and enjoy a flat, distraction-free interface with full compatibility with **Sodium** and modern performance mods.

---

## Shortcuts & Access

- **`P`** — Open PackStudio
- **`O`** — Quick-Switch to your next saved preset
- **Menu Button** — Optional `PackStudio` launch button on the Title, Options, and Resource Packs screens (toggleable in Settings)

---

## Requirements

- **Minecraft**: `26.3`
- **Loader**: Fabric Loader `0.20.1+`
- **Dependencies**: Fabric API (`0.165.2+26.3+`)
- **Side**: **Client-only** (works on any multiplayer server!)
