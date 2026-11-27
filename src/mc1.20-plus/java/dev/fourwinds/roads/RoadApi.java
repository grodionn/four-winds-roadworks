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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

public final class RoadApi {
    static final Registry<Block> BLOCKS = BuiltInRegistries.BLOCK;
    static final Registry<Feature<?>> FEATURES = BuiltInRegistries.FEATURE;
    static final ResourceKey<Registry<Block>> BLOCK = Registries.BLOCK;
    static final ResourceKey<Registry<Fluid>> FLUID = Registries.FLUID;
    static final ResourceKey<Registry<Biome>> BIOME = Registries.BIOME;
    static final ResourceKey<Registry<Structure>> STRUCTURE = Registries.STRUCTURE;
    static final ResourceKey<Registry<PlacedFeature>> PLACED_FEATURE = Registries.PLACED_FEATURE;
    static final Codec<BitSet> FOOTPRINT = net.minecraft.util.ExtraCodecs.BIT_SET;

    public static ResourceLocation blockId(Block block) { return BLOCKS.getKey(block); }
    static boolean replaceable(BlockState state) { return state.canBeReplaced(); }
    static boolean solid(BlockState state) { return state.isSolid(); }
    static boolean liquid(BlockState state) { return state.liquid(); }
    static Direction direction(int dx, int dz) { return Direction.fromDelta(dx, 0, dz); }
    static BlockState readBlock(ServerLevel level, CompoundTag tag) { return NbtUtils.readBlockState(level.holderLookup(BLOCK), tag); }
    static void writeSign(WorldGenRegion level, CompoundTag tag, List<Component> front, List<Component> back) {
        tag.put("front_text", RoadVersion.signText(level, front));
        tag.put("back_text", RoadVersion.signText(level, back));
    }
}
