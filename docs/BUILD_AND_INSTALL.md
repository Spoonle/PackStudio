# PackStudio — Build & Installation Guide

## Player Installation Instructions

### Requirements
- **Minecraft Java Edition**: `26.3`
- **Java Runtime**: Java `25` (bundled with modern Minecraft launchers for 26.x, or [Eclipse Temurin 25](https://adoptium.net/))
- **Mod Loader**: [Fabric Loader](https://fabricmc.net/use/installer/) `0.20.1` or newer
- **Required Dependency**: [Fabric API](https://modrinth.com/mod/fabric-api) `0.165.2+26.3` or newer

### Step-by-Step Setup
1. Install **Fabric Loader `0.20.1+`** for Minecraft `26.3` in your launcher (Official Launcher, Prism Launcher, Modrinth App, or CurseForge App).
2. Download **Fabric API** for `26.3` and place its `.jar` inside your instance's `mods/` directory.
3. Copy `packstudio-1.0.0+mc26.3.jar` into the same `mods/` directory.
4. Start Minecraft `26.3`.
5. Open PackStudio at any time by:
   - Pressing **`P`** (in-game or in menus, rebindable in Settings or Minecraft Controls), or
   - Clicking the **`PackStudio`** button in the top-right corner of the **Title Screen**, **Options Screen**, or **Resource Packs Screen**.

### Server Compatibility
PackStudio is **strictly client-side** (`"environment": "client"` in `fabric.mod.json`).
- You can join vanilla, Fabric, Paper, or proxy multiplayer servers without installing PackStudio on the server.
- Do **not** install PackStudio on a dedicated server; Fabric Loader will safely ignore client-only mods if present, though keeping server `mods/` server-only is recommended.

---

## Developer Build Instructions

### Prerequisites
- **JDK 25** (`JAVA_HOME` pointing to an OpenJDK 25 installation such as Temurin `25.0.2`).
- Outbound internet access to `maven.fabricmc.net`, `repo.maven.apache.org`, and `libraries.minecraft.net` so Fabric Loom `1.19` can resolve Minecraft `26.3` and Fabric API `0.165.2+26.3`.

### Building the Distributable JAR
From the repository root:

```bash
./gradlew clean build
```

Output artifacts:
- `build/libs/packstudio-1.0.0+mc26.3.jar` — Ready-to-distribute Fabric mod JAR
- `build/libs/packstudio-1.0.0+mc26.3-sources.jar` — Sources archive

*(A prebuilt verified release artifact is also included at `releases/packstudio-1.0.0+mc26.3.jar`.)*

### Launching a Development Client
```bash
./gradlew runClient
```

### Running Automated Tests
```bash
./gradlew test
```

---

## Configuration & Troubleshooting

- **Resetting PackStudio Settings**: Open **PackStudio -> Settings -> Reset All Settings**, or delete `.minecraft/config/packstudio/config.json` while Minecraft is closed.
- **Backing Up / Sharing Presets**: Click **Export** on any preset in the **Profiles** tab to write a portable `.json` file to `.minecraft/config/packstudio/exports/` (also copied to your clipboard). Share that `.json` file and import it on another machine via **Import JSON**.
- **Recovering Corrupted JSON**: If a power loss or manual edit corrupts `config.json` or a profile `.json`, PackStudio automatically preserves your original file as `<filename>.corrupt.<timestamp>.bak` before reinitializing cleanly.
