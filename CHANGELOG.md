# Changelog

All notable changes to Copy Paster are documented in this file. Update it on every release (see [`CLAUDE.md`](CLAUDE.md)).

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
