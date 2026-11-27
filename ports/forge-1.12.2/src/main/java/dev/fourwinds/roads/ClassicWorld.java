package dev.fourwinds.roads;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import static dev.fourwinds.roads.RoadLayout.*;

final class ClassicWorld implements LegacyRoads.Land {
    private final World world;
    private final ClassicSurface survey;
    private final LegacyRoads network=new LegacyRoads();
    private final List<int[]> envelopes=new ArrayList<>();
    private final List<Point> nearby=new ArrayList<>();
    private final BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
    private int minX,minZ;

    ClassicWorld(World world,ClassicSurface survey) {
        this.world=world;this.survey=survey;
    }
    void generate(int cx,int cz) {
        // Vanilla's population window is shifted by half a chunk. Its four chunks must already exist.
        for(int dz=0;dz<2;dz++) for(int dx=0;dx<2;dx++) {
            if (world.getChunkProvider().getLoadedChunk(cx+dx,cz+dz)==null) return;
        }
        minX=cx*16+8;minZ=cz*16+8;
        structures();
        network.generate(this,minX,minZ);
    }
    private void structures() {
        nearby.clear();envelopes.clear();
        for(String type:new String[]{"Village","Temple","Mansion","Monument","Stronghold","Mineshaft"}) {
            MapGenStructureData data=(MapGenStructureData)world.getPerWorldStorage().getOrLoadData(MapGenStructureData.class,type);
            if(data==null) continue;
            NBTTagCompound all=data.getTagCompound();
            for(String key:all.getKeySet()) {
                NBTTagCompound structure=all.getCompoundTag(key);
                if (!structure.hasKey("BB") || !structure.getBoolean("Valid") && type.equals("Village")) continue;
                int[] box=structure.getIntArray("BB");
                if(box.length!=6 || box[0]>minX+128 || box[3]<minX-128 || box[2]>minZ+128 || box[5]<minZ-128) continue;
                if(type.equals("Village")) nearby.add(new Point(structure.getInteger("ChunkX")*16+8,structure.getInteger("ChunkZ")*16+8));
                NBTTagList pieces=structure.getTagList("Children",10);
                for(int i=0;i<pieces.tagCount();i++) {
                    NBTTagCompound piece=pieces.getCompoundTagAt(i);
                    int[] b=piece.getIntArray("BB");
                    if(b.length==6 && !piece.getString("id").equals("ViSR") && b[0]<minX+52 && b[3]>=minX-36 && b[2]<minZ+52 && b[5]>=minZ-36)
                        envelopes.add(b);
                }
            }
        }
    }
    @Override public List<Point> villages(int x,int z) { return nearby; }
    @Override public long seed() { return world.getSeed(); }
    @Override public int sea() { return world.getSeaLevel(); }
    @Override public int bed(int x,int z) { return survey.at(x,z)>>1; }
    @Override public boolean wet(int x,int z) { return (survey.at(x,z)&1)!=0; }
    @Override public boolean ocean(int x,int z) {
        return net.minecraftforge.common.BiomeDictionary.hasType(world.getBiomeProvider().getBiome(pos.setPos(x,sea(),z)),net.minecraftforge.common.BiomeDictionary.Type.OCEAN);
    }
    @Override public boolean occupied(int x,int y,int z) {
        for(int[] box:envelopes)
            if(x>=box[0] && x<=box[3] && z>=box[2] && z<=box[5] && y+9>=box[1] && y-3<=box[4]) return true;
        return false;
    }
    @Override public boolean firm(int x,int y,int z) {
        IBlockState state=world.getBlockState(pos.setPos(x,y,z));
        return natural(x,y,z) && state.getMaterial().blocksMovement() && state.getMaterial()!=Material.LEAVES && state.getMaterial()!=Material.WOOD;
    }
    @Override public boolean openForStop(int x,int y,int z) {
        Material m=world.getBlockState(pos.setPos(x,y,z)).getMaterial();
        return m==Material.AIR || m==Material.PLANTS || m==Material.VINE || m==Material.SNOW;
    }
    @Override public boolean natural(int x,int y,int z) {
        if(y<1 || y>=255 || x<minX || x>=minX+16 || z<minZ || z>=minZ+16) return false;
        IBlockState state=world.getBlockState(pos.setPos(x,y,z));Block b=state.getBlock();
        if(b.hasTileEntity(state)) return false;
        Material m=state.getMaterial();
        if(m==Material.AIR || m==Material.PLANTS || m==Material.VINE || m==Material.LEAVES || m==Material.SNOW || m==Material.WATER) return true;
        if(b==Blocks.STONE || b==Blocks.GRASS || b==Blocks.DIRT || b==Blocks.GRAVEL || b==Blocks.SAND || b==Blocks.SANDSTONE
                || b==Blocks.RED_SANDSTONE || b==Blocks.CLAY || b==Blocks.HARDENED_CLAY || b==Blocks.STAINED_HARDENED_CLAY
                || b==Blocks.LOG || b==Blocks.LOG2 || b==Blocks.ICE || b==Blocks.SNOW || b==Blocks.GRASS_PATH) return true;
        ResourceLocation id=b.getRegistryName();
        if(id==null || !id.getNamespace().equals("tfc")) return false;
        String p=id.getPath();
        return p.startsWith("grass/") || p.startsWith("dry_grass/") || p.startsWith("dirt/") || p.startsWith("raw/")
                || p.startsWith("gravel/") || p.startsWith("sand/") || p.startsWith("path/") || p.startsWith("plants/")
                || p.startsWith("wood/log/") || p.startsWith("wood/leaves/");
    }
    private String localRock(int x,int z) {
        int y=bed(x,z);
        for(int h=y;h>=Math.max(1,y-7);h--) {
            ResourceLocation id=world.getBlockState(pos.setPos(x,h,z)).getBlock().getRegistryName();
            if(id==null || !id.getNamespace().equals("tfc")) continue;
            String[] parts=id.getPath().split("/");
            if(parts.length==2 && (parts[0].equals("grass") || parts[0].equals("dirt") || parts[0].equals("raw") ||
                parts[0].equals("path") || parts[0].equals("gravel"))) return parts[1];
        }
        return "andesite";
    }
    private IBlockState block(LegacyRoads.Material mat,int x,int z) {
        if(mat==LegacyRoads.Material.AIR) return Blocks.AIR.getDefaultState();
        if(survey.tfc) {
            String name;
            switch(mat) {
                case STONE:name="bricks/andesite";break;
                case DRESSED:name="smooth/andesite";break;
                case COBBLE:case WORN:name="cobble/andesite";break;
                case SLIM_POST:name="wood/fence/spruce";break;
                case EARTH:name="dirt/"+localRock(x,z);break;
                case GRAVEL:name="gravel/"+localRock(x,z);break;
                case PATH:name="path/"+localRock(x,z);break;
                case FILL:name="raw/andesite";break;
                case PLANK:name="wood/planks/spruce";break;
                case BEAM:case POST:name="wood/log/spruce";break;
                case LOW_TIMBER:name="slab/wood/spruce";break;
                case LOW_STONE:name="slab/bricks/andesite";break;
                case TIMBER_STAIR:name="stairs/wood/spruce";break;
                case STAIR:name="stairs/bricks/andesite";break;
                default:throw new AssertionError(mat);
            }
            Block block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc",name));
            if(block==null || block==Blocks.AIR) throw new IllegalStateException("Missing TFC road material "+name);
            return block.getDefaultState();
        }
        switch(mat) {
            case STONE:return Blocks.STONEBRICK.getDefaultState();
            case DRESSED:return Blocks.STONE.getStateFromMeta(6);
            case WORN:return Blocks.STONEBRICK.getStateFromMeta(2);
            case COBBLE:return Blocks.COBBLESTONE.getDefaultState();
            case SLIM_POST:return Blocks.SPRUCE_FENCE.getDefaultState();
            case EARTH:return Blocks.DIRT.getStateFromMeta(1);
            case GRAVEL:return Blocks.GRAVEL.getDefaultState();
            case PATH:return Blocks.GRASS_PATH.getDefaultState();
            case FILL:return Blocks.STONE.getDefaultState();
            case PLANK:return Blocks.PLANKS.getStateFromMeta(1);
            case POST:case BEAM:return Blocks.LOG.getStateFromMeta(1);
            case LOW_TIMBER:return Blocks.WOODEN_SLAB.getStateFromMeta(1);
            case LOW_STONE:return Blocks.STONE_SLAB.getStateFromMeta(5);
            case TIMBER_STAIR:return Blocks.SPRUCE_STAIRS.getDefaultState();
            case STAIR:return Blocks.STONE_BRICK_STAIRS.getDefaultState();
            default:throw new AssertionError(mat);
        }
    }
    @Override public void put(int x,int y,int z,LegacyRoads.Material mat,int dx,int dz) {
        IBlockState state=block(mat,x,z);
        if(state.getBlock() instanceof BlockStairs) state=state.withProperty(BlockStairs.FACING,
                dx>0?EnumFacing.EAST:dx<0?EnumFacing.WEST:dz>0?EnumFacing.SOUTH:EnumFacing.NORTH);
        if(mat==LegacyRoads.Material.BEAM && state.getPropertyKeys().contains(net.minecraft.block.BlockLog.LOG_AXIS))
            state=state.withProperty(net.minecraft.block.BlockLog.LOG_AXIS,dx==0?net.minecraft.block.BlockLog.EnumAxis.X:net.minecraft.block.BlockLog.EnumAxis.Z);
        world.setBlockState(pos.setPos(x,y,z),state,18);
    }
    @Override public void lighthouse(PierLayout p, int tileX, int tileZ) {
        net.minecraft.util.Rotation rotation = p.eastWest()
                ? (p.outward() > 0 ? net.minecraft.util.Rotation.NONE : net.minecraft.util.Rotation.CLOCKWISE_180)
                : (p.outward() > 0 ? net.minecraft.util.Rotation.CLOCKWISE_90 : net.minecraft.util.Rotation.COUNTERCLOCKWISE_90);
        EnumFacing side = p.eastWest() ? (p.side() > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH)
                : (p.side() > 0 ? EnumFacing.EAST : EnumFacing.WEST);
        net.minecraft.util.Mirror mirror = rotation.rotate(EnumFacing.SOUTH) == side
                ? net.minecraft.util.Mirror.NONE : net.minecraft.util.Mirror.LEFT_RIGHT;
        net.minecraft.world.gen.structure.template.PlacementSettings settings =
                new net.minecraft.world.gen.structure.template.PlacementSettings().setMirror(mirror).setRotation(rotation)
                        .setIgnoreEntities(true).setBoundingBox(new net.minecraft.world.gen.structure.StructureBoundingBox(
                                tileX,1,tileZ,tileX+15,254,tileZ+15));
        Point start=p.point(0,PierLayout.MIN_SIDE);
        String template=survey.tfc ? "tfc/coastal_lighthouse" : "coastal_lighthouse";
        net.minecraft.world.WorldServer server=(net.minecraft.world.WorldServer)world;
        server.getStructureTemplateManager().getTemplate(server.getMinecraftServer(),
                new ResourceLocation(ClassicRoadMod.ID,template)).addBlocksToWorld(world,
                        new BlockPos(start.x(),p.deck()+1,start.z()),settings,18);
    }
    @Override public void waypost(int x,int y,int z,Spur destination) {
        ClassicWaypost.place(world,new BlockPos(x,y,z),survey.tfc,destination);
    }
}
