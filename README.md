<p align="center">
  <img src="docs/banner_new.png" alt="Copy Paster — copy and paste Minecraft structures with player-relative positioning" width="640">
</p>

<p align="center">
  <strong>Copy cuboid regions to files. Paste them back exactly where you mean to — anchored to your position.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.1.2-green?style=flat-square" alt="Minecraft 26.1.2">
  <img src="https://img.shields.io/badge/Fabric-0.19.2-DBD0B4?style=flat-square&logo=fabricmc&logoColor=white" alt="Fabric Loader 0.19.2">
  <img src="https://img.shields.io/badge/Java-25-blue?style=flat-square&logo=openjdk&logoColor=white" alt="Java 25">
  <img src="https://img.shields.io/badge/version-3.1.0-informational?style=flat-square" alt="Version 3.1.0">
</p>

---

**Copy Paster** is a Fabric **client + server** mod for survival and creative builders who want a fast, visual way to duplicate structures without leaving the game. Select a box with keybinds, name it in chat, then paste it later — position follows **where you stand** at copy time and paste time, so offsets stay intuitive.

- **`[` / `]` copy** — two corners, live wireframe preview, name in chat  
- **Paste options** — absolute coords, rotation, mirror, skip air, overwrite confirm  
- **Paste ghost preview** — cyan box when blocks would be overwritten (respects rotation/mirror)  
- **Undo** — `/pasteundo` restores the last paste (in-memory until restart)  
- **Ukrainian (`uk_ua`)** — full UI strings alongside English  
- **Configurable limits** — server `copypaster-server.yml`; client highlight + preview cap via `copypaster.yml` or Mod Menu  
- **Web UI** (optional) — browser 3D viewer/editor with **texture-accurate** block preview, palette remap, voxel paint  

> Maintainer docs: [`CLAUDE.md`](CLAUDE.md) · Release notes: [`CHANGELOG.md`](CHANGELOG.md)

---

## Install

| Where | What |
|-------|------|
| **Dedicated server** | `copy_paster-<version>.jar` in `mods/` + **Fabric API** |
| **Each player who copies** | **Same JAR** in client `mods/` + bind **`[`** / **`]`** under *Options → Controls → Miscellaneous* |

**Server-only mode:** `/paste`, `/copylist`, `/copyinfo`, `/copydelete`, `/copyweb`, and the web UI still work. **World copy requires the client mod and keybinds.**

**Optional:** [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config) for in-game client settings.

**Optional:** [LuckPerms](https://luckperms.net/) — grant `copypaster.*` nodes to non-ops; **vanilla operators are always allowed** (copy + commands).

---

## Quick start

### 1 · Save a region

1. Stand at the **anchor** point you want for this copy.  
2. Bind and press **`[`** on corner 1, then **`]`** on corner 2 (wireframe follows your crosshair for the end corner).  
3. Type a structure name in **chat** (`a`–`z`, `0`–`9`, `_`, `-`, max 64 chars). Type **`cancel`** to abort (timeout from server config, default 60 s).

### 2 · Paste it back

1. Stand in the **same relative spot** you used when copying (or use `at` — see below).  
2. `/paste <name>` — if the ghost warns about overwrites, run the suggested `… confirm` command.  
3. Save the **undo ID** from chat for `/pasteundo <id>`.

**Examples:**

```text
/paste house
/paste house at 100 64 -20
/paste house rotate 90 mirror left_right noair confirm
```

---

## Commands

| Command | Description |
|---------|-------------|
| `/paste <name>` | Paste at player-relative anchor |
| `/paste <name> at <x> <y> <z>` | Paste with min corner at world coordinates |
| `/paste <name> rotate <90\|180\|270>` | Rotate before placing |
| `/paste <name> mirror <left_right\|front_back>` | Mirror before placing |
| `/paste <name> noair` | Skip air blocks in the structure (do not clear destination air) |
| `/paste <name> … confirm` | Paste after overwrite warning (modifiers can be combined) |
| `/pasteundo <id>` | Restore blocks from one paste |
| `/copylist` | List saved structures |
| `/copyinfo <name>` | Size, dimension, offset, metadata |
| `/copydelete <name>` | Remove `.nbt` + `.json` |
| `/copyweb` | Print web UI URL (when enabled) |

**Access:** vanilla **operator** always has full access. With **LuckPerms** installed, **non-ops** need the nodes below:

| Node | Allows |
|------|--------|
| `copypaster.copy` | `[` / `]` region copy (C2S) |
| `copypaster.paste` | `/paste` |
| `copypaster.pasteundo` | `/pasteundo` |
| `copypaster.copylist` | `/copylist` |
| `copypaster.copyinfo` | `/copyinfo` |
| `copypaster.copydelete` | `/copydelete` |
| `copypaster.copyweb` | `/copyweb` |

---

## Web UI (viewer + editor)

Optional **browser** interface for saved structures. Disabled by default.

| Feature | Description |
|---------|-------------|
| **3D viewer** | Orbit + **WASD** pan, layer slider, isometric toggle |
| **Colors / Textures** | Map-color cubes or Minecraft 1.21.4 textures (cutout, glass, entity blocks) |
| **Palette remap** | Replace one block type with another across the structure |
| **Voxel paint** | Click blocks to repaint or erase |
| **Download** | Export edited `.nbt` |

1. On the server, edit `config/copypaster-server.yml`:
   - `limits.maxVolume` / `limits.sessionTimeoutSeconds` (optional)
   - `web.enabled: true`
   - `web.port: 8792` (default)
   - `web.bind: 127.0.0.1` (or `0.0.0.0` for LAN/VPN)
   - `web.publicHost: 192.168.50.100` (optional URL hint in logs)
   - Copy `web.authToken` for the browser login screen
2. Restart the server. Run **`/copyweb`** in-game for the URL.
3. Open the URL, paste the token, select a structure, edit, **Save** — then `/paste` in-game as usual.

Build embedded assets: `./gradlew buildWeb` (included in `./gradlew jar`). After a server update, hard-refresh the browser (`Ctrl+Shift+R`).

---

## Controls

| Input | Action |
|-------|--------|
| **`[`** | Set corner 1 (look at a solid block) |
| **`]`** | Set corner 2 and send region to server |
| Chat name | Save structure after corners are set |
| **`cancel`** in chat | Abort naming session |

Keybinds are **unbound by default** — assign under **Options → Controls → Miscellaneous → Copy Paster**.

### Web viewer (when enabled)

| Input | Action |
|-------|--------|
| Drag | Orbit camera |
| Scroll | Zoom |
| **W** / **A** / **S** / **D** | Pan (click viewer first) |
| **Q** / **E** | Down / up |

---

## Limits & storage

| | |
|---|---|
| **Max volume** | `limits.maxVolume` in server config (default **32 768**); client preview cap in `config/copypaster.yml` |
| **Copy session timeout** | `limits.sessionTimeoutSeconds` in server config (default **60**) |
| **Files** | Server `copypaster/structures/` — `<name>.nbt` + `<name>.json` |
| **Undo** | In memory only; cleared on server restart |
| **Highlight colour** | Client `config/copypaster.yml` |

---

## Build

```bash
./gradlew clean jar
# → build/libs/copy_paster-<version>.jar
```

Requires **Java 25**. See [`CLAUDE.md`](CLAUDE.md) for deploy runbook (Fabric on gserver).

---

## Roadmap

Planned work — not implemented yet.

### In-game functionality

- **Paste with physics** — no air below pasted blocks (support blocks stay filled)
- **Persistent undo** — survive server restarts
- **Copy locator metadata** — store capture origin and last paste in `.json`; expose in `/copyinfo` and the web UI

### Web viewer

- **Sign text**, **item frames**, and **paintings** in 3D
- **More modded blocks** — texture and display handling beyond vanilla + current fallbacks
- **Banner pattern composing** — full pattern NBT on banner geometry (not base dye only)
- **Performance mode** for huge structures — LOD or cull distant voxels

---

## License

See repository license file if present; otherwise treat as private CrazyHouse tooling until a license is added.
