package dev.fourwinds.roads.client;

import dev.fourwinds.roads.FourWinds;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

final class WaypostModelIds {
    private static final ResourceLocation CROSSING = FourWinds.id("block/crossroads_waypost");
    private static final ResourceLocation VILLAGE = FourWinds.id("block/village_waypost");
    private static final ResourceLocation TFC_CROSSING = FourWinds.id("block/tfc/crossroads_waypost");
    private static final ResourceLocation TFC_VILLAGE = FourWinds.id("block/tfc/village_waypost");

    static ResourceLocation get(boolean tfc, boolean crossing) {
        if (tfc) return crossing ? TFC_CROSSING : TFC_VILLAGE;
        return crossing ? CROSSING : VILLAGE;
    }

    static void register(boolean tfc, Consumer<ResourceLocation> add) {
        add.accept(CROSSING);
        add.accept(VILLAGE);
        if (tfc) {
            add.accept(TFC_CROSSING);
            add.accept(TFC_VILLAGE);
        }
    }
}
