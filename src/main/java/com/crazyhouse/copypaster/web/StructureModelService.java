package com.crazyhouse.copypaster.web;

import com.crazyhouse.copypaster.service.StructureStorageService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import java.lang.reflect.Field;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.MapColor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Exports / imports structure templates as JSON for the web UI.
 */
public final class StructureModelService {

    private record VoxelData(int sizeX, int sizeY, int sizeZ, Map<BlockPos, BlockState> voxels) {}

    private final StructureStorageService storage;

    public StructureModelService(StructureStorageService storage) {
        this.storage = storage;
    }

    public JsonObject exportModel(String name, HolderLookup.Provider registries) throws IOException {
        if (!storage.nbtExists(name) || !storage.metaExists(name)) {
            throw new IOException("Structure not found: " + name);
        }
        StructureStorageService.StructureInfo meta = storage.loadMeta(name);
        StructureTemplate template = storage.loadTemplate(name, registries);
        Vec3i size = template.getSize();
        int sizeX = size.getX() > 0 ? size.getX() : meta.sizeX();
        int sizeY = size.getY() > 0 ? size.getY() : meta.sizeY();
        int sizeZ = size.getZ() > 0 ? size.getZ() : meta.sizeZ();
        List<StructureTemplate.StructureBlockInfo> blockInfos = allBlocksFromTemplate(template);
        if ((long) sizeX * sizeY * sizeZ > StructureStorageService.MAX_VOLUME) {
            throw new IOException("Structure exceeds max volume");
        }

        Map<BlockState, Integer> stateToIndex = new LinkedHashMap<>();
        List<BlockState> palette = new ArrayList<>();
        List<JsonObject> blocksJson = new ArrayList<>();
        Map<String, Integer> blockIdCounts = new TreeMap<>();

        for (StructureTemplate.StructureBlockInfo info : blockInfos) {
            BlockState state = info.state();
            if (state.isAir()) continue;
            int paletteIndex = stateToIndex.computeIfAbsent(state, s -> {
                palette.add(s);
                return palette.size() - 1;
            });
            BlockPos pos = info.pos();
            JsonObject block = new JsonObject();
            block.addProperty("x", pos.getX());
            block.addProperty("y", pos.getY());
            block.addProperty("z", pos.getZ());
            block.addProperty("paletteIndex", paletteIndex);
            CompoundTag blockEntity = info.nbt();
            if (blockEntity != null && !blockEntity.isEmpty()) {
                block.add("blockEntity", compoundToJson(blockEntity));
            }
            blocksJson.add(block);

            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            blockIdCounts.merge(blockId, 1, Integer::sum);
        }

        JsonObject root = new JsonObject();
        root.addProperty("name", name);
        root.add("meta", metaToJson(meta));
        JsonArray sizeArr = new JsonArray();
        sizeArr.add(sizeX);
        sizeArr.add(sizeY);
        sizeArr.add(sizeZ);
        root.add("size", sizeArr);

        JsonArray paletteJson = new JsonArray();
        for (int i = 0; i < palette.size(); i++) {
            paletteJson.add(paletteEntryToJson(i, palette.get(i)));
        }
        root.add("palette", paletteJson);

        JsonArray blocksArr = new JsonArray();
        blocksJson.forEach(blocksArr::add);
        root.add("blocks", blocksArr);

        JsonArray countsArr = new JsonArray();
        blockIdCounts.forEach((id, count) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("count", count);
            countsArr.add(entry);
        });
        root.add("blockCounts", countsArr);
        return root;
    }

    public void applyEdits(String name, StructureEditRequest request, HolderLookup.Provider registries)
            throws IOException {
        if (!storage.nbtExists(name) || !storage.metaExists(name)) {
            throw new IOException("Structure not found: " + name);
        }

        StructureStorageService.StructureInfo meta = storage.loadMeta(name);
        StructureTemplate template = storage.loadTemplate(name, registries);
        Map<BlockPos, BlockState> voxelMap = new LinkedHashMap<>();
        for (StructureTemplate.StructureBlockInfo info : allBlocksFromTemplate(template)) {
            if (!info.state().isAir()) {
                voxelMap.put(info.pos().immutable(), info.state());
            }
        }

        if (!request.paletteRemap().isEmpty()) {
            Map<BlockState, BlockState> stateRemap = new HashMap<>();
            for (Map.Entry<BlockPos, BlockState> e : voxelMap.entrySet()) {
                BlockState state = e.getValue();
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                String targetId = request.paletteRemap().get(id);
                if (targetId != null && !targetId.equals(id)) {
                    BlockState newState = resolveBlockState(targetId, Map.of(), registries);
                    stateRemap.put(state, newState);
                }
            }
            if (!stateRemap.isEmpty()) {
                Map<BlockPos, BlockState> updated = new LinkedHashMap<>();
                for (Map.Entry<BlockPos, BlockState> e : voxelMap.entrySet()) {
                    updated.put(e.getKey(), stateRemap.getOrDefault(e.getValue(), e.getValue()));
                }
                voxelMap = updated;
            }
        }

        for (StructureEditRequest.VoxelEdit edit : request.voxelEdits()) {
            BlockPos pos = new BlockPos(edit.x(), edit.y(), edit.z());
            if (edit.id().equals("minecraft:air") || edit.id().equals("air")) {
                voxelMap.remove(pos);
            } else {
                BlockState state = resolveBlockState(edit.id(), edit.properties(), registries);
                voxelMap.put(pos, state);
            }
        }

        Vec3i size = template.getSize();
        int sizeX = size.getX() > 0 ? size.getX() : meta.sizeX();
        int sizeY = size.getY() > 0 ? size.getY() : meta.sizeY();
        int sizeZ = size.getZ() > 0 ? size.getZ() : meta.sizeZ();
        int vol = sizeX * sizeY * sizeZ;
        if (vol > StructureStorageService.MAX_VOLUME) {
            throw new IOException("Structure exceeds max volume");
        }
        if (voxelMap.size() > StructureStorageService.MAX_VOLUME) {
            throw new IOException("Too many blocks after edit");
        }

        CompoundTag out = buildTemplateTag(sizeX, sizeY, sizeZ, voxelMap, registries);
        StructureTemplate rebuilt = new StructureTemplate();
        rebuilt.load(registries.lookupOrThrow(Registries.BLOCK), out);
        storage.saveTemplate(name, rebuilt, registries);
    }

    private static CompoundTag buildTemplateTag(
            int sizeX, int sizeY, int sizeZ,
            Map<BlockPos, BlockState> voxels,
            HolderLookup.Provider registries) {
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> stateToIndex = new LinkedHashMap<>();
        ListTag blocksTag = new ListTag();

        for (Map.Entry<BlockPos, BlockState> e : voxels.entrySet()) {
            BlockState state = e.getValue();
            if (state.isAir()) continue;
            int idx = stateToIndex.computeIfAbsent(state, s -> {
                palette.add(s);
                return palette.size() - 1;
            });
            BlockPos pos = e.getKey();
            CompoundTag blockTag = new CompoundTag();
            blockTag.putIntArray(StructureTemplate.BLOCK_TAG_POS, new int[]{pos.getX(), pos.getY(), pos.getZ()});
            blockTag.putInt(StructureTemplate.BLOCK_TAG_STATE, idx);
            blocksTag.add(blockTag);
        }

        ListTag paletteStates = new ListTag();
        for (BlockState state : palette) {
            paletteStates.add(NbtUtils.writeBlockState(state));
        }

        ListTag sizeList = new ListTag();
        sizeList.add(IntTag.valueOf(sizeX));
        sizeList.add(IntTag.valueOf(sizeY));
        sizeList.add(IntTag.valueOf(sizeZ));

        CompoundTag tag = new CompoundTag();
        tag.put(StructureTemplate.SIZE_TAG, sizeList);
        tag.put(StructureTemplate.PALETTE_TAG, paletteStates);
        tag.put(StructureTemplate.BLOCKS_TAG, blocksTag);
        return tag;
    }

    @SuppressWarnings("unchecked")
    private static List<StructureTemplate.StructureBlockInfo> allBlocksFromTemplate(StructureTemplate template)
            throws IOException {
        try {
            Field palettesField = StructureTemplate.class.getDeclaredField("palettes");
            palettesField.setAccessible(true);
            List<?> palettes = (List<?>) palettesField.get(template);
            if (palettes == null || palettes.isEmpty()) {
                return List.of();
            }
            Object palette = palettes.get(0);
            if (!(palette instanceof StructureTemplate.Palette p)) {
                return List.of();
            }
            return p.blocks();
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to read structure template blocks", e);
        }
    }

    private static VoxelData readVoxelsFromNbt(CompoundTag tag, HolderLookup.Provider registries) throws IOException {
        HolderGetter<Block> blockLookup = registries.lookupOrThrow(Registries.BLOCK);
        int[] size = readSize(tag);
        ListTag paletteStates = readPaletteStateList(tag);
        List<BlockState> palette = new ArrayList<>(paletteStates.size());
        for (int i = 0; i < paletteStates.size(); i++) {
            palette.add(NbtUtils.readBlockState(blockLookup, paletteStates.getCompoundOrEmpty(i)));
        }

        Map<BlockPos, BlockState> voxels = new LinkedHashMap<>();
        ListTag blocksTag = tag.getListOrEmpty(StructureTemplate.BLOCKS_TAG);
        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompoundOrEmpty(i);
            int[] pos = blockTag.getIntArray(StructureTemplate.BLOCK_TAG_POS).orElse(new int[0]);
            if (pos.length < 3) continue;
            int stateIdx = blockTag.getInt(StructureTemplate.BLOCK_TAG_STATE).orElse(-1);
            if (stateIdx < 0 || stateIdx >= palette.size()) continue;
            BlockState state = palette.get(stateIdx);
            if (!state.isAir()) {
                voxels.put(new BlockPos(pos[0], pos[1], pos[2]), state);
            }
        }
        return new VoxelData(size[0], size[1], size[2], voxels);
    }

    private static int[] readSize(CompoundTag tag) {
        ListTag sizeList = tag.getListOrEmpty(StructureTemplate.SIZE_TAG);
        if (sizeList.size() >= 3) {
            return new int[]{
                sizeList.getIntOr(0, 0),
                sizeList.getIntOr(1, 0),
                sizeList.getIntOr(2, 0)
            };
        }
        int[] legacy = tag.getIntArray(StructureTemplate.SIZE_TAG).orElse(new int[0]);
        if (legacy.length >= 3) {
            return new int[]{legacy[0], legacy[1], legacy[2]};
        }
        return new int[]{0, 0, 0};
    }

    private static ListTag readPaletteStateList(CompoundTag tag) {
        ListTag direct = tag.getListOrEmpty(StructureTemplate.PALETTE_TAG);
        if (!direct.isEmpty()) {
            return direct;
        }
        Optional<ListTag> palettesOpt = tag.getList(StructureTemplate.PALETTE_LIST_TAG);
        if (palettesOpt.isEmpty() || palettesOpt.get().isEmpty()) {
            return new ListTag();
        }
        var first = palettesOpt.get().get(0);
        if (first instanceof ListTag list) {
            return list;
        }
        if (first instanceof CompoundTag compound) {
            return compound.getListOrEmpty(StructureTemplate.PALETTE_TAG);
        }
        return new ListTag();
    }

    public JsonArray searchBlocks(String query, int limit) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        JsonArray arr = new JsonArray();
        int count = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR) continue;
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) continue;
            String idStr = id.toString();
            String name = block.getName().getString().toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !idStr.contains(q) && !name.contains(q)) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("id", idStr);
            entry.addProperty("displayName", block.getName().getString());
            entry.addProperty("color", mapColorHex(block.defaultBlockState()));
            arr.add(entry);
            if (++count >= limit) break;
        }
        return arr;
    }

    private static BlockState resolveBlockState(
            String id, Map<String, String> properties, HolderLookup.Provider registries) throws IOException {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            throw new IOException("Invalid block id: " + id);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(ResourceKey.create(Registries.BLOCK, identifier));
        if (block == null || block == Blocks.AIR && !id.contains("air")) {
            throw new IOException("Unknown block: " + id);
        }
        BlockState state = block.defaultBlockState();
        if (!properties.isEmpty()) {
            for (Map.Entry<String, String> pe : properties.entrySet()) {
                Property<?> prop = state.getBlock().getStateDefinition().getProperty(pe.getKey());
                if (prop != null) {
                    state = setProperty(state, prop, pe.getValue());
                }
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setProperty(
            BlockState state, Property<T> prop, String valueName) {
        Optional<T> parsed = prop.getValue(valueName);
        if (parsed.isPresent()) {
            return state.setValue(prop, parsed.get());
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String stringifyProperty(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    private static JsonObject metaToJson(StructureStorageService.StructureInfo meta) {
        JsonObject o = new JsonObject();
        o.addProperty("sizeX", meta.sizeX());
        o.addProperty("sizeY", meta.sizeY());
        o.addProperty("sizeZ", meta.sizeZ());
        o.addProperty("offsetX", meta.offsetX());
        o.addProperty("offsetY", meta.offsetY());
        o.addProperty("offsetZ", meta.offsetZ());
        o.addProperty("creatorName", meta.creatorName());
        o.addProperty("createdAt", meta.createdAt());
        o.addProperty("dimension", meta.dimension());
        return o;
    }

    private static JsonObject paletteEntryToJson(int index, BlockState state) {
        JsonObject o = new JsonObject();
        o.addProperty("index", index);
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        o.addProperty("id", id);
        o.addProperty("displayName", state.getBlock().getName().getString());
        o.addProperty("color", mapColorHex(state));
        JsonObject props = new JsonObject();
        for (Property<?> prop : state.getProperties()) {
            props.addProperty(prop.getName(), stringifyProperty(state, prop));
        }
        o.add("properties", props);
        return o;
    }

    private static JsonObject compoundToJson(CompoundTag tag) {
        JsonObject o = new JsonObject();
        for (String key : tag.keySet()) {
            Tag value = tag.get(key);
            if (value != null) {
                o.add(key, tagToJson(value));
            }
        }
        return o;
    }

    private static JsonElement tagToJson(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            return compoundToJson(compound);
        }
        if (tag instanceof ListTag list) {
            JsonArray arr = new JsonArray();
            for (Tag entry : list) {
                arr.add(tagToJson(entry));
            }
            return arr;
        }
        if (tag instanceof StringTag s) {
            return new JsonPrimitive(s.value());
        }
        if (tag instanceof IntTag i) {
            return new JsonPrimitive(i.value());
        }
        if (tag instanceof ByteTag b) {
            return new JsonPrimitive(b.value());
        }
        return new JsonPrimitive(tag.toString());
    }

    private static String mapColorHex(BlockState state) {
        MapColor mapColor = state.getMapColor(null, BlockPos.ZERO);
        if (mapColor == null) {
            return "#808080";
        }
        int col = mapColor.col;
        int r = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int b = col & 0xFF;
        return String.format("#%02x%02x%02x", r, g, b);
    }

    /** Read raw NBT bytes for download. */
    public byte[] readNbtBytes(String name) throws IOException {
        return Files.readAllBytes(storage.getNbtPath(name));
    }
}
