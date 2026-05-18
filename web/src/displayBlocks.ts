import { isBannerBlock } from "./bannerTexture";
import { isBellBlock } from "./bellTexture";
import { isSkullBlock } from "./skullTexture";

function blockName(blockId: string): string {
  const path = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  const state = path.indexOf("[");
  return state >= 0 ? path.slice(0, state) : path;
}

/** Chain/hanging lanterns (transparent sprite; not jack-o-lantern or sea lantern). */
export function isHangingLanternBlock(blockId: string): boolean {
  const name = blockName(blockId);
  return name === "lantern" || name === "soul_lantern";
}

/** Fences / gates: avoid full-cube plank texture (MC uses planks only on thin posts). */
export function isFenceBlock(blockId: string): boolean {
  const name = blockName(blockId);
  if (name === "bamboo_fence" || name === "bamboo_fence_gate") return false;
  return name.endsWith("_fence") || name.endsWith("_fence_gate");
}

export function isThinDisplayBlock(blockId: string): boolean {
  return (
    isFenceBlock(blockId) ||
    isBannerBlock(blockId) ||
    isBellBlock(blockId) ||
    isHangingLanternBlock(blockId) ||
    isSkullBlock(blockId)
  );
}

export interface BlockMeshMetrics {
  scaleX: number;
  scaleY: number;
  scaleZ: number;
  centerYOffset: number;
}

export function blockMeshMetrics(
  blockId: string,
  properties: Record<string, string>
): BlockMeshMetrics {
  const name = blockName(blockId);
  const def = { scaleX: 1, scaleY: 0.98, scaleZ: 1, centerYOffset: 0.5 };

  if (name === "snow") {
    const layers = Math.min(8, Math.max(1, parseInt(properties.layers ?? "1", 10) || 1));
    const scaleY = (layers / 8) * 0.98;
    return { scaleX: 1, scaleY, scaleZ: 1, centerYOffset: scaleY / 2 };
  }

  if (isFenceBlock(blockId)) {
    return { scaleX: 0.22, scaleY: 0.92, scaleZ: 0.22, centerYOffset: 0.46 };
  }

  if (isBannerBlock(blockId)) {
    return { scaleX: 0.12, scaleY: 0.88, scaleZ: 0.55, centerYOffset: 0.44 };
  }

  if (isBellBlock(blockId)) {
    return { scaleX: 0.55, scaleY: 0.7, scaleZ: 0.55, centerYOffset: 0.35 };
  }

  if (isHangingLanternBlock(blockId)) {
    return { scaleX: 0.42, scaleY: 0.62, scaleZ: 0.42, centerYOffset: 0.31 };
  }

  if (isSkullBlock(blockId)) {
    return { scaleX: 0.72, scaleY: 0.72, scaleZ: 0.72, centerYOffset: 0.36 };
  }

  return def;
}
