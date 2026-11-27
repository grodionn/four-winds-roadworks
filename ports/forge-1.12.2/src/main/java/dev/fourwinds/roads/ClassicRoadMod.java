package dev.fourwinds.roads;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=ClassicRoadMod.ID, name="Grodion's Four Winds Roadworks", acceptedMinecraftVersions="[1.12.2]")
public final class ClassicRoadMod implements IWorldGenerator {
    public static final String ID="fourwindsroadworks";
    private final Map<World,ClassicWorld> worlds=new HashMap<>();

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        GameRegistry.registerWorldGenerator(this,Integer.MAX_VALUE);
        MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) { worlds.remove(event.getWorld()); }
    @Override public void generate(Random random,int cx,int cz,World world,IChunkGenerator generator,IChunkProvider provider) {
        if (world.isRemote || world.provider.getDimension()!=0) return;
        if (!worlds.containsKey(world)) {
            ClassicSurface survey=ClassicSurface.forWorld(world,generator);
            worlds.put(world,survey==null?null:new ClassicWorld(world,survey));
        }
        ClassicWorld work=worlds.get(world);
        if (work!=null) work.generate(cx,cz);
    }
}
