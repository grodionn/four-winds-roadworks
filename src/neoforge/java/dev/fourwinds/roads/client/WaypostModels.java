package dev.fourwinds.roads.client;

import dev.fourwinds.roads.FourWinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = FourWinds.ID, value = Dist.CLIENT)
public final class WaypostModels {
    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        WaypostModelIds.register(ModList.get().isLoaded("tfc"), id -> event.register(ModelResourceLocation.standalone(id)));
    }

    static BakedModel get(boolean tfc, boolean crossing) {
        var model = ModelResourceLocation.standalone(WaypostModelIds.get(tfc, crossing));
        return Minecraft.getInstance().getModelManager().getModel(model);
    }
}
