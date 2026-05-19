# Changelog

All notable changes to Copy Paster are documented in this file. Update it on every release (see [`CLAUDE.md`](CLAUDE.md)).

## [Unreleased]

## [3.1.0] - 2026-05-19

### Added

- **Keybind-only copy** — `[` / `]` select corners and send the region to the server (no `/copy` command).
- **Paste options** — `/paste <name> at <x> <y> <z>`, `rotate 90|180|270`, `mirror left_right|front_back`, `noair`, and `confirm` (combinable).
- **Paste ghost** — preview uses the rotated/mirrored world bounding box.
- **LuckPerms** — permission nodes (`copypaster.copy`, `copypaster.paste`, …) when LuckPerms is installed; vanilla **operator** always allowed for in-game copy and commands.
- **Config** — `limits.maxVolume` and `limits.sessionTimeoutSeconds` in `config/copypaster-server.yml`; client `limits.maxVolume` in `config/copypaster.yml` (+ Mod Menu slider).
- **`CopyRegionPayload`** — C2S region + S2C HUD sync; client `CopyRegionClientHandler`.

### Changed

- **Permissions** — non-ops use LuckPerms nodes when LP is installed; ops bypass LP checks.
- **Overwrite / undo** — capture and warning use the transformed paste footprint.
- **Lang files** — removed unused keys; every `Component.translatable` key in code exists in `en_us` and `uk_ua`.

### Removed

- **`/copy` command** (interactive and coordinate forms).
- **Interactive selection** — attack corners, use-to-cancel, and `CopySelectPayload` session.

### Breaking

- Copying in-game requires the **client mod** and bound **`[`** / **`]`** keys.
- Server-only installs can still **paste** and use the web UI but cannot copy from the world in-game.

### Fixed

- **LuckPerms + copy** — permission check no longer denies **operators** who lack explicit `copypaster.copy` (and no silent failure).
- **Client chat feedback** — corner messages use `copypaster.overlay.pos1` / `pos2_sending` (no missing translation keys).
- **`/paste` Brigadier** — `at …` modifier branch no longer recurses infinitely.

## [3.0.0] - 2026-05-18

### Added

- **Web UI texture rendering** — structure viewer loads Minecraft 1.21.4 assets with per-block handling (cutout, transparency, entity atlases, composed textures).
- **Special block renderers** — banners (palette color), beds, chests (single/double), doors/trapdoors, bells, hanging lanterns, mob/player heads (head crop + skin lookup), grass/podzol/mycelium (side + biome tint), thin meshes for fences, banners, bells, skulls.
- **Block entity in structure JSON** — banner patterns, skull owners, and other NBT exported from structure templates for web display.
- **Viewer controls** — WASD / arrow keys pan, Q/E vertical move; click canvas to focus.
- **Snow layers** — instanced cubes scale by `layers` block state.

### Changed

- **Web UI default experience** — texture mode is the intended way to preview structures; color mode remains for performance.
- **Texture path resolution** — HEAD probe + mapped paths for stairs, slabs, walls, crops, mod fences (`mcwfences`), glass, panes; avoids mass 404s from invented `block/{name}.png` suffixes.

### Fixed

- **Web UI data** — structures load all blocks from NBT (not empty `filterBlocks`); save-after-edit uses vanilla palette layout.
- **Layer slider** — stable instanced mesh keys prevent white flash when scrubbing Y.
- **Large `/copy` preview** — null entity pick results and HUD scan cap at max volume (32 768).

### Notes

- Banner **patterns** in 3D view show base dye color only (instanced cubes cannot map pattern NBT per face); full pattern compositing is a future improvement.
- After upgrading the server JAR, **hard-refresh** the browser (`Ctrl+Shift+R`) so the embedded SPA reloads.

## [2.2.4] - 2026-05-18

### Fixed

- **Client crash on large `/copy` preview** — `Entity.getPickResult()` can be null (villagers, etc.); use spawn-egg fallback or skip. Skip full block/entity HUD scan when volume exceeds 32 768 (avoids freezing on village-sized boxes).

## [2.2.3] - 2026-05-18

### Fixed

- **Web UI textures** — resolve block images via HEAD probe + fallback paths (`_top`, `_front`, entity chest, etc.) so multi-face blocks like crafting tables no longer 404 on `block/{name}.png`.

## [2.2.2] - 2026-05-18

### Added

- **Web UI texture mode** — viewer toolbar **Colors** / **Textures**; textures load from Minecraft 1.21.4 assets (nearest-filter, falls back to map-color cubes when a texture is missing).

## [2.2.1] - 2026-05-18

### Fixed

- **Web UI 3D viewer** — structures export block data from NBT instead of `filterBlocks(null)` (which returned an empty list). Save-after-edit NBT now uses vanilla `size` / `palette` layout.

## [2.2.0] - 2026-05-18

### Added

- **Web UI** — embedded HTTP server (default port **8792**, disabled by default) serves a browser app to view saved structures in 3D, remap block palettes, and paint individual voxels before pasting in-game.
- **Server config** — `config/copypaster-server.yml` (`web.enabled`, `web.port`, `web.bind`, `web.authToken`, `web.publicHost`).
- **`/copyweb`** — prints the web UI URL when the server is enabled (op-only).

### Notes

- Enable the web UI on gserver: set `web.enabled: true`, `web.bind: 0.0.0.0`, and `web.publicHost` to the LAN IP; use the Bearer token from `web.authToken` in the browser login screen.
- Build the SPA with `./gradlew buildWeb` or full `./gradlew jar` (set `SKIP_WEB_BUILD=1` to skip Node during Java-only builds if assets are already present).

## [2.1.0] - 2026-05-17

### Added

- **Ukrainian language (`uk_ua`)** — chat messages, action-bar hints, keybind labels, and Mod Menu config UI.
- **Localization** — all player-facing strings in `assets/copypaster/lang/`; code uses `Component.translatable` (English `en_us` unchanged in meaning).

### Notes

- Chat abort keyword remains **`cancel`** in all languages (commands stay English: `/copy`, `/paste`, etc.).

## [2.0.0] - 2026-05-17

### Added

- **Interactive `/copy`** — run `/copy` with no arguments; attack blocks for corner 1 and corner 2; live blue selection wireframe follows your crosshair between corners; press **Use** to cancel.
- **Client + server model** — same JAR on dedicated server and on each player client for selection highlights, paste preview, and input handling.
- **`config/copypaster.yml`** — configurable selection highlight colour (RGBA).
- **ModMenu integration** (optional) — opens Cloth Config sliders when Mod Menu and Cloth Config are installed.
- **`CHANGELOG.md`** — release notes for this and future versions.
- **Lang entries** for Copy Paster keybind names in Controls.

### Changed

- **Breaking:** primary copy workflow is interactive `/copy` instead of six coordinate arguments.
- **Keybinds `[` / `]`** — no longer bound by default; assign them in **Options → Controls → Miscellaneous** if you want the legacy corner shortcut (still sends `/copy x1 y1 z1 x2 y2 z2`).
- **`fabric.mod.json` description** — documents client + server install explicitly.

### Deprecated

- `/copy <x1> <y1> <z1> <x2> <y2> <z2>` — still supported for scripts and server-only setups; may be removed in a future major version.

### Notes

- Selection highlights are **client-side wireframes** (no custom shaders in this release).
- **Degraded mode:** server-only install supports coordinate `/copy` and all `/paste` commands; bare `/copy` requires the client mod.

## [1.1.1] - prior releases

- Coordinate-based `/copy`, paste ghost preview, `[` / `]` keybinds (default bound to bracket keys), undo, structure library commands.
