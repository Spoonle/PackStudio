#!/usr/bin/env python3
"""Renders the GitHub Release page body for every supported Minecraft version.

PackStudio ships one independent release per Minecraft version, and every one of those pages must list the
whole version set (so a visitor landing on 26.3 still finds 26.1) while keeping that page's own download,
requirements and change list accurate.

Usage:
    python3 tools/render_release_notes.py                 # write releases/<version>/RELEASE_PAGE.md
    python3 tools/render_release_notes.py --version 26.2  # print one body to stdout
"""
from __future__ import annotations

import argparse
import pathlib
import sys

REPO = "Spoonle/PackStudio"
MOD_VERSION = "1.0.0"
RAW = f"https://raw.githubusercontent.com/{REPO}/main"

# Single source of truth per target: must stay in sync with `minecraftTargets` in build.gradle.
TARGETS = [
    {
        "mc": "26.1",
        "family": "26.1",
        "loader": "0.20.1",
        "fabric_api": "0.155.3+26.1.2",
        "range": ">=26.1 <26.1.1",
        "changes": [
            "First 26.X target built on the pre-26.2 client API (`Minecraft.screen` / `Minecraft.setScreen`).",
            "Resource-pack `pack.mcmeta` / `pack.png` read through `Pack.open()` → `PackResources` (no `PackMetadataResources` yet).",
            "Keyboard bindings use `InputConstants.Type.KEYSYM`; folders open via `Util.getPlatform().openUri`.",
            "Official Fabric API build for 26.1 / 26.1.1 / 26.1.2 is `0.155.3+26.1.2`.",
        ],
    },
    {
        "mc": "26.1.1",
        "family": "26.1",
        "loader": "0.20.1",
        "fabric_api": "0.155.3+26.1.2",
        "range": ">=26.1.1 <26.1.2",
        "changes": [
            "Same API family as 26.1; official Fabric API `0.155.3+26.1.2` covers 26.1, 26.1.1 and 26.1.2.",
            "Dependency metadata is pinned to `>=26.1.1 <26.1.2`, so this JAR loads only on 26.1.1.",
        ],
    },
    {
        "mc": "26.1.2",
        "family": "26.1",
        "loader": "0.20.1",
        "fabric_api": "0.155.3+26.1.2",
        "range": ">=26.1.2 <26.2",
        "changes": [
            "Same API family as 26.1; official Fabric API `0.155.3+26.1.2`.",
            "Dependency metadata is pinned to `>=26.1.2 <26.2`.",
        ],
    },
    {
        "mc": "26.2",
        "family": "26.2",
        "loader": "0.20.1",
        "fabric_api": "0.161.0+26.2",
        "range": ">=26.2 <26.3",
        "changes": [
            "Uses the new `Minecraft.gui` client holder (`gui.screen()` / `gui.setScreen()`).",
            "Still the pre-26.3 pack metadata API (`Pack.open()` → `PackResources`) and `InputConstants.Type.KEYSYM`.",
            "Official Fabric API build for 26.2 is `0.161.0+26.2`.",
        ],
    },
    {
        "mc": "26.3",
        "family": "26.3",
        "loader": "0.20.1",
        "fabric_api": "0.165.2+26.3",
        "range": "~26.3",
        "changes": [
            "Baseline release: Minecraft `26.3`, Fabric Loader `0.20.1`, Fabric API `0.165.2+26.3`, Loom `1.19-SNAPSHOT`, Java 25.",
            "Uses `Pack.openMetadata()` → `PackMetadataResources`, `InputConstants.Type.KEYBOARD` and `Blaze3D.openPath`.",
            "First stable PackStudio release; the previously shipped `1.0.0+mc26.3` JAR is unchanged.",
        ],
    },
]


def tag(target: dict) -> str:
    return f"v{MOD_VERSION}+mc{target['mc']}"


def release_url(target: dict) -> str:
    # `+` must be percent-encoded inside a release tag URL.
    return f"https://github.com/{REPO}/releases/tag/v{MOD_VERSION}%2Bmc{target['mc']}"


def versions_table(current: dict) -> str:
    """Every supported version with a link to its own release; the current page is marked, not linked."""
    rows = [
        "| Minecraft | Release | Fabric API | Status |",
        "| :--- | :--- | :--- | :--- |",
    ]
    for target in TARGETS:
        if target["mc"] == current["mc"]:
            release = f"**`{MOD_VERSION}+mc{target['mc']}`**"
            status = "**This page**"
        else:
            release = f"[`{MOD_VERSION}+mc{target['mc']}`]({release_url(target)})"
            status = "Available"
        rows.append(
            f"| `{target['mc']}` | {release} | `{target['fabric_api']}` | {status} |"
        )
    return "\n".join(rows)


def version_changes() -> str:
    """Per-version change list, so each page documents how every build differs."""
    blocks = []
    for target in TARGETS:
        bullets = "\n".join(f"  - {line}" for line in target["changes"])
        blocks.append(f"- **Minecraft `{target['mc']}`** — `{MOD_VERSION}+mc{target['mc']}`\n{bullets}")
    return "\n".join(blocks)


def build_body(target: dict) -> str:
    mc = target["mc"]
    jar = f"PackStudio-{mc}.jar"
    sources = f"PackStudio-{mc}-sources.jar"

    return f"""# PackStudio `{MOD_VERSION}+mc{mc}`

> **Your resource packs. Your presets. Your world.**

Client-side **Minecraft Java Edition `{mc}` (Fabric)** mod — a modern, native **Liquid Glass** resource pack
library and one-click preset manager built directly into the Minecraft client.

> **This release is only for Minecraft `{mc}`.** PackStudio ships one independent release per Minecraft
> version; every other version has its own release with its own JAR and its own change list.

---

## All supported versions

{versions_table(target)}

Each release contains exactly one Minecraft version — there is no universal JAR, and each JAR declares an
exact Minecraft dependency range so a mismatched install fails immediately instead of misbehaving in game.

---

## Quick Installation

1. Install **Minecraft Java Edition `{mc}`** with **[Fabric Loader `{target['loader']}+`](https://fabricmc.net/use/installer/)** (running on **Java `25`**).
2. Download **[Fabric API](https://modrinth.com/mod/fabric-api)** (`{target['fabric_api']}` or newer) and place it in your `.minecraft/mods/` folder.
3. Scroll down to **Assets**, download **`{jar}`**, and drop it into `.minecraft/mods/`.
4. Launch Minecraft and press **`P`** — or click **`PackStudio`** in the top-right corner of the **Title Screen**, **Options Screen**, or **Resource Packs Screen**.

> **100% Client-Side**: Works on singleplayer worlds and any multiplayer server (vanilla, Fabric, Paper, Velocity) without server installation.

---

## Compatibility & Requirements

| Component | Version / Requirement |
| :--- | :--- |
| **Minecraft** | `{mc}` (Java Edition) — dependency range `{target['range']}` |
| **Mod Loader** | Fabric Loader `>= {target['loader']}` |
| **Required Dependency** | Fabric API `{target['fabric_api']}` or newer |
| **Java Runtime** | Java `25` |
| **Side / Compatibility** | Client-only (`Zero Mixins`, `Zero Blur Shaders` — **Sodium** compatible) |
| **API family** | `{target['family']}` (shared codebase + one version-specific compatibility layer) |

---

## Screenshots

| Resource Pack Library (Grid — *Obsidian Cyan*) | Resource Pack Library (List — *Frosted Emerald*) |
| :---: | :---: |
| ![Library Grid]({RAW}/docs/screenshots/01-library-grid-obsidian.png) | ![Library List]({RAW}/docs/screenshots/02-library-list-emerald.png) |

| Overview & One-Click Presets | Profile Manager & Missing-Pack Diagnostics |
| :---: | :---: |
| ![Overview]({RAW}/docs/screenshots/03-overview-dashboard.png) | ![Profiles]({RAW}/docs/screenshots/04-profile-manager.png) |

| Persistent Settings & Shortcuts (*Midnight Indigo*) |
| :---: |
| ![Settings]({RAW}/docs/screenshots/05-settings-indigo.png) |

---

## What's changed in this build (`{MOD_VERSION}+mc{mc}`)

{chr(10).join(f"- {line}" for line in target["changes"])}

### Version differences across the whole 26.X line

Every target is the same mod: the same Liquid Glass UI, the same features, the same behaviour. Only the four
Minecraft APIs that actually differ between 26.1 and 26.3 are implemented per API family, behind one small
`com.packstudio.compat` seam:

- **Client screen access** — `Minecraft.screen` / `setScreen` (26.1), `Minecraft.gui` (26.2+).
- **Pack metadata** — `Pack.open()` → `PackResources` (26.1–26.2), `Pack.openMetadata()` → `PackMetadataResources` (26.3).
- **Key mapping type** — `InputConstants.Type.KEYSYM` (26.1–26.2), `InputConstants.Type.KEYBOARD` (26.3).
- **Opening folders** — `Util.getPlatform().openUri(uri)` (26.1–26.2), `Blaze3D.openPath` (26.3).

{version_changes()}

---

## Features

### Resource Pack Library (`All Packs` & `Favorites`)
- Discovers `.zip` archives, unpacked folders, and built-in packs directly from Minecraft's native `PackRepository` (no fake entries).
- Asynchronous `pack.png` `DynamicTexture` decoding and `pack.mcmeta` inspection on `Util.backgroundExecutor()` so the UI thread never blocks.
- Real-time search, filter pills (`All`, `Enabled`, `Disabled`, `Favorites`, `Warnings`), 5 sort modes, **Grid / List** layout toggle, favorites, and top-to-bottom priority ordering (`#1` overrides `#2` while keeping pinned base packs intact).

### Schema-Versioned Profile Manager (`config/packstudio/profiles/`)
- **Create, Update, Rename, Duplicate, Delete, Export, and Import** presets (`PvP`, `Vanilla+`, `Builder`, `Performance`, etc.) using portable pack IDs.
- **Honest Missing-Pack Detection**: Deleted packs referenced by a preset are flagged clearly (`⚠ N missing pack(s)`) and never silently replaced.
- **Safe Storage**: Atomic JSON writes (`JsonStorage`) and automatic `.corrupt.<timestamp>.bak` backup quarantine for damaged JSON files; never modifies user pack files.

### Transactional One-Click Profile Switching
- Validates the preset, applies exact priority order via `PackRepository#setSelected`, and triggers `Minecraft#reloadResourcePacks()`.
- Confirms profile activation **strictly after reload completion** and automatically restores your previous configuration if reload fails.

### Pack Inspector & Liquid Glass Settings
- Displays authentic pack descriptions, authors (strictly when declared in `pack.mcmeta`), pack formats, compatibility badges, state, and priority rank.
- Three dark translucent themes (*Obsidian Cyan*, *Midnight Indigo*, *Frosted Emerald*), adjustable opacity (`45%`–`96%`), default view mode, smooth animation toggle, launch button toggles, and interactive SDL3 shortcut rebinding.

### Default Controls
- **`P`** — Open PackStudio
- **`O`** — Quick-Switch to next saved preset (with native toast feedback)
- **`↑` / `↓` / `Space` / `Esc`** — Keyboard library navigation & unsaved-changes guard

---

## Verification for this build

Built with the official unobfuscated Minecraft toolchain (Fabric Loom `1.19-SNAPSHOT`, Gradle `9.8.2`, Java `25`),
then verified in CI for **Minecraft `{mc}`** specifically:

- dependency resolution against that version's real Minecraft, Fabric Loader and Fabric API;
- compilation of the shared sources plus the `{target['family']}` compatibility layer;
- the full JUnit 5 suite (config persistence + corruption recovery, path traversal, real ZIP/folder pack
  discovery, apply + rollback, profile CRUD/import/export, widget render/input, adaptive layout, all tabs and
  views, entrypoint registration, target-identity and jar-metadata audits);
- a jar metadata audit (`tools/verify_release_jar.sh`) covering the Minecraft range, client-only flag, Java 25
  minimum, real Fabric API lower bound, entrypoint and build identity.

**Not verified (requires a real Minecraft client):** GPU rendering and texture upload at real GUI scales, a live
resource-pack reload of a running game, real SDL key capture, and OS folder dialogs / native toasts. Run the
manual smoke test in [`docs/VERIFICATION_26X.md`](https://github.com/{REPO}/blob/main/docs/VERIFICATION_26X.md)
before relying on those.

---

## Attached Assets

- **`{jar}`** — playable client mod JAR *(drop into `.minecraft/mods/`)*
- **`{sources}`** — developer Java 25 sources archive
- **`SHA256SUMS.txt`** — checksum manifest for the attached files

---

## Links

- [All PackStudio releases](https://github.com/{REPO}/releases) · [Build & install guide](https://github.com/{REPO}/blob/main/docs/BUILD_AND_INSTALL.md) · [Compatibility & architecture](https://github.com/{REPO}/blob/main/docs/MULTIVERSION_COMPATIBILITY.md) · [Verification record](https://github.com/{REPO}/blob/main/docs/VERIFICATION_26X.md)
- [Report an issue](https://github.com/{REPO}/issues) · [Changelog](https://github.com/{REPO}/blob/main/CHANGELOG.md)
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", help="Minecraft version to render ('26.1', '26.2', ...)")
    parser.add_argument("--outdir", default="releases", help="directory holding the per-version folders")
    args = parser.parse_args()

    by_version = {target["mc"]: target for target in TARGETS}
    if args.version:
        target = by_version.get(args.version)
        if target is None:
            print(f"unknown Minecraft version '{args.version}'; known: {', '.join(by_version)}", file=sys.stderr)
            return 2
        print(build_body(target))
        return 0

    outdir = pathlib.Path(args.outdir)
    for target in TARGETS:
        path = outdir / target["mc"] / "RELEASE_PAGE.md"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(build_body(target), encoding="utf-8")
        print(f"wrote {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
