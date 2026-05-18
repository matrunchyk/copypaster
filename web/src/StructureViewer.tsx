import { Canvas, useThree } from "@react-three/fiber";
import { OrbitControls } from "@react-three/drei";
import { useLayoutEffect, useMemo, useRef } from "react";
import * as THREE from "three";
import { isBannerBlock } from "./bannerTexture";
import { isTurfBlock, turfTextureCacheKey } from "./turfBlockTexture";
import { BlockMaterial, type RenderMode } from "./BlockMaterial";
import { blockMeshMetrics } from "./displayBlocks";
import { chestTextureCacheKey, isChestBlock } from "./chestTexture";
import { isPlayerSkull, isSkullBlock, skullTextureCacheKey } from "./skullTexture";
import type { BlockPlacement, PaletteEntry, StructureModel } from "./types";
import { ViewerKeyboardControls } from "./ViewerKeyboardControls";

interface Props {
  model: StructureModel;
  maxLayer: number;
  selectedPos: { x: number; y: number; z: number } | null;
  onPick: (pos: { x: number; y: number; z: number } | null) => void;
  paintMode: boolean;
  displayPalette: PaletteEntry[];
  renderMode: RenderMode;
}

/** Variant blocks need more than palette index for instancing. */
function instanceGroupKey(b: BlockPlacement, entry: PaletteEntry | undefined): string {
  const id = entry?.id ?? "";
  const props = entry?.properties ?? {};
  if (isBannerBlock(id) && b.blockEntity) {
    return `${b.paletteIndex}:${JSON.stringify(b.blockEntity)}`;
  }
  if (isChestBlock(id)) {
    return `${b.paletteIndex}:${chestTextureCacheKey(id, props)}`;
  }
  if (isTurfBlock(id)) {
    const color = entry?.color ?? "#888888";
    return `${b.paletteIndex}:${turfTextureCacheKey(id, props, color)}`;
  }
  if (isSkullBlock(id) && (isPlayerSkull(id) || b.blockEntity)) {
    return `${b.paletteIndex}:${skullTextureCacheKey(id, b.blockEntity)}`;
  }
  return String(b.paletteIndex);
}

function applyInstanceMatrices(
  mesh: THREE.InstancedMesh,
  blocks: BlockPlacement[],
  offset: THREE.Vector3,
  entry: PaletteEntry | undefined
) {
  const blockId = entry?.id ?? "minecraft:stone";
  const props = entry?.properties ?? {};
  const { scaleX, scaleY, scaleZ, centerYOffset } = blockMeshMetrics(blockId, props);
  const dummy = new THREE.Object3D();
  blocks.forEach((b, i) => {
    dummy.position.set(
      b.x + 0.5 + offset.x,
      b.y + centerYOffset,
      b.z + 0.5 + offset.z
    );
    dummy.scale.set(scaleX, scaleY, scaleZ);
    dummy.updateMatrix();
    mesh.setMatrixAt(i, dummy.matrix);
  });
  mesh.count = blocks.length;
  mesh.instanceMatrix.needsUpdate = true;
}

function InstancedBlocks({
  model,
  maxLayer,
  displayPalette,
  onPick,
  paintMode,
  renderMode,
}: {
  model: StructureModel;
  maxLayer: number;
  displayPalette: PaletteEntry[];
  onPick: (pos: { x: number; y: number; z: number } | null) => void;
  paintMode: boolean;
  renderMode: RenderMode;
}) {
  const meshRefs = useRef<Record<string, THREE.InstancedMesh | null>>({});
  const [sx, , sz] = model.size;
  const offset = useMemo(() => new THREE.Vector3(-sx / 2, 0, -sz / 2), [sx, sz]);

  const groups = useMemo(() => {
    const byKey = new Map<string, BlockPlacement[]>();
    for (const b of model.blocks) {
      if (b.y > maxLayer) continue;
      const entry = displayPalette[b.paletteIndex];
      const key = instanceGroupKey(b, entry);
      const list = byKey.get(key) ?? [];
      list.push(b);
      byKey.set(key, list);
    }
    return byKey;
  }, [model.blocks, maxLayer, displayPalette]);

  useLayoutEffect(() => {
    groups.forEach((blocks, groupKey) => {
      const mesh = meshRefs.current[groupKey];
      const entry = displayPalette[blocks[0]?.paletteIndex ?? 0];
      if (mesh && blocks.length > 0) {
        applyInstanceMatrices(mesh, blocks, offset, entry);
      } else if (mesh) {
        mesh.count = 0;
        mesh.instanceMatrix.needsUpdate = true;
      }
    });
  }, [groups, offset, displayPalette]);

  const geometry = useMemo(() => new THREE.BoxGeometry(1, 1, 1), []);

  return (
    <group>
      {Array.from(groups.entries()).map(([groupKey, blocks]) => {
        if (blocks.length === 0) return null;
        const entry = displayPalette[blocks[0].paletteIndex];
        const color = entry?.color ?? "#888888";
        const blockId = entry?.id ?? "minecraft:stone";
        const blockEntity = blocks[0].blockEntity;
        const properties = entry?.properties ?? {};
        return (
          <instancedMesh
            key={`${renderMode}-${groupKey}-${blockId}`}
            ref={(el) => {
              meshRefs.current[groupKey] = el;
              if (el && blocks.length > 0) {
                applyInstanceMatrices(el, blocks, offset, entry);
              }
            }}
            args={[geometry, undefined, blocks.length]}
            frustumCulled={false}
            onClick={(e) => {
              if (!paintMode) return;
              e.stopPropagation();
              const id = e.instanceId;
              if (id == null) return;
              const b = blocks[id];
              if (b) onPick({ x: b.x, y: b.y, z: b.z });
            }}
          >
            <BlockMaterial
              mode={renderMode}
              blockId={blockId}
              fallbackColor={color}
              blockEntity={blockEntity}
              properties={properties}
            />
          </instancedMesh>
        );
      })}
      <mesh
        position={[offset.x + sx / 2 - 0.5, -0.01, offset.z + sz / 2 - 0.5]}
        rotation={[-Math.PI / 2, 0, 0]}
        onClick={() => paintMode && onPick(null)}
      >
        <planeGeometry args={[sx, sz]} />
        <meshBasicMaterial visible={false} />
      </mesh>
    </group>
  );
}

function CameraIso() {
  const { camera } = useThree();
  useLayoutEffect(() => {
    const dist = 40;
    camera.position.set(dist * 0.9, dist * 0.7, dist * 0.9);
    camera.lookAt(0, 8, 0);
    camera.updateProjectionMatrix();
  }, [camera]);
  return null;
}

export function StructureViewer({
  model,
  maxLayer,
  selectedPos,
  onPick,
  paintMode,
  displayPalette,
  isometric,
  renderMode,
}: Props & { isometric: boolean }) {
  const [sx, sy, sz] = model.size;
  const centerY = sy / 2;

  return (
    <Canvas
      tabIndex={0}
      camera={{ position: [20, 16, 20], fov: 50, near: 0.1, far: 500 }}
      style={{ width: "100%", height: "100%", background: "#0a0c10", outline: "none" }}
      onPointerDown={(e) => (e.target as HTMLElement)?.focus?.()}
      onPointerMissed={() => paintMode && onPick(null)}
    >
      <ambientLight intensity={0.85} />
      <directionalLight position={[10, 20, 10]} intensity={0.95} />
      {isometric && <CameraIso />}
      <OrbitControls target={[0, centerY, 0]} makeDefault enableDamping />
      <ViewerKeyboardControls />
      <gridHelper args={[Math.max(sx, sz) + 4, Math.max(sx, sz) + 4, "#333", "#222"]} />
      <InstancedBlocks
        model={model}
        maxLayer={maxLayer}
        displayPalette={displayPalette}
        onPick={onPick}
        paintMode={paintMode}
        renderMode={renderMode}
      />
      {selectedPos && (
        <mesh position={[selectedPos.x + 0.5 - sx / 2, selectedPos.y + 0.5, selectedPos.z + 0.5 - sz / 2]}>
          <boxGeometry args={[1.02, 1.02, 1.02]} />
          <meshBasicMaterial color="#ffff00" wireframe />
        </mesh>
      )}
    </Canvas>
  );
}

export type { RenderMode };
