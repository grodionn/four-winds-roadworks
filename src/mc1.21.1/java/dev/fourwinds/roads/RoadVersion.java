package dev.fourwinds.roads;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

final class RoadVersion {
    static final ChunkStatus CARVERS = ChunkStatus.CARVERS;
    static final ChunkStatus STRUCTURE_STARTS = ChunkStatus.STRUCTURE_STARTS;
    static final String DEFAULT_SOIL = "entisol";
    static final Block SHORT_GRASS = Blocks.SHORT_GRASS;

    static String tfcMaterial(String path) {
        return path;
    }

    static boolean full(ChunkAccess chunk) { return chunk.getPersistedStatus() == ChunkStatus.FULL; }
    static boolean lit(ChunkAccess chunk) { return chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT); }
    static StructurePlaceSettings dry(StructurePlaceSettings settings) { return settings.setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING); }
    static CompoundTag saveEntity(BlockEntity entity, WorldGenRegion level) { return entity.saveWithFullMetadata(level.registryAccess()); }
    static CompoundTag saveStack(ItemStack stack, WorldGenRegion level) { return (CompoundTag)stack.save(level.registryAccess()); }

    static Tag signText(WorldGenRegion level, SignText text) {
        return SignText.DIRECT_CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), text).getOrThrow();
    }
}
