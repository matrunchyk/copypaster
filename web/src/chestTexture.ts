const MC_ASSET_VERSION = "1.21.4";
const BASE = `https://assets.mcasset.cloud/${MC_ASSET_VERSION}/assets/minecraft/textures`;

export function isChestBlock(blockId: string): boolean {
  const name = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  return name === "chest" || name === "trapped_chest" || name === "ender_chest";
}

/** Entity chest atlas path from block id + `type` property (single / left / right). */
export function chestTexturePath(
  blockId: string,
  properties: Record<string, string> = {}
): string {
  const name = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  const part = properties.type ?? "single";

  if (name === "ender_chest") {
    return "entity/chest/ender.png";
  }

  const variant = name === "trapped_chest" ? "trapped" : "normal";
  if (part === "left") return `entity/chest/${variant}_left.png`;
  if (part === "right") return `entity/chest/${variant}_right.png`;
  return `entity/chest/${variant}.png`;
}

export function chestTextureCacheKey(
  blockId: string,
  properties: Record<string, string> = {}
): string {
  return `${blockId}:${properties.type ?? "single"}`;
}

export function chestTextureUrl(
  blockId: string,
  properties: Record<string, string> = {}
): string {
  return `${BASE}/${chestTexturePath(blockId, properties)}`;
}
