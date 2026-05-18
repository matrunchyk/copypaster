package com.crazyhouse.copypaster.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StructureEditRequest(
    Map<String, String> paletteRemap,
    List<VoxelEdit> voxelEdits
) {
    public record VoxelEdit(int x, int y, int z, String id, Map<String, String> properties) {}

    public static StructureEditRequest fromJson(JsonObject root) {
        Map<String, String> remap = new HashMap<>();
        if (root.has("paletteRemap") && root.get("paletteRemap").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("paletteRemap").entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    remap.put(e.getKey(), e.getValue().getAsString());
                }
            }
        }
        List<VoxelEdit> edits = new java.util.ArrayList<>();
        if (root.has("voxelEdits") && root.get("voxelEdits").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("voxelEdits")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                Map<String, String> props = new LinkedHashMap<>();
                if (o.has("properties") && o.get("properties").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> pe : o.getAsJsonObject("properties").entrySet()) {
                        if (pe.getValue().isJsonPrimitive()) {
                            props.put(pe.getKey(), pe.getValue().getAsString());
                        }
                    }
                }
                edits.add(new VoxelEdit(
                    o.get("x").getAsInt(),
                    o.get("y").getAsInt(),
                    o.get("z").getAsInt(),
                    o.get("id").getAsString(),
                    props
                ));
            }
        }
        return new StructureEditRequest(remap, edits);
    }
}
