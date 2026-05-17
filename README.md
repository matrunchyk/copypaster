> See [`CLAUDE.md`](CLAUDE.md) for build, deploy, source layout, and maintainer notes. See also [`../CLAUDE.md`](../CLAUDE.md) for shared conventions across the `minecraft` repo.

# Copy Paster

**Copy Paster** is a Fabric **client + server** mod that saves a box-shaped slice of the world to a file and pastes it back later. Paste position follows **where you stand** when you copy and when you paste.

**Minecraft 26.1.2** · **Fabric Loader 0.19.2** · **Fabric API** (required)

All commands require **operator** status on the server.

---

## Install

1. Run a **Fabric** server with **Fabric API** installed.
2. Put `copy_paster-<version>.jar` in the server **`mods/`** folder.
3. Put the **same JAR** in each player’s client **`mods/`** folder (required for interactive `/copy`, paste preview, and highlights).

**Server-only fallback:** coordinate `/copy` and all `/paste` commands still work; bare `/copy` will ask you to install the client mod or use coordinates.

**Optional:** [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config) for in-game highlight colour settings (otherwise edit `config/copypaster.yml`).

---

## Quick start

### Save a region (interactive)

1. Stand where you want the **anchor** for this copy.
2. Run **`/copy`** (no arguments).
3. **Attack** (left-click) the first corner block, then the second. A blue wireframe shows the box while you aim.
4. Press **Use** (right-click) to cancel and start over.
5. In **chat**, type a structure name (`a`–`z`, `0`–`9`, `_`, `-`, up to 64 chars). Type **`cancel`** to abort (60 second limit).

### Save a region (legacy coordinates)

```
/copy <x1> <y1> <z1> <x2> <y2> <z2>
```

Then type the structure name in chat as above.

### Paste a region

1. Stand in the **same relative spot** you used when copying.
2. Run `/paste <name>`.
3. If blocks would be overwritten, you’ll see a **cyan wireframe** (with client mod). Run `/paste <name> confirm` to proceed.
4. Note the **undo ID** in chat for `/pasteundo <id>`.

---

## Client controls

Rebind in **Options → Controls → Miscellaneous**. **`[`** and **`]`** are **not assigned by default.**

| Input | Action |
|-------|--------|
| `/copy` | Interactive selection |
| Attack block | Corners 1 and 2 during interactive `/copy` |
| Use | Cancel interactive selection |
| **`[`** (if bound) | Legacy corner 1 |
| **`]`** (if bound) | Legacy corner 2 + `/copy` with coordinates |

---

## Commands

| Command | What it does |
|---------|----------------|
| `/copy` | Interactive selection (client mod required) |
| `/copy <x1> <y1> <z1> <x2> <y2> <z2>` | Coordinate selection |
| `/paste <name>` | Paste at your anchor |
| `/paste <name> confirm` | Paste after overwrite warning |
| `/pasteundo <id>` | Undo one paste |
| `/copylist` | List saved structures |
| `/copyinfo <name>` | Metadata for a structure |
| `/copydelete <name>` | Delete a saved structure |

---

## Limits and tips

| Topic | Detail |
|-------|--------|
| **Max region size** | **32 768** blocks |
| **Undo** | In memory only; lost on server restart |
| **Storage** | Server: `copypaster/structures/` (`.nbt` + `.json`) |
| **Highlight colour** | `config/copypaster.yml` on the client |

See [`CHANGELOG.md`](CHANGELOG.md) for version history.

---

## For developers

Build, deployment, and architecture: **[`CLAUDE.md`](CLAUDE.md)**.
