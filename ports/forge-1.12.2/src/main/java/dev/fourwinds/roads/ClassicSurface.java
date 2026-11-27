package dev.fourwinds.roads;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.ChunkGeneratorFlat;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.gen.IChunkGenerator;

/** Samples a separate terrain generator. Surveying a far abutment never loads or decorates a live chunk. */
final class ClassicSurface {
    private final IChunkGenerator probe;
    final boolean tfc;
    private final Map<Long,short[]> columns = new LinkedHashMap<Long,short[]>(128,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,short[]> eldest) { return size()>2048; }
    };

    static ClassicSurface forWorld(World world, IChunkGenerator actual) {
        String options=world.getWorldInfo().getGeneratorOptions();
        if (actual.getClass()==ChunkGeneratorOverworld.class)
            return new ClassicSurface(new ChunkGeneratorOverworld(world,world.getSeed(),false,options),false);
        if (actual.getClass()==ChunkGeneratorFlat.class)
            return new ClassicSurface(new ChunkGeneratorFlat(world,world.getSeed(),false,options),false);
        if (actual.getClass().getName().equals("net.dries007.tfc.world.classic.ChunkGenTFC"))
            return new ClassicSurface(world.getWorldType().getChunkGenerator(world,options),true);
        return null;
    }
    private ClassicSurface(IChunkGenerator probe,boolean tfc) { this.probe=probe;this.tfc=tfc; }
    int at(int x,int z) {
        int cx=x>>4,cz=z>>4;
        long k=((long)cx<<32)|(cz&0xffffffffL);
        return columns.computeIfAbsent(k,key->survey(cx,cz))[(z&15)*16+(x&15)];
    }
    private short[] survey(int cx,int cz) {
        short[] bed=new short[256];
        ChunkPrimer primer=null;
        Chunk chunk=null;
        if (probe instanceof ChunkGeneratorOverworld) {
            primer=new ChunkPrimer();
            ((ChunkGeneratorOverworld)probe).setBlocksInChunk(cx,cz,primer);
        } else chunk=probe.generateChunk(cx,cz);
        for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            int y=255;boolean water=false;
            for(;y>0;y--) {
                Material mat=(primer==null?chunk.getBlockState(x,y,z):primer.getBlockState(x,y,z)).getMaterial();
                if (mat.isLiquid() || mat==Material.ICE) water=true;
                else if (mat.blocksMovement() && mat!=Material.LEAVES) break;
            }
            bed[z*16+x]=(short)(y*2+(water?1:0));
        }
        return bed;
    }
}
