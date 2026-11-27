package dev.fourwinds.roads;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

final class RoadVersion {
    static final ChunkStatus CARVERS = ChunkStatus.LIQUID_CARVERS;
    static final ChunkStatus STRUCTURE_STARTS = ChunkStatus.STRUCTURE_STARTS;
    static final String DEFAULT_SOIL = "loam";
    static final Block SHORT_GRASS = Blocks.GRASS;

    static String tfcMaterial(String path) {
        if (path.startsWith("wood/fence/")) return "wood/planks/" + path.substring(11) + "_fence";
        if (path.startsWith("wood/sign/")) return "wood/planks/" + path.substring(10) + "_sign";
        if (path.startsWith("coarse_dirt/")) return "rooted_dirt/" + path.substring(12);
        return path;
    }

    static boolean full(ChunkAccess chunk) { return chunk.getStatus() == ChunkStatus.FULL; }
    static boolean lit(ChunkAccess chunk) { return chunk.getStatus().isOrAfter(ChunkStatus.LIGHT); }
    static StructurePlaceSettings dry(StructurePlaceSettings settings) { return settings.setKeepLiquids(false); }
    static CompoundTag saveEntity(BlockEntity entity, WorldGenRegion level) { return entity.saveWithFullMetadata(); }
    static CompoundTag saveStack(ItemStack stack, WorldGenRegion level) { return stack.save(new CompoundTag()); }
}
