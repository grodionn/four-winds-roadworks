package dev.fourwinds.roads;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

@Mod(FourWinds.ID)
public final class FourWinds {
    public static final String ID = "fourwindsroadworks";
    public static ResourceLocation id(String path) { return new ResourceLocation(ID, path); }
}
