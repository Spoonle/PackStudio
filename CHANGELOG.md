# Changelog

All notable changes to **PackStudio** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
