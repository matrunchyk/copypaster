package com.crazyhouse.copypaster.service;

import com.crazyhouse.copypaster.model.PendingCopy;
import com.crazyhouse.copypaster.model.UndoSnapshot;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class StructureStorageService {

    public record StructureInfo(
        String name,
        int sizeX, int sizeY, int sizeZ,
        int offsetX, int offsetY, int offsetZ,
        String creatorName,
        String createdAt,
        String dimension
    ) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int DEFAULT_MAX_VOLUME = 32_768;

    private final Path structuresDir;
    private int maxVolume = DEFAULT_MAX_VOLUME;

    public StructureStorageService(Path dataDir) {
        this.structuresDir = dataDir.resolve("structures");
    }

    public void setMaxVolume(int maxVolume) {
        this.maxVolume = maxVolume;
    }

    public int maxVolume() { return maxVolume; }

    public Path getNbtPath(String name) { return structuresDir.resolve(name + ".nbt"); }
    public Path getMetaPath(String name) { return structuresDir.resolve(name + ".json"); }

    public boolean nbtExists(String name)  { return Files.exists(getNbtPath(name)); }
    public boolean metaExists(String name) { return Files.exists(getMetaPath(name)); }

    // ── Save ─────────────────────────────────────────────────────────────────

    public void save(ServerPlayer player, String name, PendingCopy pending) throws IOException {
        Files.createDirectories(structuresDir);

        MinecraftServer server = (MinecraftServer) player.level().getServer();
        ServerLevel level = server != null ? server.getLevel(pending.dimension()) : null;
        if (level == null) {
            level = (ServerLevel) player.level();
        }

        loadChunks(level, pending.corner1(), pending.corner2());

        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, pending.corner1(),
                new Vec3i(pending.sizeX(), pending.sizeY(), pending.sizeZ()),
                true, List.of());

        CompoundTag tag = template.save(new CompoundTag());
        try (OutputStream os = Files.newOutputStream(getNbtPath(name))) {
            NbtIo.writeCompressed(tag, os);
        }

        JsonObject meta = new JsonObject();
        meta.addProperty("name", name);
        meta.addProperty("createdAt", Instant.now().toString());
        meta.addProperty("creatorUuid", player.getUUID().toString());
        meta.addProperty("creatorName", player.getName().getString());
        meta.addProperty("dimension", pending.dimension().toString());
        meta.addProperty("sizeX", pending.sizeX());
        meta.addProperty("sizeY", pending.sizeY());
        meta.addProperty("sizeZ", pending.sizeZ());
        meta.addProperty("corner1X", pending.corner1().getX());
        meta.addProperty("corner1Y", pending.corner1().getY());
        meta.addProperty("corner1Z", pending.corner1().getZ());
        meta.addProperty("offsetX", pending.offset().getX());
        meta.addProperty("offsetY", pending.offset().getY());
        meta.addProperty("offsetZ", pending.offset().getZ());
        Files.writeString(getMetaPath(name), GSON.toJson(meta));
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    public StructureInfo loadMeta(String name) throws IOException {
        JsonObject o = JsonParser.parseString(Files.readString(getMetaPath(name))).getAsJsonObject();
        return new StructureInfo(
            o.get("name").getAsString(),
            o.get("sizeX").getAsInt(),
            o.get("sizeY").getAsInt(),
            o.get("sizeZ").getAsInt(),
            o.get("offsetX").getAsInt(),
            o.get("offsetY").getAsInt(),
            o.get("offsetZ").getAsInt(),
            o.get("creatorName").getAsString(),
            o.get("createdAt").getAsString(),
            o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld"
        );
    }

    public StructureTemplate loadTemplate(String name, HolderLookup.Provider registries) throws IOException {
        CompoundTag tag = readTemplateTag(name);
        StructureTemplate template = new StructureTemplate();
        template.load(registries.lookupOrThrow(Registries.BLOCK), tag);
        return template;
    }

    public CompoundTag readTemplateTag(String name) throws IOException {
        try (InputStream is = Files.newInputStream(getNbtPath(name))) {
            return NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
        }
    }

    public void saveTemplate(String name, StructureTemplate template, HolderLookup.Provider registries)
            throws IOException {
        Files.createDirectories(structuresDir);
        CompoundTag tag = template.save(new CompoundTag());
        try (OutputStream os = Files.newOutputStream(getNbtPath(name))) {
            NbtIo.writeCompressed(tag, os);
        }
    }

    // ── Paste helpers ─────────────────────────────────────────────────────────

    public int countNonAirInBox(ServerLevel level, AABB box) {
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX - 1.0E-5, box.maxY - 1.0E-5, box.maxZ - 1.0E-5);
        loadChunks(level, min, max);
        int count = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) count++;
                }
            }
        }
        return count;
    }

    public List<UndoSnapshot.BlockSnapshot> captureRegionInBox(ServerLevel level, AABB box) {
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX - 1.0E-5, box.maxY - 1.0E-5, box.maxZ - 1.0E-5);
        loadChunks(level, min, max);
        int volume = (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        List<UndoSnapshot.BlockSnapshot> snaps = new ArrayList<>(volume);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    snaps.add(new UndoSnapshot.BlockSnapshot(pos.immutable(), state));
                }
            }
        }
        return snaps;
    }

    public void restoreRegion(ServerLevel level, UndoSnapshot snap) {
        for (UndoSnapshot.BlockSnapshot bs : snap.blocks()) {
            level.setBlock(bs.pos(), bs.state(), Block.UPDATE_CLIENTS);
        }
    }

    // ── List / Delete ─────────────────────────────────────────────────────────

    public List<String> listNames() {
        if (!Files.exists(structuresDir)) return List.of();
        try (var stream = Files.list(structuresDir)) {
            return stream
                .map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".nbt"))
                .map(n -> n.substring(0, n.length() - 4))
                .sorted()
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public List<StructureInfo> listAll() {
        return listNames().stream()
            .filter(this::metaExists)
            .<StructureInfo>mapMulti((name, consumer) -> {
                try { consumer.accept(loadMeta(name)); }
                catch (IOException ignored) {}
            })
            .toList();
    }

    public void delete(String name) throws IOException {
        Files.deleteIfExists(getNbtPath(name));
        Files.deleteIfExists(getMetaPath(name));
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static void loadChunks(ServerLevel level, BlockPos min, BlockPos max) {
        int minCX = min.getX() >> 4, maxCX = max.getX() >> 4;
        int minCZ = min.getZ() >> 4, maxCZ = max.getZ() >> 4;
        for (int cx = minCX; cx <= maxCX; cx++)
            for (int cz = minCZ; cz <= maxCZ; cz++)
                level.getChunk(cx, cz);
    }
}
