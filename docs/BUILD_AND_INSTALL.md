# PackStudio — Build & Installation Guide

## Player Installation Instructions

### Requirements

Download everything from the **[Releases page](https://github.com/Spoonle/PackStudio/releases)** — PackStudio ships
**one independent release per Minecraft version**, so open the release that matches your game.

| Minecraft | Release | JAR | Fabric API | Java | Fabric Loader |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `26.1` | `v1.0.0+mc26.1` | `packstudio-fabric-26.1-1.0.0.jar` | `0.155.3+26.1.2` | 25 | `>= 0.20.1` |
| `26.1.1` | `v1.0.0+mc26.1.1` | `packstudio-fabric-26.1.1-1.0.0.jar` | `0.155.3+26.1.2` | 25 | `>= 0.20.1` |
| `26.1.2` | `v1.0.0+mc26.1.2` | `packstudio-fabric-26.1.2-1.0.0.jar` | `0.155.3+26.1.2` | 25 | `>= 0.20.1` |
| `26.2` | `v1.0.0+mc26.2` | `packstudio-fabric-26.2-1.0.0.jar` | `0.161.0+26.2` | 25 | `>= 0.20.1` |
| `26.3` | `v1.0.0+mc26.3` | `packstudio-fabric-26.3-1.0.0.jar` | `0.165.2+26.3` | 25 | `>= 0.20.1` |

Every JAR declares an exact Minecraft dependency range, so a mismatched install fails fast with a clear message
instead of misbehaving in game.

### Step-by-Step Setup
1. Install **Fabric Loader `0.20.1+`** for your Minecraft version (`26.1` … `26.3`) in your launcher (Official Launcher, Prism Launcher, Modrinth App, or CurseForge App).
2. Download **Fabric API** for the same version and place its `.jar` inside your instance's `mods/` directory.
3. Download the matching `packstudio-fabric-<version>-1.0.0.jar` from that version's release and copy it into the same `mods/` directory.
4. Start that Minecraft version.
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
- Outbound internet access to `maven.fabricmc.net`, `repo.maven.apache.org`, and `libraries.minecraft.net` so Fabric Loom `1.19` can resolve Minecraft and the official Fabric API for the target.

### Building a Distributable JAR
From the repository root, one Minecraft version per build:

```bash
./gradlew clean build                              # Minecraft 26.3 (default)
./gradlew clean build -Pminecraft_version=26.2     # or 26.1 / 26.1.1 / 26.1.2
```

Output artifacts:
- `build/libs/packstudio-1.0.0+mc<version>.jar` — ready-to-distribute Fabric mod JAR
- `build/libs/packstudio-1.0.0+mc<version>-sources.jar` — sources archive

Verified per-version release files are staged in `releases/<version>/` by CI after each target passes its build,
tests and jar audit, and are published as assets on the matching [release](https://github.com/Spoonle/PackStudio/releases).

### Building All Five Release Files
```bash
for v in 26.1 26.1.1 26.1.2 26.2 26.3; do
  ./gradlew clean build -Pminecraft_version="$v"
  mkdir -p "releases/$v"
  cp build/libs/packstudio-*+mc"$v".jar "releases/$v/packstudio-fabric-$v-1.0.0.jar"
done
```

### Verifying a JAR
```bash
tools/verify_release_jar.sh releases/26.2/packstudio-fabric-26.2-1.0.0.jar 26.2 26.2
```
The audit checks the packaged `fabric.mod.json` (Minecraft range, client-only, Java 25, Fabric API lower bound),
the entrypoint class, the bundled translations and the build identity recorded in the JAR.

### Launching a Development Client
```bash
./gradlew runClient                              # 26.3
./gradlew runClient -Pminecraft_version=26.1     # or any supported target
```

### Running Automated Tests
```bash
./gradlew test                                   # 26.3
./gradlew test -Pminecraft_version=26.1.2        # same suite, different target
```

The suite includes target-identity and jar-metadata audits, so it fails if the classes were compiled against a
different Minecraft version than the one the build targets.

### What the automated suite cannot verify
Interactive behaviour needs a real Minecraft client and is **not** covered: GPU rendering, texture upload,
live resource-pack reloads, real SDL key capture, OS folder dialogs and native toasts. Perform the manual smoke
test listed in [`docs/VERIFICATION_26X.md`](VERIFICATION_26X.md) before publishing a release.

---

## Configuration & Troubleshooting

- **Resetting PackStudio Settings**: Open **PackStudio -> Settings -> Reset All Settings**, or delete `.minecraft/config/packstudio/config.json` while Minecraft is closed.
- **Backing Up / Sharing Presets**: Click **Export** on any preset in the **Profiles** tab to write a portable `.json` file to `.minecraft/config/packstudio/exports/` (also copied to your clipboard). Share that `.json` file and import it on another machine via **Import JSON**.
- **Recovering Corrupted JSON**: If a power loss or manual edit corrupts `config.json` or a profile `.json`, PackStudio automatically preserves your original file as `<filename>.corrupt.<timestamp>.bak` before reinitializing cleanly.
