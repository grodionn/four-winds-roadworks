package dev.fourwinds.roads;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public final class RoadApi {
    public static ResourceLocation blockId(Block block) { return Registry.BLOCK.getKey(block); }
}
