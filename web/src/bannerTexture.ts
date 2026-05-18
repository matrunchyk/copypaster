import * as THREE from "three";

const MC_ASSET_VERSION = "1.21.4";
const BANNER_BASE = `https://assets.mcasset.cloud/${MC_ASSET_VERSION}/assets/minecraft/textures/entity/banner`;

/** Minecraft dye colors (banner pattern tints). */
const DYE_RGB: Record<string, string> = {
  white: "#f9fffe",
  orange: "#f9801d",
  magenta: "#c74ebd",
  light_blue: "#3ab3da",
  yellow: "#fed83d",
  lime: "#80c71f",
  pink: "#f38bba",
  gray: "#474f52",
  light_gray: "#9d9d97",
  cyan: "#169c9c",
  purple: "#8932b8",
  blue: "#3c44aa",
  brown: "#835432",
  green: "#5e7c16",
  red: "#b02e26",
  black: "#1e1b1b",
};

const DYE_INDEX: Record<number, string> = {
  0: "white",
  1: "orange",
  2: "magenta",
  3: "light_blue",
  4: "yellow",
  5: "lime",
  6: "pink",
  7: "gray",
  8: "light_gray",
  9: "cyan",
  10: "purple",
  11: "blue",
  12: "brown",
  13: "green",
  14: "red",
  15: "black",
};

const imageCache = new Map<string, Promise<HTMLImageElement | null>>();
const composedCache = new Map<string, Promise<THREE.Texture | null>>();

function patternName(raw: string): string {
  const id = raw.includes(":") ? raw.split(":").pop()! : raw;
  return id;
}

function resolveDyeColor(raw: unknown): string {
  if (typeof raw === "number") {
    return DYE_RGB[DYE_INDEX[raw] ?? "white"] ?? "#f9fffe";
  }
  if (typeof raw === "string") {
    if (raw.startsWith("#")) return raw;
    if (/^\d+$/.test(raw)) {
      return DYE_RGB[DYE_INDEX[parseInt(raw, 10)] ?? "white"] ?? "#f9fffe";
    }
    return DYE_RGB[raw] ?? "#f9fffe";
  }
  return "#f9fffe";
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

export function parseBannerPatterns(
  blockEntity: Record<string, unknown> | undefined
): { pattern: string; color: string }[] {
  if (!blockEntity) return [];
  const raw = blockEntity.Patterns ?? blockEntity.patterns;
  if (!Array.isArray(raw)) return [];
  const out: { pattern: string; color: string }[] = [];
  for (const entry of raw) {
    if (!entry || typeof entry !== "object") continue;
    const rec = entry as Record<string, unknown>;
    const pattern = rec.Pattern ?? rec.pattern;
    if (typeof pattern !== "string") continue;
    const color = resolveDyeColor(rec.Color ?? rec.color ?? "white");
    out.push({ pattern: patternName(pattern), color });
  }
  return out;
}

function baseBannerColor(blockId: string): string {
  const name = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  const m = name.match(/^(\w+)_wall_banner$/) || name.match(/^(\w+)_banner$/);
  if (m) return DYE_RGB[m[1]!] ?? "#f9fffe";
  return "#f9fffe";
}

async function drawTintedPattern(
  ctx: CanvasRenderingContext2D,
  pattern: string,
  color: string
): Promise<void> {
  const url = `${BANNER_BASE}/${pattern}.png`;
  const img = await loadImage(url);
  if (!img) return;

  const w = ctx.canvas.width;
  const h = ctx.canvas.height;
  const layer = document.createElement("canvas");
  layer.width = w;
  layer.height = h;
  const lctx = layer.getContext("2d")!;
  lctx.drawImage(img, 0, 0, w, h);
  lctx.globalCompositeOperation = "source-atop";
  lctx.fillStyle = color;
  lctx.fillRect(0, 0, w, h);
  ctx.globalCompositeOperation = "source-over";
  ctx.drawImage(layer, 0, 0);
}

export function isBannerBlock(blockId: string): boolean {
  const name = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  return name.endsWith("_banner") || name.endsWith("_wall_banner");
}

export function bannerTextureCacheKey(
  blockId: string,
  blockEntity: Record<string, unknown> | undefined
): string {
  return `${blockId}:${JSON.stringify(blockEntity ?? {})}`;
}

export function composeBannerTexture(
  blockId: string,
  blockEntity: Record<string, unknown> | undefined
): Promise<THREE.Texture | null> {
  const key = bannerTextureCacheKey(blockId, blockEntity);
  const cached = composedCache.get(key);
  if (cached) return cached;

  const promise = (async () => {
    const size = 64;
    const canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext("2d")!;
    const baseColor = baseBannerColor(blockId);

    const baseImg = await loadImage(`${BANNER_BASE}/base.png`);
    if (baseImg) {
      ctx.drawImage(baseImg, 0, 0, size, size);
      ctx.globalCompositeOperation = "multiply";
      ctx.fillStyle = baseColor;
      ctx.fillRect(0, 0, size, size);
      ctx.globalCompositeOperation = "source-over";
    } else {
      ctx.fillStyle = baseColor;
      ctx.fillRect(0, 0, size, size);
    }

    for (const layer of parseBannerPatterns(blockEntity)) {
      await drawTintedPattern(ctx, layer.pattern, layer.color);
    }

    const tex = new THREE.CanvasTexture(canvas);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestFilter;
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.needsUpdate = true;
    return tex;
  })();

  composedCache.set(key, promise);
  return promise;
}
