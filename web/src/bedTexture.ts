import * as THREE from "three";

const MC_ASSET_VERSION = "1.21.4";
const BASE = `https://assets.mcasset.cloud/${MC_ASSET_VERSION}/assets/minecraft/textures`;

const textureCache = new Map<string, Promise<THREE.Texture | null>>();

export function isBedBlock(blockId: string): boolean {
  const name = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  return name.endsWith("_bed");
}

export function bedColor(blockId: string): string {
  const name = blockId.includes(":") ? blockId.split(":")[1]! : blockId;
  return name.replace(/_bed$/, "");
}

function loadUrl(url: string, cutout: boolean): Promise<THREE.Texture | null> {
  return new Promise((resolve) => {
    const loader = new THREE.TextureLoader();
    loader.setCrossOrigin("anonymous");
    loader.load(
      url,
      (tex) => {
        tex.magFilter = THREE.NearestFilter;
        tex.minFilter = THREE.NearestFilter;
        tex.colorSpace = THREE.SRGBColorSpace;
        if (cutout) {
          tex.wrapS = THREE.ClampToEdgeWrapping;
          tex.wrapT = THREE.ClampToEdgeWrapping;
        }
        tex.needsUpdate = true;
        resolve(tex);
      },
      undefined,
      () => resolve(null)
    );
  });
}

/** Entity bed sheet (cutout) with wool fallback — cube approximation of dyed beds. */
export function loadBedTexture(blockId: string): Promise<THREE.Texture | null> {
  const cached = textureCache.get(blockId);
  if (cached) return cached;

  const color = bedColor(blockId);
  const promise = (async () => {
    const entity = await loadUrl(`${BASE}/entity/bed/${color}.png`, true);
    if (entity) return entity;
    return loadUrl(`${BASE}/block/${color}_wool.png`, false);
  })();

  textureCache.set(blockId, promise);
  return promise;
}
