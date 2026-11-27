package dev.fourwinds.roads;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

final class VillageAccess {
    static final TagKey<Structure> DESTINATIONS =
            TagKey.create(RoadApi.STRUCTURE, FourWinds.id("destinations"));

    static boolean enabled(ServerLevel level) { return level.structureManager().shouldGenerateStructures(); }
    static Structure type(StructureStart start) { return start.getStructure(); }
    static StructureStart start(ChunkAccess chunk, Structure type) { return chunk.getStartForStructure(type); }
    static ChunkPos potential(RandomSpreadStructurePlacement spread, long seed, int x, int z) {
        return spread.getPotentialStructureChunk(seed, x, z);
    }
    static List<StructureStart> inChunk(WorldGenRegion region, ChunkPos pos) {
        return region.getLevel().structureManager().forWorldGenRegion(region).startsForStructure(pos, any -> true);
    }

    static Set<StructurePlacement> placements(WorldGenRegion region, HolderSet<Structure> targets) {
        var result = new HashSet<StructurePlacement>();
        var state = region.getLevel().getChunkSource().getGeneratorState();
        for (var target : targets) result.addAll(state.getPlacementsForStructure(target));
        return result;
    }
    static boolean isStart(WorldGenRegion region, StructurePlacement placement, ChunkPos pos) {
        var source = region.getLevel().getChunkSource();
        return placement.isStructureChunk(source.getGeneratorState(), pos.x, pos.z);
    }
    static void startPieces(WorldGenRegion region, ChunkAccess scratch) {
        var level = region.getLevel();
        var source = level.getChunkSource();
        source.getGenerator().createStructures(region.registryAccess(), source.getGeneratorState(), level.structureManager(), scratch, level.getStructureManager());
    }
}
