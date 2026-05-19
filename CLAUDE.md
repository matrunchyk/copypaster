> See [`../CLAUDE.md`](../CLAUDE.md) for shared server infrastructure, RCON usage, resource-pack tooling, and conventions across all plugins in the `minecraft` repo.

# Copy Paster

Fabric **client + server** mod (one JAR, install on both) that copies cuboid regions to `.nbt` files and pastes them back with **player-relative offset** positioning. Copy via **`[` / `]`** keybinds (client); paste and management commands on the server. **Operators always have access.** With LuckPerms, non-ops need `copypaster.*` nodes.

| | |
|---|---|
| **Mod id** | `copypaster` |
| **Artifact** | `build/libs/copy_paster-<version>.jar` |
| **Minecraft** | 26.1.2 |
| **Loader** | Fabric 0.19.2 + Fabric API |
| **Java** | 25 |

---

## Release discipline

On **every release**:

1. Bump `version` in [`build.gradle.kts`](build.gradle.kts) and [`src/main/resources/fabric.mod.json`](src/main/resources/fabric.mod.json) together.
2. Append a dated section to [`CHANGELOG.md`](CHANGELOG.md) (Added / Changed / Deprecated / Breaking as needed).
3. `./gradlew clean jar` before deploy.

---

## Build

```bash
cd ~/code/minecraft/copy_paster
./gradlew clean jar   # before deploy
./gradlew jar         # incremental
```

---

## Deploy (gserver)

Target: **Fabric** on `gserver` (`192.168.50.100`), install dir `D:\Games\Minecraft\`, mods folder `D:\Games\Minecraft\mods\`.

Use `stop-fabric-mc.ps1` / `start-fabric-mc.ps1` (not the legacy Paper scripts). Full RCON env and password: parent [`../CLAUDE.md`](../CLAUDE.md).

```bash
# 0. Bump version in build.gradle.kts + fabric.mod.json + CHANGELOG.md

# 1. Build
./gradlew clean jar

# 2. Announce restart
export MINECRAFT_RCON_PASSWORD='tqy0Te3bEKRASv6Ig1EnLjdQhaZuYjPqn4fi1NKs4'
python3 scripts/gserver/minecraft_rcon.py say "[CopyPaster] Server restarting in 30 seconds."

# 3. Wait, save, stop
ssh gserver 'powershell -Command "Start-Sleep -Seconds 30; Write-Output done"'
python3 scripts/gserver/minecraft_rcon.py save-all
ssh gserver 'powershell -File D:\Games\Minecraft\stop-fabric-mc.ps1'

# 4. Deploy mod JAR (server)
scp ./build/libs/copy_paster-*.jar gserver:'D:/Games/Minecraft/mods/'

# 5. Start and verify
ssh gserver 'powershell -File D:\Games\Minecraft\start-fabric-mc.ps1'
ssh gserver 'powershell -Command "Start-Sleep 25; Get-Content D:\\Games\\Minecraft\\logs\\latest.log -Tail 40"'

python3 scripts/gserver/minecraft_rcon.py say "[CopyPaster] deployed."
```

**Players:** install the **same JAR** in the client `mods/` folder for **`[` / `]`** copy, selection HUD, and paste ghost preview.

**Degraded mode:** server-only install — `/paste`, list/info/delete/web still work; **no in-game world copy** without the client mod and keybinds.

---

## Directory layout

```
copy_paster/
├── CHANGELOG.md
├── build.gradle.kts
├── src/main/java/com/crazyhouse/copypaster/
│   ├── CopyPasterMod.java              # Server init, PENDING, chat capture
│   ├── CopyPasterCommands.java         # Brigadier commands + paste execution
│   ├── CopyPasterPermissions.java      # Op bypass; LuckPerms nodes for non-ops
│   ├── paste/                          # PasteOptions, PasteGeometry, PasteCommandTree
│   ├── client/
│   │   ├── CopyPasterClientMod.java
│   │   ├── KeyHandler.java             # [ ] → CopyRegionPayload C2S
│   │   ├── CopyRegionClientHandler.java
│   │   ├── CopyPasterConfig.java       # config/copypaster.yml
│   │   ├── ModMenuIntegration.java     # Optional Mod Menu → Cloth Config
│   │   └── GhostRenderer.java          # Wireframe overlays
│   ├── net/
│   │   ├── GhostPayload.java           # S2C paste preview (world AABB)
│   │   └── CopyRegionPayload.java      # C2S copy region + S2C HUD sync
│   └── service/StructureStorageService.java
└── src/main/resources/
    ├── fabric.mod.json
    └── assets/copypaster/lang/en_us.json
```

---

## Architecture

**Server JVM** (`CopyPasterMod`):

- Structure storage under `<gameDir>/copypaster/structures/`
- `PENDING` — waiting for structure name in chat after region selected
- `UNDOS` — in-memory paste undo (lost on restart)
- Commands, validation, NBT save/load

**Client JVM** (`CopyPasterClientMod`):

- `KeyHandler` — `[` / `]` corners → `CopyRegionPayload` C2S
- `GhostRenderer` — selection + paste ghost (no custom shaders)
- `GhostPayload` receiver — paste overwrite preview (rotated AABB from server)
- `CopyPasterConfig` — highlight colour + preview volume from YAML; Mod Menu optional

**Web UI** (`com.crazyhouse.copypaster.web`, server-only):

- `CopyPasterServerConfig` — `config/copypaster-server.yml` (`limits.*`, `web.enabled`, port **8792**, bind, Bearer token)
- `StructureWebServer` — embedded `HttpServer`; static SPA from `copypaster/web/` in the JAR
- `StructureModelService` — export/import structures as JSON via vanilla `StructureTemplate` (includes `blockEntity` NBT per block)
- Build SPA: `web/` (Vite + React + Three.js) → `./gradlew buildWeb` or full `jar`

**Web SPA** (`web/src/`, bundled into the JAR):

- `StructureViewer` — instanced cubes, layer filter, Colors/Textures modes
- `blockTextures.ts` — asset URL resolution, cutout/transparent flags, mod fallbacks
- `*Texture.ts` — composed loaders (banner, bed, chest, bell, skull, turf, …)
- `displayBlocks.ts` — thin meshes for fences, banners, bells, lanterns, skulls
- `ViewerKeyboardControls` — WASD camera pan

**Copy flow:** `[` / `]` corners → `CopyRegionPayload` C2S → server `PENDING` → chat name (`cancel`, timeout from config) → `.nbt` + `.json`.

**Paste flow:** `/paste <name>` [modifiers] → optional cyan ghost (transformed bounds) + confirm → place + undo id.

**Limits:** `limits.maxVolume` and `limits.sessionTimeoutSeconds` in `copypaster-server.yml` (defaults 32 768 / 60 s).

---

## Commands

| Command | Description |
|---------|-------------|
| `/paste <name>` | Paste at player-relative position; warns on overwrite |
| `/paste <name> at <x> <y> <z>` | Paste with min corner at coordinates |
| `/paste <name> rotate <90\|180\|270>` | Rotate structure |
| `/paste <name> mirror <left_right\|front_back>` | Mirror structure |
| `/paste <name> noair` | Do not place air from the structure |
| `/paste <name> … confirm` | Paste after overwrite warning |
| `/pasteundo <id>` | Restore blocks from undo snapshot |
| `/copylist` | List saved structures |
| `/copyinfo <name>` | Size, dimension, offset, creator, date |
| `/copydelete <name>` | Remove `.nbt` and `.json` |
| `/copyweb` | Print web UI URL when `web.enabled` |

**Permissions:** operators always allowed; with LuckPerms, non-ops need `copypaster.*` (see README).

---

## Client controls

| Input | Action |
|-------|--------|
| `[` / `]` (if bound in Controls) | Set corners and send region to server |
| Chat name | Save structure after region is accepted |

Keybinds are **unbound by default** — assign under **Options → Controls → Miscellaneous**.

**Config:** `config/copypaster.yml` — `selectionHighlight`, `limits.maxVolume`, `hudAnchor`. Optional **Mod Menu** when Mod Menu + Cloth Config are installed.

---

## Data on server

```
<gameDir>/copypaster/structures/
├── <name>.nbt
└── <name>.json
```

---

## Roadmap

- **Web UI — banner patterns in 3D** — per-face or billboard geometry for composed banner NBT
- **Used-blocks report** — `/copyinfo` or `/copyblocks` shopping list of block types and counts

---

## RCON script

Canonical location: `copy_paster/scripts/gserver/minecraft_rcon.py`. If absent, copy from `legacy/copy_paster/scripts/gserver/` or sibling plugins per parent doc.
