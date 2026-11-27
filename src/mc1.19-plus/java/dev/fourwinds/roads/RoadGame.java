package dev.fourwinds.roads;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

public final class RoadGame {
    static final Block EARTH_SLAB = Blocks.MUD_BRICK_SLAB;
    static final Block EARTH_STAIRS = Blocks.MUD_BRICK_STAIRS;
    static ResourceLocation id(String namespace, String path) { return ResourceLocation.tryBuild(namespace, path); }
    static Holder<Biome> biome(ServerChunkCache chunks, int x, int y, int z) {
        return chunks.getGenerator().getBiomeSource().getNoiseBiome(x, y, z, chunks.randomState().sampler());
    }
    static int baseHeight(ServerLevel level, int x, int z, Heightmap.Types type) {
        var chunks = level.getChunkSource();
        return chunks.getGenerator().getBaseHeight(x, z, type, level, chunks.randomState());
    }
    public static net.minecraft.util.RandomSource random(long seed) { return net.minecraft.util.RandomSource.create(seed); }
    public static MutableComponent translated(String key, Object... args) { return Component.translatable(key, args); }
    public static MutableComponent literal(String text) { return Component.literal(text); }
    public static String translationKey(Component text) {
        return text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr ? tr.getKey() : "";
    }
}
