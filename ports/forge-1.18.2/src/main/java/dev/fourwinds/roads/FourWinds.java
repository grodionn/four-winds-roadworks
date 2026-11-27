package dev.fourwinds.roads;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(FourWinds.ID)
public final class FourWinds {
    public static final String ID = "fourwindsroadworks";
    public static ResourceLocation id(String path) { return RoadGame.id(ID, path); }

    public FourWinds() {
        var context = FMLJavaModLoadingContext.get();
        var features = DeferredRegister.create(ForgeRegistries.FEATURES, ID);
        var roads = features.register("roads", RoadFeature::new);
        var placed = com.google.common.base.Suppliers.memoize(() -> {
            var configured = new net.minecraft.world.level.levelgen.feature.ConfiguredFeature<>(
                    roads.get(), net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE);
            return net.minecraft.core.Holder.direct(new net.minecraft.world.level.levelgen.placement.PlacedFeature(
                    net.minecraft.core.Holder.direct(configured), java.util.List.of()));
        });
        features.register(context.getModEventBus());
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.world.BiomeLoadingEvent event) -> {
            var category = event.getCategory();
            if (category != net.minecraft.world.level.biome.Biome.BiomeCategory.NETHER
                    && category != net.minecraft.world.level.biome.Biome.BiomeCategory.THEEND) {
                event.getGeneration().getFeatures(net.minecraft.world.level.levelgen.GenerationStep.Decoration.TOP_LAYER_MODIFICATION)
                        .add(placed.get());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ChunkDataEvent.Load event) ->
                ChunkField.load(event.getChunk(), event.getData()));
        MinecraftForge.EVENT_BUS.addListener((ChunkDataEvent.Save event) ->
                ChunkField.save(event.getChunk(), event.getData()));
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getWorld() instanceof ServerLevel) RoadClearance.discardLegacy(event.getChunk());
        });
    }
}
