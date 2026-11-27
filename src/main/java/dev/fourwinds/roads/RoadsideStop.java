package dev.fourwinds.roads;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import static dev.fourwinds.roads.RoadLayout.*;

/** One candidate between two pairs of crossroads. Its whole footprint belongs to a single chunk. */
record RoadsideStop(Point front,int y,boolean eastWest,int side,boolean shelter,String quarry) {
    private Point point(int u,int v) {
        return eastWest?new Point(front.x()+u,front.z()+v*side):new Point(front.x()+v*side,front.z()+u);
    }
    private int half() { return shelter?3:1; }
    private int back() { return shelter?5:2; }
    static RoadsideStop find(RoadTerrain terrain,List<Path> paths) {
        var chunk=terrain.level.getCenter();
        for (Path path:paths) {
            if (!(path instanceof Road road)) continue;
            int mid=road.eastWest()?chunk.getMiddleBlockX():chunk.getMiddleBlockZ();
            int group=Math.floorDiv(mid,2*SPACING),t=stopStation(road,group);
            if ((mid>>4)!=(t>>4)) continue;
            var site=survey(terrain,road,t,group);
            if (site!=null && (site.front.x()>>4)==chunk.x && (site.front.z()>>4)==chunk.z && site.suitable(terrain,paths)) return site;
        }
        return null;
    }
    private static RoadsideStop survey(RoadTerrain terrain,Road road,int station,int group) {
        var channel=terrain.bridges.along(road);
        long salt=road.seed()^(road.eastWest()?92867L:22993L);
        int preferred=roll(salt,road.index(),group,2)==0?-1:1;
        boolean shelter=roll(salt+31,road.index(),group,5)<2;
        // All neighbouring chunks select the same clearing from the pre-decoration survey.
        for (int delta:new int[]{0,-2,2}) {
            int t=station+delta;
            if (channel.built(t)!=null || channel.surveyedWet(t)) continue;
            int roadY=RoadFeature.surface(terrain,road,channel.point(t,0));
            for (int side:new int[]{preferred,-preferred}) {
                Point front=channel.point(t,edge(road.width(),side)+2*side);
                int ground=ShoreTerrain.surface(terrain.level,front);
                if (ground==Integer.MIN_VALUE || (ground&1)!=0 || Math.abs((ground>>1)-roadY)>1) continue;
                int y=ground>>1;
                if (y+7>=terrain.level.getMaxBuildHeight()) continue;
                var site=new RoadsideStop(front,y,road.eastWest(),side,shelter,ShoreTerrain.quarry(terrain.level,front));
                Point a=site.point(-site.half(),0),b=site.point(site.half(),site.back());
                if ((a.x()>>4)!=(b.x()>>4) || (a.z()>>4)!=(b.z()>>4)) continue;
                if (site.levelGround(terrain)) return site;
            }
        }
        return null;
    }
    private boolean levelGround(RoadTerrain terrain) {
        if (y<=terrain.level.getSeaLevel()+4) {
            for (Direction dir:Direction.Plane.HORIZONTAL) {
                var p=new Point(front.x()+8*dir.getStepX(),front.z()+8*dir.getStepZ());
                int s=ShoreTerrain.surface(terrain.level,p);
                if (s==Integer.MIN_VALUE || (s&1)!=0) return false;
            }
        }
        for (int u=-half();u<=half();u++) for (int v=0;v<=back();v++) {
            Point p=point(u,v);
            int ground=ShoreTerrain.surface(terrain.level,p);
            if (ground==Integer.MIN_VALUE || (ground&1)!=0 || Math.abs((ground>>1)-y)>1) return false;
        }
        return true;
    }
    private boolean suitable(RoadTerrain terrain,List<Path> paths) {
        for (int u=-half();u<=half();u++) for (int v=0;v<=back();v++) {
            Point p=point(u,v);
            if (terrain.protectedAt(p,y-2,y+6,false)) return false;
            for (Path path:paths) {
                int t=p.along(path.eastWest());
                if (!path.contains(t)) continue;
                if (path instanceof Spur spur && spur.sign().distanceTo(p)<=3) return false;
                if (Math.abs(path.center(t)-p.across(path.eastWest()))<=BridgeAlignment.MAX_SHIFT+6 &&
                        depth(path.width(),p.across(path.eastWest())-terrain.bridges.along(path).center(t))>=0) return false;
            }
        }
        return true;
    }
    CompoundTag save() {
        var tag=new CompoundTag();
        tag.putInt("x",front.x()); tag.putInt("z",front.z()); tag.putInt("y",y);
        tag.putBoolean("ew",eastWest); tag.putInt("side",side); tag.putBoolean("shelter",shelter); tag.putString("quarry",quarry);
        return tag;
    }
    static RoadsideStop load(CompoundTag tag) {
        return new RoadsideStop(new Point(tag.getInt("x"),tag.getInt("z")),tag.getInt("y"),tag.getBoolean("ew"),
                tag.getInt("side"),tag.getBoolean("shelter"),tag.getString("quarry"));
    }
    private static boolean softCover(BlockState state) {
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) return false;
        var id=RoadApi.blockId(state.getBlock());
        return state.isAir() || RoadApi.replaceable(state) || state.getBlock() instanceof BushBlock
                || id.getNamespace().equals("tfc") && (id.getPath().startsWith("groundcover/") || id.getPath().startsWith("plant/")
                || id.getPath().startsWith("rock/loose/") || id.getPath().startsWith("rock/mossy_loose/") || id.getPath().startsWith("wood/twig/"));
    }
    private int clearance(int u,int v) {
        if (shelter) return v==0?2:v<=2?5:4;
        return u==0 && v==1?3:2;
    }
    private int ground(ServerLevel level,ChunkAccess chunk,Point p,int headroom) {
        if ((p.x()>>4)!=chunk.getPos().x || (p.z()>>4)!=chunk.getPos().z || CoastalPier.reserved(chunk,p.x(),p.z())) return Integer.MIN_VALUE;
        var pos=new BlockPos.MutableBlockPos(p.x(),y,p.z());
        int top=y+headroom;
        for (int h=top;h>=y-1;h--) {
            var state=chunk.getBlockState(pos.setY(h));
            if (softCover(state)) continue;
            if (h>y+1 || state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.is(RoadTerrain.ICE) || state.is(BlockTags.ICE)
                    || !state.is(RoadTerrain.GROUND) || !state.isFaceSturdy(level,pos,Direction.UP)) break;
            var below=pos.below();
            var footing=chunk.getBlockState(below);
            if (!footing.is(BlockTags.ICE) && footing.isFaceSturdy(level,below,Direction.UP)) return h;
            break;
        }
        return Integer.MIN_VALUE;
    }
    boolean finish(ServerLevel level,ChunkAccess chunk) {
        int half=half(),back=back();
        // The last neighbouring tree has already grown. Validate before the first write.
        for (int u=-half;u<=half;u++) for (int v=0;v<=back;v++) {
            int h=ground(level,chunk,point(u,v),clearance(u,v));
            if (h==Integer.MIN_VALUE) return false;
        }
        var stone=RoadMaterials.construction(RoadMaterials.isTfc(level),quarry);
        for (int u=-half;u<=half;u++) for (int v=0;v<=back;v++) {
            boolean pad=shelter?Math.abs(u)<=2 && v>=1 && v<=4: v>=1 || u==0;
            if (!pad && !(shelter && v==0 && Math.abs(u)<=1)) continue;
            Point p=point(u,v);
            for (int i=y+1;i<=y+clearance(u,v);i++) {
                var pos=new BlockPos(p.x(),i,p.z());
                if (!chunk.getBlockState(pos).isAir()) chunk.setBlockState(pos,Blocks.AIR.defaultBlockState(),false);
            }
            boolean footing=shelter && Math.abs(u)==2 && (v==2 || v==4);
            put(chunk,stone,u,v,0,(footing?Blocks.COBBLESTONE:Blocks.GRAVEL).defaultBlockState());
        }
        Direction away=eastWest?(side>0?Direction.SOUTH:Direction.NORTH):(side>0?Direction.EAST:Direction.WEST);
        if (shelter) {
            var length=eastWest?Direction.Axis.X:Direction.Axis.Z;
            for (int u=-3;u<=3;u++) {
                for (int v=1;v<=5;v++) {
                    var roof=v==3?Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,away.getOpposite()):
                            Blocks.SPRUCE_SLAB.defaultBlockState();
                    put(chunk,stone,u,v,v<=2?5:4,roof);
                }
                if (Math.abs(u)>2) continue;
                for (int v:new int[]{2,4}) {
                    int beam=v==2?4:3;
                    if (Math.abs(u)==2) for (int h=1;h<beam;h++) put(chunk,stone,u,v,h,TunnelWorks.timber(Direction.Axis.Y));
                    put(chunk,stone,u,v,beam,TunnelWorks.timber(length));
                }
                if (Math.abs(u)<2) {
                    for (int h=1;h<=2;h++) put(chunk,stone,u,4,h,TunnelWorks.timber(length));
                    put(chunk,stone,u,3,1,Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,away));
                }
            }
        } else {
            put(chunk,stone,0,1,1,Blocks.STONE_BRICKS.defaultBlockState());
            put(chunk,stone,0,1,2,Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            put(chunk,stone,0,1,3,Blocks.STONE_BRICK_SLAB.defaultBlockState());
            put(chunk,stone,0,2,1,Blocks.COBBLESTONE.defaultBlockState());
            put(chunk,stone,1,2,1,Blocks.COBBLESTONE_SLAB.defaultBlockState());
        }
        return true;
    }
    private void put(ChunkAccess chunk,RoadMaterials stone,int u,int v,int h,BlockState state) {
        Point p=point(u,v);
        chunk.setBlockState(new BlockPos(p.x(),y+h,p.z()),stone.state(state),false);
    }
}
