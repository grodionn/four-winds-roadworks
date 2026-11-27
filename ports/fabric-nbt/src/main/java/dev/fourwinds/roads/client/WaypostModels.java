package dev.fourwinds.roads.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.BakedModelManagerHelper;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;

public final class WaypostModels implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ModelLoadingRegistry.INSTANCE.registerModelProvider((manager, add) -> WaypostModelIds.register(FabricLoader.getInstance().isModLoaded("tfc"), add));
    }

    static BakedModel get(boolean tfc, boolean crossing) {
        return BakedModelManagerHelper.getModel(Minecraft.getInstance().getModelManager(), WaypostModelIds.get(tfc, crossing));
    }
}
