package dev.fourwinds.roads.client;

import dev.fourwinds.roads.FourWinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FourWinds.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WaypostModels {
    private static ModelResourceLocation model(ResourceLocation id) {
        return new ModelResourceLocation(id, "standalone");
    }

    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        WaypostModelIds.register(ModList.get().isLoaded("tfc"), id -> event.register(model(id)));
    }

    static BakedModel get(boolean tfc, boolean crossing) {
        return Minecraft.getInstance().getModelManager().getModel(model(WaypostModelIds.get(tfc, crossing)));
    }
}
