package dev.fourwinds.roads;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

final class VillageAccess {
    static final TagKey<ConfiguredStructureFeature<?, ?>> DESTINATIONS =
            TagKey.create(RoadApi.STRUCTURE, FourWinds.id("destinations"));

    static boolean enabled(ServerLevel level) { return level.structureFeatureManager().shouldGenerateFeatures(); }
    static ConfiguredStructureFeature<?, ?> type(StructureStart start) { return start.getFeature(); }
    static StructureStart start(ChunkAccess chunk, ConfiguredStructureFeature<?, ?> type) { return chunk.getStartForFeature(type); }
    static ChunkPos potential(RandomSpreadStructurePlacement spread, long seed, int x, int z) {
        return spread.getPotentialFeatureChunk(seed, x, z);
    }
    static List<StructureStart> inChunk(WorldGenRegion region, ChunkPos pos) {
        return region.getLevel().structureFeatureManager().forWorldGenRegion(region).startsForFeature(
                SectionPos.of(pos, region.getMinSection()), any -> true);
    }

    static Set<StructurePlacement> placements(WorldGenRegion region, HolderSet<ConfiguredStructureFeature<?, ?>> targets) {
        var result = new HashSet<StructurePlacement>();
        region.getLevel().getChunkSource().getGenerator().possibleStructureSets().forEach(holder -> {
            var set = holder.value();
            if (set.structures().stream().anyMatch(entry -> targets.contains(entry.structure()))) result.add(set.placement());
        });
        return result;
    }
    static boolean isStart(WorldGenRegion region, StructurePlacement placement, ChunkPos pos) {
        var source = region.getLevel().getChunkSource();
        return placement.isFeatureChunk(source.getGenerator(), region.getSeed(), pos.x, pos.z);
    }
    static void startPieces(WorldGenRegion region, ChunkAccess scratch) {
        var level = region.getLevel();
        var source = level.getChunkSource();
        source.getGenerator().createStructures(region.registryAccess(), level.structureFeatureManager(), scratch, level.getStructureManager(), region.getSeed());
    }
}
