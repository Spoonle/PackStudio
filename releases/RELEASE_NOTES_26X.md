<!-- GitHub Releases are published per Minecraft version from releases/<version>/RELEASE_PAGE.md.
     Regenerate every page body with: python3 tools/render_release_notes.py -->

# PackStudio 1.0.0 — Minecraft 26.X release set

Five **independent** release files, one per supported Minecraft version. Download only the JAR that matches
your Minecraft version; they are not interchangeable and each declares an exact Minecraft dependency range.

| Release folder | File | Minecraft | Fabric Loader | Fabric API | Java |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `releases/26.1/` | `PackStudio-26.1.jar` | `26.1` (`>=26.1 <26.1.1`) | `>=0.20.1` | `0.155.3+26.1.2` | 25 |
| `releases/26.1.1/` | `PackStudio-26.1.1.jar` | `26.1.1` (`>=26.1.1 <26.1.2`) | `>=0.20.1` | `0.155.3+26.1.2` | 25 |
| `releases/26.1.2/` | `PackStudio-26.1.2.jar` | `26.1.2` (`>=26.1.2 <26.2`) | `>=0.20.1` | `0.155.3+26.1.2` | 25 |
| `releases/26.2/` | `PackStudio-26.2.jar` | `26.2` (`>=26.2 <26.3`) | `>=0.20.1` | `0.161.0+26.2` | 25 |
| `releases/26.3/` | `PackStudio-26.3.jar` | `26.3` (`~26.3`) | `>=0.20.1` | `0.165.2+26.3` | 25 |

Each folder contains:

- `PackStudio-<version>.jar` — the installable client mod for that Minecraft version
- `PackStudio-<version>-sources.jar` — matching sources
- `SHA256SUMS.txt` — checksum of the release JAR

## Verification performed for every target

Every JAR above was produced by its own Fabric Loom build (`-Pminecraft_version=<version>`) and passed, in CI:

1. Dependency resolution against the official Minecraft, Fabric Loader and Fabric API for that version.
2. Compilation of the shared sources plus that version's compatibility layer.
3. The full JUnit 5 feature suite (config persistence, pack discovery and metadata inspection, icon decoding,
   search/filter/sort, priority ordering, profile CRUD, import/export, transactional apply with rollback,
   adaptive layout, keyboard navigation, rendering smoke tests) plus target-identity and jar-metadata audits.
4. A jar metadata audit (`tools/verify_release_jar.sh`): `fabric.mod.json` Minecraft range, client-only flag,
   Java 25 minimum, real Fabric API lower bound, entrypoint present, build identity matches the target.

## Not verified (requires a real Minecraft client)

These cannot run headlessly and were **not** executed:

- Live rendering on a GPU (texture upload, GUI scaling at real window sizes, Sodium rendering).
- A real resource-pack reload of a running client and the resulting visual change.
- Real keybind capture through SDL and the in-game Controls screen.
- OS-level folder opening and native system toasts.

Manual smoke test per target: launch that Minecraft version with Fabric Loader and Fabric API, install the
matching JAR only, then open PackStudio with `P`, switch tabs, stage and apply a pack change, create/apply a
profile, and quick-switch with `O`.
