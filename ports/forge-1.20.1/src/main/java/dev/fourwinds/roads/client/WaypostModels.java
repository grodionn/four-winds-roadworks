package dev.fourwinds.roads.client;

import dev.fourwinds.roads.FourWinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FourWinds.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WaypostModels {
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        WaypostModelIds.register(ModList.get().isLoaded("tfc"), event::register);
    }

    static BakedModel get(boolean tfc, boolean crossing) {
        return Minecraft.getInstance().getModelManager().getModel(WaypostModelIds.get(tfc, crossing));
    }
}
