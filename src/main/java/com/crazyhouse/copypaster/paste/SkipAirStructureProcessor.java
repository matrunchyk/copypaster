package com.crazyhouse.copypaster.paste;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Skips air blocks in the structure so destination blocks are left unchanged. */
public final class SkipAirStructureProcessor extends StructureProcessor {

    public static final SkipAirStructureProcessor INSTANCE = new SkipAirStructureProcessor();

    private SkipAirStructureProcessor() {}

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level,
            BlockPos offset,
            BlockPos pos,
            StructureTemplate.StructureBlockInfo blockInfo,
            StructureTemplate.StructureBlockInfo relativeInfo,
            StructurePlaceSettings settings) {
        if (blockInfo == null || blockInfo.state().isAir()) {
            return null;
        }
        return blockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.BLOCK_IGNORE;
    }
}
