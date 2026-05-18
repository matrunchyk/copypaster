import * as THREE from "three";

const MC_ASSET_VERSION = "1.21.4";
const BASE = `https://assets.mcasset.cloud/${MC_ASSET_VERSION}/assets/minecraft/textures`;

const imageCache = new Map<string, Promise<HTMLImageElement | null>>();
const textureCache = new Map<string, Promise<THREE.Texture | null>>();

export function isTurfBlock(blockId: string): boolean {
  const name = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  const state = name.indexOf("[");
  const path = state >= 0 ? name.slice(0, state) : name;
  return path === "grass_block" || path === "podzol" || path === "mycelium";
}

function blockName(blockId: string): string {
  const path = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  const state = path.indexOf("[");
  return state >= 0 ? path.slice(0, state) : path;
}

function loadImage(url: string): Promise<HTMLImageElement | null> {
  const cached = imageCache.get(url);
  if (cached) return cached;
  const promise = new Promise<HTMLImageElement | null>((resolve) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = url;
  });
  imageCache.set(url, promise);
  return promise;
}

function turfPaths(
  name: string,
  properties: Record<string, string>
): { side: string; top: string } {
  const snowy = properties.snowy === "true";
  if (name === "podzol") {
    return { side: "block/podzol_side.png", top: "block/podzol_top.png" };
  }
  if (name === "mycelium") {
    return { side: "block/mycelium_side.png", top: "block/mycelium_top.png" };
  }
  if (snowy) {
    return { side: "block/grass_block_snow.png", top: "block/grass_block_snow.png" };
  }
  return { side: "block/grass_block_side.png", top: "block/grass_block_top.png" };
}

export function turfTextureCacheKey(
  blockId: string,
  properties: Record<string, string>,
  tintColor: string
): string {
  return `${blockId}:${properties.snowy ?? ""}:${tintColor}`;
}

/**
 * Opaque turf texture for cube voxels: grass side (not overlay) + optional biome tint.
 * Avoids grass_block_top on all six faces and greyscale overlay misuse.
 */
export function composeTurfTexture(
  blockId: string,
  properties: Record<string, string>,
  tintColor: string
): Promise<THREE.Texture | null> {
  const key = turfTextureCacheKey(blockId, properties, tintColor);
  const cached = textureCache.get(key);
  if (cached) return cached;

  const promise = (async () => {
    const name = blockName(blockId);
    const { side, top } = turfPaths(name, properties);
    const sideImg = await loadImage(`${BASE}/${side}`);
    const topImg = await loadImage(`${BASE}/${top}`);

    const size = 64;
    const canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext("2d")!;

    if (sideImg) {
      ctx.drawImage(sideImg, 0, 0, size, size);
    } else if (topImg) {
      ctx.drawImage(topImg, 0, 0, size, size);
    } else {
      return null;
    }

    if (tintColor && tintColor.startsWith("#") && name === "grass_block") {
      ctx.globalCompositeOperation = "multiply";
      ctx.fillStyle = tintColor;
      ctx.fillRect(0, 0, size, size);
      ctx.globalCompositeOperation = "source-over";
    }

    const tex = new THREE.CanvasTexture(canvas);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestFilter;
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.needsUpdate = true;
    return tex;
  })();

  textureCache.set(key, promise);
  return promise;
}
