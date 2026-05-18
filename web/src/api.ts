import type { BlockSearchResult, StructureModel, StructureSummary } from "./types";

const TOKEN_KEY = "copypaster_token";

export function getToken(): string {
  return sessionStorage.getItem(TOKEN_KEY) ?? "";
}

export function setToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token.trim());
}

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    ...(init?.headers as Record<string, string>),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (init?.body && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }
  const res = await fetch(path, { ...init, headers });
  if (!res.ok) {
    let msg = res.statusText;
    try {
      const err = (await res.json()) as { error?: string };
      if (err.error) msg = err.error;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export function listStructures(): Promise<StructureSummary[]> {
  return api("/api/structures");
}

export function loadStructure(name: string): Promise<StructureModel> {
  return api(`/api/structures/${encodeURIComponent(name)}`);
}

export function searchBlocks(q: string, limit = 40): Promise<BlockSearchResult[]> {
  const params = new URLSearchParams({ q, limit: String(limit) });
  return api(`/api/blocks?${params}`);
}

export function saveEdits(
  name: string,
  body: { paletteRemap: Record<string, string>; voxelEdits: unknown[] }
): Promise<{ ok: boolean }> {
  return api(`/api/structures/${encodeURIComponent(name)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function deleteStructure(name: string): Promise<{ ok: boolean }> {
  return api(`/api/structures/${encodeURIComponent(name)}`, { method: "DELETE" });
}
