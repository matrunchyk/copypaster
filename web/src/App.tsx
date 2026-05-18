import { useCallback, useEffect, useMemo, useState } from "react";
import {
  deleteStructure,
  getToken,
  listStructures,
  loadStructure,
  saveEdits,
  setToken,
} from "./api";
import { BlockPicker } from "./BlockPicker";
import { StructureViewer, type RenderMode } from "./StructureViewer";
import type { BlockSearchResult, StructureModel, StructureSummary, VoxelEdit } from "./types";

function LoginScreen({ onLogin }: { onLogin: () => void }) {
  const [token, setTokenInput] = useState(getToken());

  return (
    <div className="login">
      <h1>Copy Paster — Web UI</h1>
      <p className="muted">
        Paste the Bearer token from <code>config/copypaster-server.yml</code> on the server.
      </p>
      <label>
        <span className="muted">Auth token</span>
        <input
          type="password"
          value={token}
          onChange={(e) => setTokenInput(e.target.value)}
          placeholder="Bearer token"
        />
      </label>
      <p style={{ marginTop: "1rem" }}>
        <button
          type="button"
          className="primary"
          onClick={() => {
            setToken(token);
            onLogin();
          }}
        >
          Connect
        </button>
      </p>
    </div>
  );
}

export default function App() {
  const [authed, setAuthed] = useState(!!getToken());
  const [structures, setStructures] = useState<StructureSummary[]>([]);
  const [selectedName, setSelectedName] = useState<string | null>(null);
  const [model, setModel] = useState<StructureModel | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [maxLayer, setMaxLayer] = useState(255);
  const [isometric, setIsometric] = useState(false);
  const [renderMode, setRenderMode] = useState<RenderMode>("color");
  const [paintMode, setPaintMode] = useState(false);
  const [selectedPos, setSelectedPos] = useState<{ x: number; y: number; z: number } | null>(null);
  const [paintBlock, setPaintBlock] = useState<BlockSearchResult | null>(null);
  const [paletteRemap, setPaletteRemap] = useState<Record<string, string>>({});
  const [voxelEdits, setVoxelEdits] = useState<VoxelEdit[]>([]);
  const [remapFrom, setRemapFrom] = useState("");
  const [remapTo, setRemapTo] = useState("");

  const showToast = (msg: string, isError = false) => {
    setToast(msg);
    setTimeout(() => setToast(null), 3500);
    if (isError) setError(msg);
  };

  const refreshList = useCallback(() => {
    listStructures()
      .then(setStructures)
      .catch((e) => showToast(e.message, true));
  }, []);

  useEffect(() => {
    if (authed) refreshList();
  }, [authed, refreshList]);

  const loadSelected = useCallback((name: string) => {
    setSelectedName(name);
    setPaletteRemap({});
    setVoxelEdits([]);
    setSelectedPos(null);
    loadStructure(name)
      .then((m) => {
        setModel(m);
        setMaxLayer(m.size[1] - 1);
      })
      .catch((e) => showToast(e.message, true));
  }, []);

  const displayPalette = useMemo(() => {
    if (!model) return [];
    return model.palette.map((p) => {
      const remapped = paletteRemap[p.id];
      if (!remapped) return p;
      return { ...p, id: remapped, displayName: remapped };
    });
  }, [model, paletteRemap]);

  const displayModel = useMemo(() => {
    if (!model) return null;
    const idToIndex = new Map(model.palette.map((p, i) => [p.id, i]));
    const blocks = model.blocks.map((b) => {
      const entry = model.palette[b.paletteIndex];
      const newId = paletteRemap[entry?.id ?? ""] ?? entry?.id;
      const newIndex = newId != null ? idToIndex.get(newId) ?? b.paletteIndex : b.paletteIndex;
      return { ...b, paletteIndex: newIndex };
    });
    for (const edit of voxelEdits) {
      const idx = model.palette.findIndex((p) => p.id === edit.id);
      const paletteIndex = idx >= 0 ? idx : 0;
      const existing = blocks.findIndex((b) => b.x === edit.x && b.y === edit.y && b.z === edit.z);
      if (edit.id === "minecraft:air") {
        if (existing >= 0) blocks.splice(existing, 1);
      } else if (existing >= 0) {
        blocks[existing] = { ...blocks[existing], paletteIndex };
      } else {
        blocks.push({ x: edit.x, y: edit.y, z: edit.z, paletteIndex });
      }
    }
    return { ...model, blocks };
  }, [model, paletteRemap, voxelEdits]);

  const handleSave = async () => {
    if (!selectedName) return;
    if (!confirm(`Save changes to "${selectedName}"?`)) return;
    try {
      await saveEdits(selectedName, { paletteRemap, voxelEdits });
      showToast("Saved");
      setPaletteRemap({});
      setVoxelEdits([]);
      loadSelected(selectedName);
    } catch (e) {
      showToast(e instanceof Error ? e.message : "Save failed", true);
    }
  };

  const handleDownload = async () => {
    if (!selectedName) return;
    try {
      const res = await fetch(`/api/structures/${encodeURIComponent(selectedName)}/download`, {
        headers: { Authorization: `Bearer ${getToken()}` },
      });
      if (!res.ok) throw new Error("Download failed");
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${selectedName}.nbt`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      showToast(e instanceof Error ? e.message : "Download failed", true);
    }
  };

  const applyPaint = () => {
    if (!selectedPos || !paintBlock) return;
    setVoxelEdits((prev) => {
      const filtered = prev.filter(
        (e) => !(e.x === selectedPos.x && e.y === selectedPos.y && e.z === selectedPos.z)
      );
      return [
        ...filtered,
        {
          x: selectedPos.x,
          y: selectedPos.y,
          z: selectedPos.z,
          id: paintBlock.id,
          properties: {},
        },
      ];
    });
    showToast(`Painted ${paintBlock.id} at ${selectedPos.x},${selectedPos.y},${selectedPos.z}`);
  };

  const eraseVoxel = () => {
    if (!selectedPos) return;
    setVoxelEdits((prev) => [
      ...prev.filter(
        (e) => !(e.x === selectedPos.x && e.y === selectedPos.y && e.z === selectedPos.z)
      ),
      { x: selectedPos.x, y: selectedPos.y, z: selectedPos.z, id: "minecraft:air", properties: {} },
    ]);
  };

  const applyPaletteRemap = () => {
    if (!remapFrom || !remapTo) return;
    setPaletteRemap((prev) => ({ ...prev, [remapFrom]: remapTo }));
    showToast(`Remap ${remapFrom} → ${remapTo}`);
  };

  const dirty =
    Object.keys(paletteRemap).length > 0 || voxelEdits.length > 0;

  if (!authed) {
    return <LoginScreen onLogin={() => setAuthed(!!getToken())} />;
  }

  return (
    <div className="app">
      <aside className="panel">
        <div className="panel-header">Structures</div>
        <div className="panel-body structure-list">
          <button type="button" onClick={refreshList}>
            Refresh
          </button>
          {structures.map((s) => (
            <button
              key={s.name}
              type="button"
              className={selectedName === s.name ? "active" : ""}
              onClick={() => loadSelected(s.name)}
            >
              <strong>{s.name}</strong>
              <br />
              <span className="muted">
                {s.sizeX}×{s.sizeY}×{s.sizeZ} — {s.creatorName}
              </span>
            </button>
          ))}
        </div>
      </aside>

      <main className="viewer-wrap">
        {displayModel ? (
          <>
            <div className="viewer-toolbar">
              <button type="button" onClick={() => setIsometric((v) => !v)}>
                {isometric ? "Orbit" : "Isometric"}
              </button>
              <button
                type="button"
                className={renderMode === "color" ? "active" : ""}
                onClick={() => setRenderMode("color")}
              >
                Colors
              </button>
              <button
                type="button"
                className={renderMode === "texture" ? "active" : ""}
                onClick={() => setRenderMode("texture")}
              >
                Textures
              </button>
              <button type="button" onClick={() => setPaintMode((v) => !v)}>
                {paintMode ? "Paint on" : "Paint off"}
              </button>
              <label>
                Layer ≤
                <input
                  type="range"
                  min={0}
                  max={displayModel.size[1] - 1}
                  value={maxLayer}
                  onChange={(e) => setMaxLayer(Number(e.target.value))}
                />
                {maxLayer}
              </label>
              {selectedName && (
                <>
                  <button type="button" className="primary" disabled={!dirty} onClick={handleSave}>
                    Save
                  </button>
                  <button type="button" onClick={handleDownload}>
                    Download .nbt
                  </button>
                </>
              )}
            </div>
            <StructureViewer
              model={displayModel}
              maxLayer={maxLayer}
              selectedPos={selectedPos}
              onPick={setSelectedPos}
              paintMode={paintMode}
              displayPalette={displayPalette}
              isometric={isometric}
              renderMode={renderMode}
            />
          </>
        ) : (
          <div style={{ padding: "2rem", color: "var(--muted)" }}>
            Select a structure from the list.
          </div>
        )}
        {selectedName && model && (
          <div className="status-bar">
            {model.name} — {model.meta.dimension} — {model.blocks.length} blocks
            {dirty && " — unsaved changes"}
            {" — WASD move · Q/E up/down · drag orbit · scroll zoom"}
          </div>
        )}
      </main>

      <aside className="panel right">
        <div className="panel-header">Editor</div>
        <div className="panel-body">
          <h3 style={{ margin: "0 0 0.5rem", fontSize: "0.95rem" }}>Palette remap</h3>
          <BlockPicker value={remapFrom} onSelect={(b) => setRemapFrom(b.id)} placeholder="From block" />
          <div style={{ height: 6 }} />
          <BlockPicker value={remapTo} onSelect={(b) => setRemapTo(b.id)} placeholder="To block" />
          <button type="button" style={{ marginTop: 8 }} onClick={applyPaletteRemap}>
            Apply remap
          </button>

          {model && (
            <>
              <h3 style={{ margin: "1rem 0 0.5rem", fontSize: "0.95rem" }}>Palette</h3>
              {model.blockCounts.map((c) => (
                <div key={c.id} className="palette-row">
                  <span
                    className="swatch"
                    style={{
                      background:
                        model.palette.find((p) => p.id === c.id)?.color ?? "#888",
                    }}
                  />
                  <span>
                    {paletteRemap[c.id] ? (
                      <>
                        <s>{c.id}</s> → {paletteRemap[c.id]}
                      </>
                    ) : (
                      c.id
                    )}
                  </span>
                  <span className="muted">{c.count}</span>
                </div>
              ))}
            </>
          )}

          <h3 style={{ margin: "1rem 0 0.5rem", fontSize: "0.95rem" }}>Voxel paint</h3>
          {selectedPos ? (
            <p className="muted">
              Selected: {selectedPos.x}, {selectedPos.y}, {selectedPos.z}
            </p>
          ) : (
            <p className="muted">Enable paint mode and click a block.</p>
          )}
          <BlockPicker
            value={paintBlock?.id ?? ""}
            onSelect={setPaintBlock}
            placeholder="Paint block"
          />
          <div style={{ display: "flex", gap: 6, marginTop: 8 }}>
            <button type="button" className="primary" disabled={!selectedPos || !paintBlock} onClick={applyPaint}>
              Paint
            </button>
            <button type="button" disabled={!selectedPos} onClick={eraseVoxel}>
              Erase
            </button>
          </div>

          {selectedName && (
            <button
              type="button"
              className="danger"
              style={{ marginTop: "1.5rem", width: "100%" }}
              onClick={async () => {
                if (!confirm(`Delete "${selectedName}"?`)) return;
                try {
                  await deleteStructure(selectedName);
                  setModel(null);
                  setSelectedName(null);
                  refreshList();
                  showToast("Deleted");
                } catch (e) {
                  showToast(e instanceof Error ? e.message : "Delete failed", true);
                }
              }}
            >
              Delete structure
            </button>
          )}
        </div>
      </aside>

      {toast && <div className={`toast ${error ? "error" : ""}`}>{toast}</div>}
    </div>
  );
}
