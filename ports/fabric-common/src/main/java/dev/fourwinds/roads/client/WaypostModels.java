package dev.fourwinds.roads.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;

public final class WaypostModels implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ModelLoadingPlugin.register(ctx -> WaypostModelIds.register(FabricLoader.getInstance().isModLoaded("tfc"), id -> ctx.addModels(id)));
    }

    static BakedModel get(boolean tfc, boolean crossing) {
        return ((FabricBakedModelManager)Minecraft.getInstance().getModelManager()).getModel(WaypostModelIds.get(tfc, crossing));
    }
}
