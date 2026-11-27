package dev.fourwinds.roads;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ChunkGenerator;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.chunk.*;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.feature.FeatureState;
import static dev.fourwinds.roads.RoadLayout.*;

public final class FabricClassicWorld implements LegacyRoads.Land {
    public interface Owner { void fourwinds$populate(ChunkGenerator generator, int cx, int cz); }
    private final World world;
    private final ChunkGenerator probe;
    private final LegacyRoads roads = new LegacyRoads();
    private final List<int[]> pieces = new ArrayList<>();
    private final List<Point> villages = new ArrayList<>();
    private final Map<Long,short[]> heights = new LinkedHashMap<Long,short[]>(128,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,short[]> entry) { return size()>2048; }
    };
    private Structure tower;
    private int minX, minZ;

    public static FabricClassicWorld create(World world, ChunkGenerator generator) {
        if (world.dimension.getDimensionType()!=DimensionType.OVERWORLD) return null;
        String options=world.getLevelProperties().getGeneratorOptions();
        if (generator.getClass()==SurfaceChunkGenerator.class)
            return new FabricClassicWorld(world,new SurfaceChunkGenerator(world,world.getSeed(),false,options));
        if (generator.getClass()==FlatChunkGenerator.class)
            return new FabricClassicWorld(world,new FlatChunkGenerator(world,world.getSeed(),false,options));
        return null;
    }
    private FabricClassicWorld(World world, ChunkGenerator probe) { this.world=world;this.probe=probe; }

    public void generate(int cx,int cz) {
        ServerChunkProvider provider=(ServerChunkProvider)world.getChunkProvider();
        for (int dx=0;dx<=1;dx++) for(int dz=0;dz<=1;dz++) if(provider.getLoadedChunk(cx+dx,cz+dz)==null) return;
        minX=cx*16+8;minZ=cz*16+8;
        pieces.clear();villages.clear();
        for (String type:new String[]{"Village","Temple","Mansion","Monument","Stronghold","Mineshaft"}) {
            FeatureState saved=(FeatureState)world.getOrCreateState(FeatureState.class,type);
            if (saved==null) continue;
            NbtCompound starts=saved.getFeatures();
            for(String id:starts.getKeys()) {
                NbtCompound start=starts.getCompound(id);
                int[] bounds=start.getIntArray("BB");
                if(bounds.length!=6 || bounds[0]>minX+128 || bounds[3]<minX-128 || bounds[2]>minZ+128 || bounds[5]<minZ-128) continue;
                if(type.equals("Village")) {
                    if(!start.getBoolean("Valid")) continue;
                    villages.add(new Point(start.getInt("ChunkX")*16+8,start.getInt("ChunkZ")*16+8));
                }
                NbtList children=start.getList("Children",10);
                for(int i=0;i<children.size();i++) {
                    NbtCompound child=children.getCompound(i);
                    int[] box=child.getIntArray("BB");
                    if(box.length==6 && !child.getString("id").equals("ViSR")) pieces.add(box);
                }
            }
        }
        roads.generate(this,minX,minZ);
    }

    private int surface(int x,int z) {
        int cx=x>>4,cz=z>>4;
        long key=((long)cx<<32)|(cz&0xffffffffL);
        return heights.computeIfAbsent(key,k -> sample(cx,cz))[(x&15)+16*(z&15)];
    }
    private short[] sample(int cx,int cz) {
        ChunkBlockStateStorage primer=null;
        Chunk flat=null;
        if(probe instanceof SurfaceChunkGenerator) {
            primer=new ChunkBlockStateStorage();
            ((SurfaceChunkGenerator)probe).method_9194(cx,cz,primer);
        } else flat=probe.generate(cx,cz);
        short[] data=new short[256];
        for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            boolean water=false;int y=255;
            for(;y>0;y--) {
                Material mat=(primer==null?flat.getBlockState(x,y,z):primer.get(x,y,z)).getMaterial();
                if (mat.isFluid() || mat==Material.ICE) water=true;
                else if (mat.blocksMovement() && mat!=Material.FOLIAGE) break;
            }
            data[x+16*z]=(short)(2*y+(water?1:0));
        }
        return data;
    }
    @Override public long seed() { return world.getSeed(); }
    @Override public int sea() { return world.getSeaLevel(); }
    @Override public int bed(int x,int z) { return surface(x,z)>>1; }
    @Override public boolean wet(int x,int z) { return (surface(x,z)&1)!=0; }
    @Override public boolean ocean(int x,int z) {
        Biome b=world.method_3726().method_11535(new BlockPos(x,sea(),z));
        return b==Biomes.OCEAN || b==Biomes.DEEP_OCEAN || b==Biomes.FROZEN_OCEAN;
    }
    @Override public List<Point> villages(int x,int z) { return villages; }
    @Override public boolean occupied(int x,int y,int z) {
        for(int[] b:pieces) if(x>=b[0] && x<=b[3] && z>=b[2] && z<=b[5] && y+9>=b[1] && y-3<=b[4]) return true;
        return false;
    }
    @Override public boolean firm(int x,int y,int z) {
        BlockState state=world.getBlockState(new BlockPos(x,y,z));
        return natural(x,y,z) && state.getMaterial().blocksMovement() && state.getMaterial()!=Material.FOLIAGE
                && state.getBlock()!=Blocks.LOG && state.getBlock()!=Blocks.LOG2;
    }
    @Override public boolean openForStop(int x,int y,int z) {
        Material m=world.getBlockState(new BlockPos(x,y,z)).getMaterial();
        return m==Material.AIR || m==Material.PLANT || m.isReplaceable() && !m.isFluid() && m!=Material.FOLIAGE;
    }
    @Override public boolean natural(int x,int y,int z) {
        if (x<minX || x>=minX+16 || z<minZ || z>=minZ+16 || y<1 || y>=255) return false;
        BlockState s=world.getBlockState(new BlockPos(x,y,z));Block b=s.getBlock();Material m=s.getMaterial();
        return m==Material.AIR || m.isReplaceable() || m==Material.FOLIAGE || m==Material.PLANT || m==Material.WATER
                || b==Blocks.STONE || b==Blocks.GRASS || b==Blocks.DIRT || b==Blocks.GRAVEL || b==Blocks.GRASS_PATH
                || b==Blocks.TERRACOTTA || b==Blocks.STAINED_TERRACOTTA
                || b==Blocks.SAND || b==Blocks.SANDSTONE || b==Blocks.RED_SANDSTONE || b==Blocks.CLAY
                || b==Blocks.LOG || b==Blocks.LOG2 || b==Blocks.ICE || b==Blocks.SNOW;
    }
    @Override public void put(int x,int y,int z,LegacyRoads.Material mat,int dx,int dz) {
        Block block;int meta=0;
        switch(mat) {
            case AIR:block=Blocks.AIR;break;
            case STONE:block=Blocks.STONE_BRICKS;break;
            case DRESSED:block=Blocks.STONE;meta=6;break;
            case WORN:block=Blocks.STONE_BRICKS;meta=2;break;
            case COBBLE:block=Blocks.COBBLESTONE;break;
            case SLIM_POST:block=Blocks.SPRUCE_FENCE;break;
            case EARTH:block=Blocks.DIRT;meta=1;break;
            case GRAVEL:block=Blocks.GRAVEL;break;
            case PATH:block=Blocks.GRASS_PATH;break;
            case FILL:block=Blocks.STONE;break;
            case PLANK:block=Blocks.PLANKS;meta=1;break;
            case POST:block=Blocks.LOG;meta=1;break;
            case BEAM:block=Blocks.LOG;meta=dx==0?5:9;break;
            case LOW_TIMBER:block=Blocks.WOODEN_SLAB;meta=1;break;
            case LOW_STONE:block=Blocks.STONE_SLAB;meta=5;break;
            case STAIR:block=Blocks.STONE_BRICK_STAIRS;break;
            case TIMBER_STAIR:block=Blocks.SPRUCE_STAIRS;break;
            default:throw new AssertionError(mat);
        }
        BlockState state=block.stateFromData(meta);
        if(block instanceof StairsBlock) state=state.with(StairsBlock.FACING,dx>0?Direction.EAST:dx<0?Direction.WEST:dz>0?Direction.SOUTH:Direction.NORTH);
        world.setBlockState(new BlockPos(x,y,z),state,18);
    }
    @Override public void waypost(int x,int y,int z,Spur dest) {
        world.setBlockState(new BlockPos(x,y,z),Blocks.OAK_FENCE.getDefaultState(),18);
        BlockPos pos=new BlockPos(x,y+1,z);
        world.setBlockState(pos,Blocks.STANDING_SIGN.getDefaultState(),18);
        BlockEntity tile=world.getBlockEntity(pos);
        if(!(tile instanceof SignBlockEntity)) return;
        SignBlockEntity sign=(SignBlockEntity)tile;
        int dx=dest==null?0:dest.village().x()-x, dz=dest==null?0:dest.village().z()-z;
        String to=Math.abs(dx)>=Math.abs(dz)?(dx>=0?"east":"west"):(dz>=0?"south":"north");
        String prefix="sign.fourwindsroadworks.";
        sign.text[0]=new TranslatableText(prefix+(dest==null?"crossing":"village"));
        sign.text[1]=new TranslatableText(prefix+(dest==null?"north":to));
        sign.text[2]=dest==null?new TranslatableText(prefix+"west_east"):new TranslatableText(prefix+"distance",Math.round(Math.hypot(dx,dz)));
        sign.text[3]=dest==null?new TranslatableText(prefix+"south"):new LiteralText("");
        sign.markDirty();
    }
    @Override public void lighthouse(PierLayout p,int tileX,int tileZ) {
        if (tower==null) {
            try (InputStream input=FabricClassicWorld.class.getResourceAsStream("/assets/fourwindsroadworks/structures/coastal_lighthouse.nbt")) {
                if(input==null) throw new IllegalStateException("Missing coastal lighthouse drawing");
                tower=new Structure();
                tower.method_11897(NbtIo.readCompressed(input));
            } catch(IOException e) { throw new IllegalStateException("Cannot read coastal lighthouse",e); }
        }
        BlockRotation rot=p.eastWest()?(p.outward()>0?BlockRotation.NONE:BlockRotation.CLOCKWISE_180)
                :(p.outward()>0?BlockRotation.CLOCKWISE_90:BlockRotation.COUNTERCLOCKWISE_90);
        Direction side=p.eastWest()?(p.side()>0?Direction.SOUTH:Direction.NORTH):(p.side()>0?Direction.EAST:Direction.WEST);
        BlockMirror mirror=rot.rotate(Direction.SOUTH)==side?BlockMirror.NONE:BlockMirror.LEFT_RIGHT;
        StructurePlacementData settings=new StructurePlacementData().method_11867(mirror).method_11868(rot)
                .method_11869(new BlockBox(tileX,1,tileZ,tileX+15,254,tileZ+15));
        Point origin=p.point(0,PierLayout.MIN_SIDE);
        tower.method_13391(world,new BlockPos(origin.x(),p.deck()+1,origin.z()),settings,18);
    }
}
