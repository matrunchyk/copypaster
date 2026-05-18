import { useEffect, useState } from "react";
import { searchBlocks } from "./api";
import type { BlockSearchResult } from "./types";

interface Props {
  value: string;
  onSelect: (block: BlockSearchResult) => void;
  placeholder?: string;
}

export function BlockPicker({ value, onSelect, placeholder }: Props) {
  const [query, setQuery] = useState(value);
  const [results, setResults] = useState<BlockSearchResult[]>([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    setQuery(value);
  }, [value]);

  useEffect(() => {
    if (!open) return;
    const t = setTimeout(() => {
      searchBlocks(query)
        .then(setResults)
        .catch(() => setResults([]));
    }, 200);
    return () => clearTimeout(t);
  }, [query, open]);

  return (
    <div className="block-picker">
      <input
        value={query}
        placeholder={placeholder ?? "Search blocks…"}
        onFocus={() => setOpen(true)}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
      />
      {open && results.length > 0 && (
        <div className="block-picker-results">
          {results.map((b) => (
            <button
              key={b.id}
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => {
                onSelect(b);
                setQuery(b.id);
                setOpen(false);
              }}
            >
              <span
                className="swatch"
                style={{ background: b.color, display: "inline-block", marginRight: 6 }}
              />
              {b.displayName}{" "}
              <span className="muted">{b.id}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
