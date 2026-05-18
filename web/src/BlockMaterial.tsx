import { useEffect, useState } from "react";
import * as THREE from "three";
import {
  loadBlockTexture,
  peekLoadedTexture,
  shouldUseBlockTexture,
  usesAlphaCutout,
  usesTransparentTexture,
} from "./blockTextures";
import { isBedBlock } from "./bedTexture";
import { isBellBlock } from "./bellTexture";
import { isHangingLanternBlock } from "./displayBlocks";

export type RenderMode = "color" | "texture";

interface Props {
  mode: RenderMode;
  blockId: string;
  fallbackColor: string;
  blockEntity?: Record<string, unknown>;
  properties?: Record<string, string>;
}

export function BlockMaterial({
  mode,
  blockId,
  fallbackColor,
  blockEntity,
  properties,
}: Props) {
  const loadTexture = mode === "texture" && shouldUseBlockTexture(blockId);
  const cutout = usesAlphaCutout(blockId);
  const transparent = usesTransparentTexture(blockId);
  const bed = isBedBlock(blockId);
  const entityCutout = cutout || isBellBlock(blockId) || isHangingLanternBlock(blockId);
  const textureOpts = { blockEntity, properties, tintColor: fallbackColor };
  const [map, setMap] = useState<THREE.Texture | null>(() =>
    loadTexture ? peekLoadedTexture(blockId, textureOpts) : null
  );

  useEffect(() => {
    if (!loadTexture) {
      setMap(null);
      return;
    }
    const existing = peekLoadedTexture(blockId, textureOpts);
    if (existing) {
      setMap(existing);
      return;
    }
    let cancelled = false;
    loadBlockTexture(blockId, textureOpts).then((tex) => {
      if (!cancelled) setMap(tex);
    });
    return () => {
      cancelled = true;
    };
  }, [loadTexture, blockId, blockEntity, properties, fallbackColor]);

  const baseColor = fallbackColor || "#888888";

  if (loadTexture && map) {
    if (transparent) {
      return (
        <meshStandardMaterial
          map={map}
          color="#ffffff"
          transparent
          opacity={0.4}
          depthWrite={false}
          roughness={0.05}
          metalness={0}
          side={THREE.DoubleSide}
        />
      );
    }
    if (entityCutout || bed) {
      return (
        <meshBasicMaterial
          map={map}
          color="#ffffff"
          transparent
          alphaTest={
            bed ? 0.12 : isBellBlock(blockId) ? 0.2 : isHangingLanternBlock(blockId) ? 0.25 : 0.35
          }
          side={THREE.DoubleSide}
          depthWrite
        />
      );
    }
    return (
      <meshStandardMaterial
        map={map}
        color="#ffffff"
        roughness={0.9}
        metalness={0}
      />
    );
  }

  return <meshStandardMaterial color={baseColor} roughness={0.9} metalness={0} />;
}
