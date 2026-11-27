package dev.fourwinds.roads;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;

public final class FourWinds implements ModInitializer {
    public static final String ID = "fourwindsroadworks";
    public static ResourceLocation id(String path) { return ResourceLocation.tryBuild(ID, path); }

    @Override public void onInitialize() {
        RoadData.ALL.forEach(ChunkField::register);
        Registry.register(BuiltInRegistries.FEATURE, id("roads"), new RoadFeature());
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
                .and(ctx -> !ctx.getBiomeKey().location().getNamespace().equals("tfc")),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION, ResourceKey.create(Registries.PLACED_FEATURE, id("roads")));
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> RoadClearance.discardLegacy(chunk));
    }
}
