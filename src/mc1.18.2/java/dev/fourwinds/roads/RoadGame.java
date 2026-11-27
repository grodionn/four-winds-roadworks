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
    static final Block EARTH_SLAB = Blocks.BRICK_SLAB;
    static final Block EARTH_STAIRS = Blocks.BRICK_STAIRS;
    static ResourceLocation id(String namespace, String path) { return new ResourceLocation(namespace, path); }
    static Holder<Biome> biome(ServerChunkCache chunks, int x, int y, int z) {
        return chunks.getGenerator().getNoiseBiome(x, y, z);
    }
    static int baseHeight(ServerLevel level, int x, int z, Heightmap.Types type) {
        var chunks = level.getChunkSource();
        return chunks.getGenerator().getBaseHeight(x, z, type, level);
    }
    public static java.util.Random random(long seed) { return new java.util.Random(seed); }
    public static MutableComponent translated(String key, Object... args) { return new net.minecraft.network.chat.TranslatableComponent(key, args); }
    public static MutableComponent literal(String text) { return new net.minecraft.network.chat.TextComponent(text); }
    public static String translationKey(Component text) {
        return text instanceof net.minecraft.network.chat.TranslatableComponent tr ? tr.getKey() : "";
    }
}
