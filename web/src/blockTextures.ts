import * as THREE from "three";
import { isBannerBlock } from "./bannerTexture";
import { isBedBlock, loadBedTexture } from "./bedTexture";
import { isBellBlock, loadBellTexture } from "./bellTexture";
import { chestTextureCacheKey, chestTextureUrl, isChestBlock } from "./chestTexture";
import { isFenceBlock, isHangingLanternBlock } from "./displayBlocks";
import { isSkullBlock, loadSkullTexture, skullTextureCacheKey } from "./skullTexture";
import { composeTurfTexture, isTurfBlock, turfTextureCacheKey } from "./turfBlockTexture";

export { blockMeshMetrics } from "./displayBlocks";

/** Closest public asset pack to server MC 26.1.2 */
const MC_ASSET_VERSION = "1.21.4";

const BASE = `https://assets.mcasset.cloud/${MC_ASSET_VERSION}/assets/minecraft/textures`;

const textureCache = new Map<string, Promise<THREE.Texture | null>>();
const urlResolveCache = new Map<string, Promise<string | null>>();

/**
 * Blocks that never use a plain block/{name}.png in modern packs.
 * Values are tried first (before generic suffix fallbacks).
 */
const TEXTURE_OVERRIDES: Record<string, string[]> = {
  dirt_path: ["block/dirt_path_top.png"],
  farmland: ["block/farmland.png", "block/farmland_moist.png"],
  water: ["block/water_still.png"],
  lava: ["block/lava_still.png"],
  short_grass: ["block/short_grass.png", "block/grass.png"],
  tall_grass: ["block/tall_grass_bottom.png"],
  crafting_table: ["block/crafting_table_top.png", "block/crafting_table_front.png"],
  furnace: ["block/furnace_front.png", "block/furnace_top.png", "block/furnace_side.png"],
  blast_furnace: ["block/blast_furnace_front.png", "block/blast_furnace_top.png"],
  smoker: ["block/smoker_front.png", "block/smoker_top.png"],
  barrel: ["block/barrel_top.png", "block/barrel_side.png"],
  dispenser: ["block/dispenser_front.png", "block/dispenser_front_vertical.png"],
  dropper: ["block/dropper_front.png"],
  observer: ["block/observer_front.png", "block/observer_back.png"],
  piston: ["block/piston_top.png", "block/piston_side.png"],
  sticky_piston: ["block/piston_top.png", "block/piston_side.png"],
  beehive: ["block/beehive_end.png", "block/beehive_side.png"],
  bookshelf: ["block/bookshelf.png"],
  chiseled_bookshelf: ["block/chiseled_bookshelf_top.png"],
  jukebox: ["block/jukebox_top.png", "block/jukebox_side.png"],
  note_block: ["block/note_block.png"],
  enchanting_table: ["block/enchanting_table_top.png", "block/enchanting_table_side.png"],
  anvil: ["block/anvil_top.png"],
  chipped_anvil: ["block/chipped_anvil_top.png"],
  damaged_anvil: ["block/damaged_anvil_top.png"],
  cauldron: ["block/cauldron_top.png", "block/cauldron_side.png"],
  water_cauldron: ["block/cauldron_top.png", "block/cauldron_side.png"],
  lava_cauldron: ["block/cauldron_top.png", "block/cauldron_side.png"],
  powder_snow_cauldron: ["block/cauldron_top.png", "block/cauldron_side.png"],
  pointed_dripstone: ["block/pointed_dripstone_down_base.png", "block/pointed_dripstone_up_base.png"],
  red_tulip: ["block/red_tulip.png"],
  orange_tulip: ["block/orange_tulip.png"],
  white_tulip: ["block/white_tulip.png"],
  pink_tulip: ["block/pink_tulip.png"],
  grindstone: ["block/grindstone_round.png", "block/grindstone_side.png"],
  lectern: ["block/lectern_top.png", "block/lectern_base.png"],
  campfire: ["block/campfire_log.png", "block/campfire_fire.png"],
  soul_campfire: ["block/soul_campfire_log.png", "block/soul_campfire_fire.png"],
  hay_block: ["block/hay_block_top.png", "block/hay_block_side.png"],
  bone_block: ["block/bone_block_top.png", "block/bone_block_side.png"],
  melon: ["block/melon_top.png", "block/melon_side.png"],
  pumpkin: ["block/pumpkin_top.png", "block/pumpkin_side.png"],
  carved_pumpkin: ["block/carved_pumpkin_top.png", "block/pumpkin_side.png"],
  jack_o_lantern: ["block/jack_o_lantern.png"],
  sea_lantern: ["block/sea_lantern.png"],
  cactus: ["block/cactus_side.png", "block/cactus_top.png"],
  bamboo: ["block/bamboo_stalk.png", "block/bamboo_singleleaf.png"],
  sugar_cane: ["block/sugar_cane.png"],
  kelp: ["block/kelp.png"],
  kelp_plant: ["block/kelp_plant.png"],
  seagrass: ["block/seagrass.png"],
  torch: ["block/torch.png"],
  wall_torch: ["block/torch.png"],
  redstone_torch: ["block/redstone_torch.png", "block/redstone_torch_off.png"],
  soul_torch: ["block/soul_torch.png"],
  lantern: ["block/lantern.png"],
  soul_lantern: ["block/soul_lantern.png"],
  glass: ["block/glass.png"],
  tinted_glass: ["block/tinted_glass.png"],
  ice: ["block/ice.png"],
  packed_ice: ["block/packed_ice.png"],
  blue_ice: ["block/blue_ice.png"],
  snow: ["block/snow.png"],
  snow_block: ["block/snow.png"],
  oak_leaves: ["block/oak_leaves.png"],
  spruce_leaves: ["block/spruce_leaves.png"],
  birch_leaves: ["block/birch_leaves.png"],
  jungle_leaves: ["block/jungle_leaves.png"],
  acacia_leaves: ["block/acacia_leaves.png"],
  dark_oak_leaves: ["block/dark_oak_leaves.png"],
  mangrove_leaves: ["block/mangrove_leaves.png"],
  cherry_leaves: ["block/cherry_leaves.png"],
  azalea_leaves: ["block/azalea_leaves.png"],
  flowering_azalea_leaves: ["block/flowering_azalea_leaves.png"],
  oak_log: ["block/oak_log_top.png", "block/oak_log.png"],
  spruce_log: ["block/spruce_log_top.png", "block/spruce_log.png"],
  birch_log: ["block/birch_log_top.png", "block/birch_log.png"],
  jungle_log: ["block/jungle_log_top.png", "block/jungle_log.png"],
  acacia_log: ["block/acacia_log_top.png", "block/acacia_log.png"],
  dark_oak_log: ["block/dark_oak_log_top.png", "block/dark_oak_log.png"],
  mangrove_log: ["block/mangrove_log_top.png", "block/mangrove_log.png"],
  cherry_log: ["block/cherry_log_top.png", "block/cherry_log.png"],
  stripped_oak_log: ["block/stripped_oak_log_top.png", "block/stripped_oak_log.png"],
  stripped_spruce_log: ["block/stripped_spruce_log_top.png", "block/stripped_spruce_log.png"],
  stripped_birch_log: ["block/stripped_birch_log_top.png", "block/stripped_birch_log.png"],
  stripped_jungle_log: ["block/stripped_jungle_log_top.png", "block/stripped_jungle_log.png"],
  stripped_acacia_log: ["block/stripped_acacia_log_top.png", "block/stripped_acacia_log.png"],
  stripped_dark_oak_log: ["block/stripped_dark_oak_log_top.png", "block/stripped_dark_oak_log.png"],
  stripped_mangrove_log: ["block/stripped_mangrove_log_top.png", "block/stripped_mangrove_log.png"],
  stripped_cherry_log: ["block/stripped_cherry_log_top.png", "block/stripped_cherry_log.png"],
  stripped_pale_oak_log: ["block/stripped_pale_oak_log_top.png", "block/stripped_pale_oak_log.png"],
  bell: ["entity/bell/bell_body.png"],
  oak_door: ["block/oak_door_bottom.png", "block/oak_door_top.png"],
  iron_door: ["block/iron_door_bottom.png", "block/iron_door_top.png"],
  copper_door: ["block/copper_door_bottom.png", "block/copper_door_top.png"],
  exposed_copper_door: ["block/exposed_copper_door_bottom.png", "block/exposed_copper_door_top.png"],
  weathered_copper_door: ["block/weathered_copper_door_bottom.png", "block/weathered_copper_door_top.png"],
  oxidized_copper_door: ["block/oxidized_copper_door_bottom.png", "block/oxidized_copper_door_top.png"],
  ladder: ["block/ladder.png"],
  scaffolding: ["block/scaffolding_top.png", "block/scaffolding_side.png"],
  composter: ["block/composter_top.png", "block/composter_side.png"],
  cartography_table: ["block/cartography_table_top.png", "block/cartography_table_side3.png"],
  fletching_table: ["block/fletching_table_top.png", "block/fletching_table_front.png"],
  smithing_table: ["block/smithing_table_top.png", "block/smithing_table_front.png"],
  stonecutter: ["block/stonecutter_top.png", "block/stonecutter_side.png"],
  loom: ["block/loom_top.png", "block/loom_side.png"],
  sunflower: ["block/sunflower_front.png", "block/sunflower_back.png"],
  lilac: ["block/lilac_bottom.png"],
  rose_bush: ["block/rose_bush_bottom.png"],
  peony: ["block/peony_bottom.png"],
  wheat: ["block/wheat_stage7.png"],
  carrots: ["block/carrots_stage3.png"],
  potatoes: ["block/potatoes_stage3.png"],
  beetroots: ["block/beetroots_stage3.png"],
  nether_wart: ["block/nether_wart_stage2.png"],
  sweet_berry_bush: ["block/sweet_berry_bush_stage3.png"],
  cocoa: ["block/cocoa_stage2.png"],
  firefly_bush: ["block/short_grass.png", "block/oak_leaves.png"],
  stone_button: ["block/stone.png"],
  polished_blackstone_button: ["block/polished_blackstone.png"],
  stone_pressure_plate: ["block/stone.png"],
  polished_blackstone_pressure_plate: ["block/polished_blackstone.png"],
  light_weighted_pressure_plate: ["block/gold_block.png"],
  heavy_weighted_pressure_plate: ["block/iron_block.png"],
  cobblestone_wall: ["block/cobblestone.png"],
  mossy_cobblestone_wall: ["block/mossy_cobblestone.png"],
  stone_brick_wall: ["block/stone_bricks.png"],
  mossy_stone_brick_wall: ["block/mossy_stone_bricks.png"],
  diorite_wall: ["block/diorite.png"],
  andesite_wall: ["block/andesite.png"],
  granite_wall: ["block/granite.png"],
  brick_wall: ["block/bricks.png"],
  nether_brick_wall: ["block/nether_bricks.png"],
  red_nether_brick_wall: ["block/red_nether_bricks.png"],
  end_stone_brick_wall: ["block/end_stone_bricks.png"],
  blackstone_wall: ["block/blackstone.png"],
  polished_blackstone_wall: ["block/polished_blackstone.png"],
  polished_blackstone_brick_wall: ["block/polished_blackstone_bricks.png"],
  deepslate_brick_wall: ["block/deepslate_bricks.png"],
  deepslate_tile_wall: ["block/deepslate_tiles.png"],
  mud_brick_wall: ["block/mud_bricks.png"],
  sandstone_wall: ["block/sandstone.png"],
  red_sandstone_wall: ["block/red_sandstone.png"],
  prismarine_wall: ["block/prismarine.png"],
  prismarine_brick_wall: ["block/prismarine_bricks.png"],
  glass_pane: ["block/glass.png"],
  tinted_glass_pane: ["block/tinted_glass.png"],
  iron_bars: ["block/iron_bars.png"],
  chain: ["block/chain.png"],
  vine: ["block/vine.png"],
  cobweb: ["block/cobweb.png"],
  glow_lichen: ["block/glow_lichen.png"],
  lily_pad: ["block/lily_pad.png"],
  redstone_wire: ["block/redstone_dust_dot.png"],
  repeater: ["block/repeater.png"],
  comparator: ["block/comparator.png"],
  rail: ["block/rail.png"],
  powered_rail: ["block/powered_rail.png"],
  detector_rail: ["block/detector_rail.png"],
  activator_rail: ["block/activator_rail.png"],
  melon_stem: ["block/melon_stem.png", "block/attached_melon_stem.png"],
  pumpkin_stem: ["block/pumpkin_stem.png", "block/attached_pumpkin_stem.png"],
  fern: ["block/fern.png"],
  large_fern: ["block/large_fern_bottom.png"],
  dead_bush: ["block/dead_bush.png"],
  bamboo_mosaic: ["block/bamboo_mosaic.png"],
  resin_bricks: ["block/resin_bricks.png"],
  bush: ["block/short_grass.png"],
  wildflowers: ["block/short_grass.png"],
  leaf_litter: ["block/oak_leaves.png"],
  tall_dry_grass: ["block/short_grass.png"],
  short_dry_grass: ["block/short_grass.png"],
  creaking_heart: ["block/pale_oak_log.png", "block/pale_oak_log_top.png"],
};

/** Longest first so `dark_oak` wins over `oak`. */
const WOOD_TYPES = [
  "dark_oak",
  "pale_oak",
  "mangrove",
  "spruce",
  "birch",
  "jungle",
  "acacia",
  "cherry",
  "bamboo",
  "crimson",
  "warped",
  "oak",
] as const;

function parseBlockId(blockId: string): { namespace: string; path: string } {
  const sep = blockId.indexOf(":");
  if (sep >= 0) {
    return { namespace: blockId.slice(0, sep), path: blockId.slice(sep + 1) };
  }
  return { namespace: "minecraft", path: blockId };
}

function blockName(blockId: string): string {
  let path = parseBlockId(blockId).path;
  const state = path.indexOf("[");
  if (state >= 0) path = path.slice(0, state);
  return path;
}

function isVanillaBlock(blockId: string): boolean {
  return parseBlockId(blockId).namespace === "minecraft";
}

/**
 * Mod blocks (e.g. Macaw's `mcwfences:cherry_curved_gate`) are not on mcasset.cloud.
 * Map common name patterns to a vanilla block id for an approximate texture.
 */
function vanillaFallbackBlockId(blockId: string): string | null {
  const name = blockName(blockId);
  for (const wood of WOOD_TYPES) {
    if (name.includes(wood)) {
      return `minecraft:${wood}_planks`;
    }
  }
  if (/(cobble|deepslate|andesite|diorite|granite|blackstone|tuff|calcite)/.test(name)) {
    return "minecraft:cobblestone";
  }
  if (name.includes("glass")) return "minecraft:glass";
  if (name.includes("iron")) return "minecraft:iron_block";
  if (name.includes("gold")) return "minecraft:gold_block";
  if (name.includes("copper")) return "minecraft:copper_block";
  if (name.includes("nether_brick")) return "minecraft:nether_bricks";
  if (name.includes("brick")) return "minecraft:bricks";
  if (name.includes("concrete")) {
    const m = name.match(
      /(white|orange|magenta|light_blue|yellow|lime|pink|gray|light_gray|cyan|purple|blue|brown|green|red|black)_concrete/
    );
    if (m) return `minecraft:${m[1]}_concrete`;
  }
  if (name.includes("wool") || name.includes("carpet")) {
    const m = name.match(
      /(white|orange|magenta|light_blue|yellow|lime|pink|gray|light_gray|cyan|purple|blue|brown|green|red|black)/
    );
    if (m) return `minecraft:${m[1]}_wool`;
  }
  return null;
}

function textureUrl(path: string): string {
  return `${BASE}/${path}`;
}

/** Stairs/slabs reuse a parent block texture (no block/oak_stairs.png etc.). */
const STAIR_SLAB_BASE: Record<string, string> = {
  stone_brick: "stone_bricks",
  mossy_stone_brick: "mossy_stone_bricks",
  nether_brick: "nether_bricks",
  red_nether_brick: "red_nether_bricks",
  end_stone_brick: "end_stone_bricks",
  polished_blackstone_brick: "polished_blackstone_bricks",
  deepslate_brick: "deepslate_bricks",
  deepslate_tile: "deepslate_tiles",
  mud_brick: "mud_bricks",
  prismarine_brick: "prismarine_bricks",
  quartz_brick: "quartz_block_top",
  purpur: "purpur_block",
  brick: "bricks",
  stone: "stone",
  cobblestone: "cobblestone",
  sand: "sand",
  red_sand: "red_sand",
  smooth_sandstone: "sandstone_top",
  smooth_red_sandstone: "red_sandstone_top",
  smooth_quartz: "quartz_block_top",
  quartz: "quartz_block_top",
  cut_sandstone: "cut_sandstone",
  cut_red_sandstone: "cut_red_sandstone",
  resin_brick: "resin_bricks",
  bamboo_mosaic: "bamboo_mosaic",
};

/**
 * Paths that exist in vanilla 1.21.x but are not block/{id}.png.
 * Returned paths are probed in order; omit invented suffixes like *_block.
 */
/** Wool colors shared by beds, carpets, and banners. */
const WOOL_COLORS = [
  "white",
  "orange",
  "magenta",
  "light_blue",
  "yellow",
  "lime",
  "pink",
  "gray",
  "light_gray",
  "cyan",
  "purple",
  "blue",
  "brown",
  "green",
  "red",
  "black",
] as const;

/** Sprites with transparency — need alphaTest in Three.js (else black cubes). */
const ALPHA_CUTOUT_BLOCKS = new Set([
  "short_grass",
  "tall_grass",
  "fern",
  "large_fern",
  "dead_bush",
  "seagrass",
  "kelp",
  "kelp_plant",
  "vine",
  "cobweb",
  "glow_lichen",
  "lily_pad",
  "sunflower",
  "lilac",
  "rose_bush",
  "peony",
  "red_tulip",
  "orange_tulip",
  "white_tulip",
  "pink_tulip",
  "dandelion",
  "poppy",
  "blue_orchid",
  "allium",
  "azure_bluet",
  "oxeye_daisy",
  "cornflower",
  "lily_of_the_valley",
  "torchflower",
  "pitcher_plant",
  "pink_petals",
  "wildflowers",
  "bush",
  "firefly_bush",
  "leaf_litter",
  "short_dry_grass",
  "tall_dry_grass",
  "sweet_berry_bush",
  "wheat",
  "carrots",
  "potatoes",
  "beetroots",
  "nether_wart",
  "cocoa",
  "oak_leaves",
  "spruce_leaves",
  "birch_leaves",
  "jungle_leaves",
  "acacia_leaves",
  "dark_oak_leaves",
  "mangrove_leaves",
  "cherry_leaves",
  "azalea_leaves",
  "flowering_azalea_leaves",
]);

export function usesAlphaCutout(blockId: string): boolean {
  const name = blockName(blockId);
  if (usesTransparentTexture(blockId)) return false;
  if (isTurfBlock(blockId)) return false;
  if (ALPHA_CUTOUT_BLOCKS.has(name)) return true;
  if (name.endsWith("_door") || name.endsWith("_trapdoor")) return true;
  if (
    isBedBlock(blockId) ||
    isChestBlock(blockId) ||
    isSkullBlock(blockId) ||
    isBellBlock(blockId) ||
    isHangingLanternBlock(blockId) ||
    blockName(blockId) === "jack_o_lantern"
  ) {
    return true;
  }
  if (name.endsWith("_grass") && name !== "grass_block") return true;
  return /(_tulip|_orchid|_bluet|_daisy|_poppy|_leaves|_petals|_vine|_wart|_roots|_web)$/.test(
    name
  );
}

/** Semi-transparent blocks (glass, ice) — alpha blend, not alphaTest cutout. */
export function usesTransparentTexture(blockId: string): boolean {
  const name = blockName(blockId);
  if (name === "glass" || name === "tinted_glass" || name === "ice") return true;
  if (name.endsWith("_stained_glass")) return true;
  if (name === "glass_pane" || name === "tinted_glass_pane") return true;
  if (name.endsWith("_stained_glass_pane")) return true;
  return false;
}

/**
 * Blocks that must not use a cube-mapped texture (entity atlases, rods, etc.).
 * Grass, leaves, banners, beds, and mod blocks with fallbacks are excluded.
 */
export function preferColorOnlyTexture(blockId: string): boolean {
  const name = blockName(blockId);
  if (usesAlphaCutout(blockId)) return false;
  if (isBannerBlock(blockId)) return true;
  if (isFenceBlock(blockId) && name !== "bamboo_fence" && name !== "bamboo_fence_gate") {
    return true;
  }
  if (!isVanillaBlock(blockId)) return false;
  if (/(_torch|_sign|_bars)$/.test(name) || name === "lightning_rod" || name === "end_rod") {
    return true;
  }
  if (
    /^(chain|ladder|lever|repeater|comparator|rail|powered_rail|detector_rail|activator_rail|tripwire|tripwire_hook|flower_pot|decorated_pot|lightning_rod|redstone_wire|iron_bars)$/.test(
      name
    )
  ) {
    return true;
  }
  return false;
}

export function shouldUseBlockTexture(blockId: string): boolean {
  return !preferColorOnlyTexture(blockId);
}

/** @deprecated use shouldUseBlockTexture */
export function preferSolidCubeTexture(blockId: string): boolean {
  return shouldUseBlockTexture(blockId) && !usesAlphaCutout(blockId);
}

const loadedTextures = new Map<string, THREE.Texture>();

function textureCacheKey(blockId: string, options?: LoadBlockTextureOptions): string {
  if (isTurfBlock(blockId)) {
    return turfTextureCacheKey(
      blockId,
      options?.properties ?? {},
      options?.tintColor ?? "#888888"
    );
  }
  if (isChestBlock(blockId)) {
    return chestTextureCacheKey(blockId, options?.properties ?? {});
  }
  if (isSkullBlock(blockId)) {
    return skullTextureCacheKey(blockId, options?.blockEntity);
  }
  return blockId;
}

export function peekLoadedTexture(
  blockId: string,
  options?: LoadBlockTextureOptions
): THREE.Texture | null {
  return loadedTextures.get(textureCacheKey(blockId, options)) ?? null;
}

function inferredTexturePaths(name: string): string[] {
  const out: string[] = [];

  for (const color of WOOL_COLORS) {
    if (name === `${color}_bed`) {
      out.push(`block/${color}_wool.png`);
      return out;
    }
    if (name === `${color}_carpet`) {
      out.push(`block/${color}_wool.png`);
      return out;
    }
    if (name === `${color}_wall_banner` || name === `${color}_banner`) {
      out.push(`entity/banner/base.png`);
      return out;
    }
  }

  if (name === "bell") {
    out.push("entity/bell/bell_body.png");
    return out;
  }

  if (name.endsWith("_door")) {
    out.push(`block/${name}_bottom.png`, `block/${name}_top.png`);
    return out;
  }

  if (name.endsWith("_trapdoor")) {
    out.push(`block/${name}.png`);
    return out;
  }

  if (name.endsWith("_wall_sign") || name.endsWith("_wall_hanging_sign")) {
    for (const wood of WOOD_TYPES) {
      if (name.startsWith(`${wood}_`)) {
        out.push(`block/${wood}_planks.png`);
        return out;
      }
    }
  }

  for (const wood of WOOD_TYPES) {
    if (name === `stripped_${wood}_wood`) {
      out.push(`block/stripped_${wood}_log.png`, `block/stripped_${wood}_log_top.png`);
      return out;
    }
    if (name === `${wood}_wood`) {
      out.push(`block/${wood}_log.png`, `block/${wood}_log_top.png`);
      return out;
    }
  }

  for (const wood of WOOD_TYPES) {
    if (
      name === `${wood}_stairs` ||
      name === `${wood}_slab` ||
      name === `${wood}_fence` ||
      name === `${wood}_fence_gate` ||
      name === `${wood}_button` ||
      name === `${wood}_pressure_plate` ||
      name === `${wood}_sign` ||
      name === `${wood}_wall_sign` ||
      name === `${wood}_hanging_sign` ||
      name === `${wood}_wall_hanging_sign`
    ) {
      out.push(`block/${wood}_planks.png`);
      return out;
    }
  }

  if (name === "bamboo_fence" || name === "bamboo_fence_gate") {
    out.push(`block/${name}.png`);
    return out;
  }

  if (name === "glass_pane") {
    out.push("block/glass.png");
    return out;
  }
  if (name === "tinted_glass_pane") {
    out.push("block/tinted_glass.png");
    return out;
  }
  const stainedPane = name.match(/^(.+)_stained_glass_pane$/);
  if (stainedPane) {
    out.push(`block/${stainedPane[1]}_stained_glass.png`);
    return out;
  }

  const stairSlab = name.match(/^(.+)_(stairs|slab)$/);
  if (stairSlab) {
    const base = stairSlab[1]!;
    const tex = STAIR_SLAB_BASE[base] ?? base;
    out.push(`block/${tex}.png`);
    return out;
  }

  if (name.endsWith("_wall")) {
    const base = name.slice(0, -5);
    const tex = STAIR_SLAB_BASE[base] ?? base;
    out.push(`block/${tex}.png`);
    return out;
  }

  return out;
}

/** Ordered candidate paths to probe (HEAD); first hit wins. */
export function textureCandidates(blockId: string): string[] {
  const name = blockName(blockId);
  const seen = new Set<string>();
  const out: string[] = [];

  const add = (path: string) => {
    if (!seen.has(path)) {
      seen.add(path);
      out.push(path);
    }
  };

  for (const path of TEXTURE_OVERRIDES[name] ?? []) {
    add(path);
  }
  const inferred = inferredTexturePaths(name);
  for (const path of inferred) {
    add(path);
  }

  // Skip generic block/{id}.png probes when we already mapped a real texture
  // (avoids console 404s for stairs, walls, crops, etc.).
  const hasMappedTexture = (TEXTURE_OVERRIDES[name]?.length ?? 0) > 0 || inferred.length > 0;
  if (!hasMappedTexture) {
    add(`block/${name}.png`);
  }

  return out;
}

/** Probe paths on mcasset (shared by vanilla id and mod fallbacks). */
async function resolvePathsToUrl(paths: string[]): Promise<string | null> {
  for (const path of paths) {
    const url = textureUrl(path);
    try {
      const res = await fetch(url, { method: "HEAD" });
      if (res.ok) return url;
    } catch {
      /* try next */
    }
  }
  return null;
}

/** Probe mcasset for a vanilla block id only (no mod fallback recursion). */
async function resolveVanillaTextureUrl(blockId: string): Promise<string | null> {
  return resolvePathsToUrl(textureCandidates(blockId));
}

/**
 * Macaw's Fences (mcwfences) models mostly reference vanilla cherry/oak planks.
 * Curved gates use cherry_planks + cherry_log — map explicitly before generic fallback.
 */
function modTexturePaths(blockId: string): string[] {
  const { namespace, path } = parseBlockId(blockId);
  if (namespace !== "mcwfences") return [];

  const out: string[] = [];
  for (const wood of WOOD_TYPES) {
    if (!path.includes(wood)) continue;
    out.push(`block/${wood}_planks.png`);
    if (path.includes("gate") || path.includes("log") || path.includes("curved")) {
      out.push(`block/${wood}_log.png`);
    }
  }
  if (path.includes("metal") || path.includes("iron")) {
    out.push("block/iron_block.png");
  }
  if (path.includes("nether") && path.includes("brick")) {
    out.push("block/nether_bricks.png");
  }
  return out;
}

async function resolveModTextureUrl(blockId: string): Promise<string | null> {
  const modPaths = modTexturePaths(blockId);
  if (modPaths.length > 0) {
    const hit = await resolvePathsToUrl(modPaths);
    if (hit) return hit;
  }
  const fallback = vanillaFallbackBlockId(blockId);
  if (fallback) {
    return resolveVanillaTextureUrl(fallback);
  }
  return null;
}

async function resolveTextureUrl(blockId: string): Promise<string | null> {
  const cached = urlResolveCache.get(blockId);
  if (cached) return cached;

  const promise = (async () => {
    if (isVanillaBlock(blockId)) {
      return resolveVanillaTextureUrl(blockId);
    }
    return resolveModTextureUrl(blockId);
  })();

  urlResolveCache.set(blockId, promise);
  return promise;
}

function configureTexture(
  tex: THREE.Texture,
  opts: { cutout?: boolean; transparent?: boolean }
): void {
  const clamp = opts.cutout || opts.transparent;
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = clamp ? THREE.ClampToEdgeWrapping : THREE.RepeatWrapping;
  tex.wrapT = clamp ? THREE.ClampToEdgeWrapping : THREE.RepeatWrapping;
  tex.needsUpdate = true;
}

export interface LoadBlockTextureOptions {
  blockEntity?: Record<string, unknown>;
  properties?: Record<string, string>;
  /** Map color for biome-tinted blocks (grass, etc.). */
  tintColor?: string;
}

export function loadBlockTexture(
  blockId: string,
  options?: LoadBlockTextureOptions
): Promise<THREE.Texture | null> {
  const cacheKey = textureCacheKey(blockId, options);

  const cached = textureCache.get(cacheKey);
  if (cached) return cached;

  if (isBellBlock(blockId)) {
    const promise = loadBellTexture(blockId).then((tex) => {
      if (tex) loadedTextures.set(cacheKey, tex);
      return tex;
    });
    textureCache.set(cacheKey, promise);
    return promise;
  }

  if (isTurfBlock(blockId)) {
    const promise = composeTurfTexture(
      blockId,
      options?.properties ?? {},
      options?.tintColor ?? "#888888"
    ).then((tex) => {
      if (tex) loadedTextures.set(cacheKey, tex);
      return tex;
    });
    textureCache.set(cacheKey, promise);
    return promise;
  }

  if (isSkullBlock(blockId)) {
    const promise = loadSkullTexture(blockId, options?.blockEntity).then((tex) => {
      if (tex) loadedTextures.set(cacheKey, tex);
      return tex;
    });
    textureCache.set(cacheKey, promise);
    return promise;
  }

  if (isBedBlock(blockId)) {
    const promise = loadBedTexture(blockId).then((tex) => {
      if (tex) loadedTextures.set(cacheKey, tex);
      return tex;
    });
    textureCache.set(cacheKey, promise);
    return promise;
  }

  if (isChestBlock(blockId)) {
    const cutout = usesAlphaCutout(blockId);
    const url = chestTextureUrl(blockId, options?.properties ?? {});
    const promise = new Promise<THREE.Texture | null>((resolve) => {
      const loader = new THREE.TextureLoader();
      loader.setCrossOrigin("anonymous");
      loader.load(
        url,
        (tex) => {
          configureTexture(tex, { cutout });
          loadedTextures.set(cacheKey, tex);
          resolve(tex);
        },
        undefined,
        () => resolve(null)
      );
    });
    textureCache.set(cacheKey, promise);
    return promise;
  }

  const cutout = usesAlphaCutout(blockId);
  const transparent = usesTransparentTexture(blockId);
  const promise = resolveTextureUrl(blockId).then((url) => {
    if (!url) return null;
    return new Promise<THREE.Texture | null>((resolve) => {
      const loader = new THREE.TextureLoader();
      loader.setCrossOrigin("anonymous");
      loader.load(
        url,
        (tex) => {
          configureTexture(tex, { cutout, transparent });
          loadedTextures.set(cacheKey, tex);
          resolve(tex);
        },
        undefined,
        () => resolve(null)
      );
    });
  });

  textureCache.set(cacheKey, promise);
  return promise;
}

/** For debugging / block picker previews */
export function blockTextureUrl(blockId: string): string {
  return textureUrl(textureCandidates(blockId)[0] ?? `block/${blockName(blockId)}.png`);
}
