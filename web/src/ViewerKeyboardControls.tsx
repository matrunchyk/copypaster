import { useFrame, useThree } from "@react-three/fiber";
import { useEffect, useRef } from "react";
import * as THREE from "three";
import type { OrbitControls as OrbitControlsImpl } from "three-stdlib";

const MOVE_KEYS = new Set([
  "KeyW",
  "KeyA",
  "KeyS",
  "KeyD",
  "ArrowUp",
  "ArrowDown",
  "ArrowLeft",
  "ArrowRight",
  "KeyQ",
  "KeyE",
]);

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable;
}

/** WASD / arrows: pan camera + orbit target; Q/E: down / up. */
export function ViewerKeyboardControls() {
  const keys = useRef(new Set<string>());
  const { camera, controls } = useThree();
  const move = useRef(new THREE.Vector3());
  const forward = useRef(new THREE.Vector3());
  const right = useRef(new THREE.Vector3());

  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (isTypingTarget(e.target)) return;
      if (!MOVE_KEYS.has(e.code)) return;
      keys.current.add(e.code);
      e.preventDefault();
    };
    const up = (e: KeyboardEvent) => {
      keys.current.delete(e.code);
    };
    const clear = () => keys.current.clear();
    window.addEventListener("keydown", down);
    window.addEventListener("keyup", up);
    window.addEventListener("blur", clear);
    return () => {
      window.removeEventListener("keydown", down);
      window.removeEventListener("keyup", up);
      window.removeEventListener("blur", clear);
    };
  }, []);

  useFrame((_, delta) => {
    const orbit = controls as OrbitControlsImpl | null;
    if (!orbit || keys.current.size === 0) return;

    camera.getWorldDirection(forward.current);
    forward.current.y = 0;
    if (forward.current.lengthSq() < 1e-8) {
      forward.current.set(0, 0, -1);
    } else {
      forward.current.normalize();
    }
    right.current.crossVectors(forward.current, THREE.Object3D.DEFAULT_UP).normalize();

    move.current.set(0, 0, 0);
    const k = keys.current;
    if (k.has("KeyW") || k.has("ArrowUp")) move.current.add(forward.current);
    if (k.has("KeyS") || k.has("ArrowDown")) move.current.sub(forward.current);
    if (k.has("KeyA") || k.has("ArrowLeft")) move.current.sub(right.current);
    if (k.has("KeyD") || k.has("ArrowRight")) move.current.add(right.current);
    if (k.has("KeyQ")) move.current.y -= 1;
    if (k.has("KeyE")) move.current.y += 1;

    if (move.current.lengthSq() === 0) return;

    const step = 20 * delta;
    move.current.multiplyScalar(step);
    orbit.target.add(move.current);
    camera.position.add(move.current);
    orbit.update();
  });

  return null;
}
