import * as THREE from "three";

const MC_ASSET_VERSION = "1.21.4";
const BASE = `https://assets.mcasset.cloud/${MC_ASSET_VERSION}/assets/minecraft/textures`;

const textureCache = new Map<string, Promise<THREE.Texture | null>>();

export function isBellBlock(blockId: string): boolean {
  const name = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  return name === "bell";
}

export function loadBellTexture(blockId: string): Promise<THREE.Texture | null> {
  const cached = textureCache.get(blockId);
  if (cached) return cached;

  const promise = new Promise<THREE.Texture | null>((resolve) => {
    const loader = new THREE.TextureLoader();
    loader.setCrossOrigin("anonymous");
    loader.load(
      `${BASE}/entity/bell/bell_body.png`,
      (tex) => {
        tex.magFilter = THREE.NearestFilter;
        tex.minFilter = THREE.NearestFilter;
        tex.colorSpace = THREE.SRGBColorSpace;
        tex.wrapS = THREE.ClampToEdgeWrapping;
        tex.wrapT = THREE.ClampToEdgeWrapping;
        tex.needsUpdate = true;
        resolve(tex);
      },
      undefined,
      () => resolve(null)
    );
  });

  textureCache.set(blockId, promise);
  return promise;
}
