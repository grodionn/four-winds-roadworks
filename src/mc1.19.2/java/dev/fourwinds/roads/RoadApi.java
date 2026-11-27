package dev.fourwinds.roads;

import com.mojang.serialization.Codec;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.Fluid;

public final class RoadApi {
    static final Registry<Block> BLOCKS = Registry.BLOCK;
    static final Registry<Feature<?>> FEATURES = Registry.FEATURE;
    static final ResourceKey<Registry<Block>> BLOCK = Registry.BLOCK_REGISTRY;
    static final ResourceKey<Registry<Fluid>> FLUID = Registry.FLUID_REGISTRY;
    static final ResourceKey<Registry<Biome>> BIOME = Registry.BIOME_REGISTRY;
    static final ResourceKey<Registry<Structure>> STRUCTURE = Registry.STRUCTURE_REGISTRY;
    static final ResourceKey<Registry<PlacedFeature>> PLACED_FEATURE = Registry.PLACED_FEATURE_REGISTRY;
    static final Codec<BitSet> FOOTPRINT = Codec.LONG_STREAM.xmap(values -> BitSet.valueOf(values.toArray()), bits -> java.util.Arrays.stream(bits.toLongArray()));

    public static ResourceLocation blockId(Block block) { return BLOCKS.getKey(block); }
    static boolean replaceable(BlockState state) { return state.getMaterial().isReplaceable(); }
    static boolean solid(BlockState state) { return state.getMaterial().isSolid(); }
    static boolean liquid(BlockState state) { return state.getMaterial().isLiquid(); }
    static Direction direction(int dx, int dz) { return Direction.fromNormal(dx, 0, dz); }
    static BlockState readBlock(ServerLevel level, CompoundTag tag) { return NbtUtils.readBlockState(tag); }
    static void writeSign(WorldGenRegion level, CompoundTag tag, List<Component> front, List<Component> back) {
        for (int i = 0; i < 4; i++) {
            Component line = i < front.size() ? front.get(i) : Component.empty();
            tag.putString("Text" + (i + 1), Component.Serializer.toJson(line));
        }
    }
}
