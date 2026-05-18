export interface StructureSummary {
  name: string;
  sizeX: number;
  sizeY: number;
  sizeZ: number;
  creatorName: string;
  createdAt: string;
  dimension: string;
}

export interface PaletteEntry {
  index: number;
  id: string;
  displayName: string;
  color: string;
  properties: Record<string, string>;
}

export interface BlockPlacement {
  x: number;
  y: number;
  z: number;
  paletteIndex: number;
  /** Block entity NBT from structure template (banner patterns, etc.). */
  blockEntity?: Record<string, unknown>;
}

export interface StructureModel {
  name: string;
  meta: {
    sizeX: number;
    sizeY: number;
    sizeZ: number;
    offsetX: number;
    offsetY: number;
    offsetZ: number;
    creatorName: string;
    createdAt: string;
    dimension: string;
  };
  size: [number, number, number];
  palette: PaletteEntry[];
  blocks: BlockPlacement[];
  blockCounts: { id: string; count: number }[];
}

export interface BlockSearchResult {
  id: string;
  displayName: string;
  color: string;
}

export interface VoxelEdit {
  x: number;
  y: number;
  z: number;
  id: string;
  properties: Record<string, string>;
}
