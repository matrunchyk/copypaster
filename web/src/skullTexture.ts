import * as THREE from "three";

const MC_ASSET_VERSION = "1.21.4";
const BASE = `https://assets.mcasset.cloud/${MC_ASSET_VERSION}/assets/minecraft/textures`;

const DEFAULT_PLAYER = "entity/player/wide/steve.png";

const SKULL_ENTITY_TEXTURE: Record<string, string> = {
  skeleton_skull: "entity/skeleton/skeleton.png",
  skeleton_wall_skull: "entity/skeleton/skeleton.png",
  wither_skeleton_skull: "entity/skeleton/wither_skeleton.png",
  wither_skeleton_wall_skull: "entity/skeleton/wither_skeleton.png",
  zombie_head: "entity/zombie/zombie.png",
  zombie_wall_head: "entity/zombie/zombie.png",
  creeper_head: "entity/creeper/creeper.png",
  creeper_wall_head: "entity/creeper/creeper.png",
  piglin_head: "entity/piglin/piglin.png",
  piglin_wall_head: "entity/piglin/piglin.png",
  zombified_piglin_head: "entity/piglin/zombified_piglin.png",
  zombified_piglin_wall_head: "entity/piglin/zombified_piglin.png",
  dragon_head: "entity/enderdragon/dragon.png",
  dragon_wall_head: "entity/enderdragon/dragon.png",
  wither_head: "entity/wither/wither.png",
  wither_wall_head: "entity/wither/wither.png",
};

const imageCache = new Map<string, Promise<HTMLImageElement | null>>();
const textureCache = new Map<string, Promise<THREE.Texture | null>>();

export function isSkullBlock(blockId: string): boolean {
  const name = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  return name.endsWith("_skull") || name.endsWith("_head");
}

export function isPlayerSkull(blockId: string): boolean {
  const name = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  return name === "player_head" || name === "player_wall_head";
}

function blockName(blockId: string): string {
  return blockId.includes(":") ? blockId.split(":")[1]! : blockId;
}

function asRecord(v: unknown): Record<string, unknown> | undefined {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : undefined;
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

/** Crop the head region from a player skin or mob texture for cube display. */
function headCropRect(img: HTMLImageElement): { x: number; y: number; w: number; h: number } {
  const { width: iw, height: ih } = img;
  if (iw >= 64 && ih >= 64) return { x: 8, y: 8, w: 8, h: 8 };
  if (iw >= 64 && ih >= 32) return { x: 8, y: 8, w: 8, h: 8 };
  if (iw >= 32 && ih >= 16) return { x: 0, y: 0, w: Math.min(32, iw), h: Math.min(16, ih) };
  return { x: 0, y: 0, w: iw, h: ih };
}

async function composeHeadTexture(url: string): Promise<THREE.Texture | null> {
  const img = await loadImage(url);
  if (!img) return null;

  const { x, y, w, h } = headCropRect(img);
  const size = 64;
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext("2d")!;
  ctx.clearRect(0, 0, size, size);
  ctx.drawImage(img, x, y, w, h, 0, 0, size, size);

  const tex = new THREE.CanvasTexture(canvas);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = THREE.ClampToEdgeWrapping;
  tex.wrapT = THREE.ClampToEdgeWrapping;
  tex.needsUpdate = true;
  return tex;
}

function skinUrlFromTexturesValue(value: string): string | null {
  try {
    const json = JSON.parse(atob(value)) as { textures?: { SKIN?: { url?: string } } };
    const url = json.textures?.SKIN?.url;
    if (!url) return null;
    return url.replace(/^http:\/\//i, "https://");
  } catch {
    return null;
  }
}

function embeddedPlayerSkinUrl(blockEntity: Record<string, unknown> | undefined): string | null {
  if (!blockEntity) return null;
  const owner = asRecord(blockEntity.SkullOwner ?? blockEntity.skull_owner);
  if (!owner) return null;
  const props = asRecord(owner.Properties ?? owner.properties);
  const textures = props?.textures;
  if (!Array.isArray(textures) || textures.length === 0) return null;
  const first = asRecord(textures[0]);
  const value = first?.Value ?? first?.value;
  if (typeof value !== "string") return null;
  return skinUrlFromTexturesValue(value);
}

async function fetchPlayerSkinUrl(
  blockEntity: Record<string, unknown> | undefined
): Promise<string | null> {
  const embedded = embeddedPlayerSkinUrl(blockEntity);
  if (embedded) return embedded;

  const owner = asRecord(blockEntity?.SkullOwner ?? blockEntity?.skull_owner);
  if (!owner) return null;

  let uuid =
    (typeof owner.Id === "string" && owner.Id) ||
    (typeof owner.id === "string" && owner.id) ||
    "";
  if (uuid && !uuid.includes("-") && uuid.length === 32) {
    uuid = `${uuid.slice(0, 8)}-${uuid.slice(8, 12)}-${uuid.slice(12, 16)}-${uuid.slice(16, 20)}-${uuid.slice(20)}`;
  }

  const name =
    (typeof owner.Name === "string" && owner.Name) ||
    (typeof owner.name === "string" && owner.name) ||
    "";

  const id = uuid || name;
  if (!id) return null;

  try {
    const res = await fetch(
      `https://sessionserver.mojang.com/session/minecraft/profile/${encodeURIComponent(id)}?unsigned=false`
    );
    if (!res.ok) return null;
    const data = (await res.json()) as {
      properties?: { name: string; value: string }[];
    };
    const texProp = data.properties?.find((p) => p.name === "textures");
    if (!texProp?.value) return null;
    return skinUrlFromTexturesValue(texProp.value);
  } catch {
    return null;
  }
}

export function skullTextureCacheKey(
  blockId: string,
  blockEntity: Record<string, unknown> | undefined
): string {
  if (isPlayerSkull(blockId)) {
    return `${blockId}:${JSON.stringify(blockEntity?.SkullOwner ?? blockEntity?.skull_owner ?? {})}`;
  }
  return blockId;
}

export function loadSkullTexture(
  blockId: string,
  blockEntity: Record<string, unknown> | undefined
): Promise<THREE.Texture | null> {
  const key = skullTextureCacheKey(blockId, blockEntity);
  const cached = textureCache.get(key);
  if (cached) return cached;

  const promise = (async () => {
    if (isPlayerSkull(blockId)) {
      const skin = await fetchPlayerSkinUrl(blockEntity);
      if (skin) {
        const tex = await composeHeadTexture(skin);
        if (tex) return tex;
      }
      return composeHeadTexture(`${BASE}/${DEFAULT_PLAYER}`);
    }
    const path = SKULL_ENTITY_TEXTURE[blockName(blockId)];
    if (!path) return null;
    return composeHeadTexture(`${BASE}/${path}`);
  })();

  textureCache.set(key, promise);
  return promise;
}
