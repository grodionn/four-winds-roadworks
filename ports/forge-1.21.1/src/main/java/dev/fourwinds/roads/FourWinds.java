package dev.fourwinds.roads;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(FourWinds.ID)
public final class FourWinds {
    public static final String ID = "fourwindsroadworks";
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    public FourWinds(FMLJavaModLoadingContext context) {
        var features = DeferredRegister.create(ForgeRegistries.FEATURES, ID);
        features.register("roads", RoadFeature::new);
        features.register(context.getModEventBus());
        MinecraftForge.EVENT_BUS.addListener((ChunkDataEvent.Load event) ->
                ChunkField.load(event.getChunk(), event.getData()));
        MinecraftForge.EVENT_BUS.addListener((ChunkDataEvent.Save event) ->
                ChunkField.save(event.getChunk(), event.getData()));
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel) RoadClearance.discardLegacy(event.getChunk());
        });
    }
}
