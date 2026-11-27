package dev.fourwinds.roads;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import static dev.fourwinds.roads.RoadLayout.*;

/** The tide line and bed as carved, before bridges, tide pools or trees can change a neighbouring column. */
public final class ShoreTerrain {
    // An approach can see a nearby crossing, its far rock seat and the dry tie to the next river.
    // Read those same pre-decoration columns on either side of a chunk boundary.
    public static final int SURVEY_RADIUS=12;
    private static final int BED_OFFSET=21, WET_AT_SEA=32, WET_BELOW_SEA=64;

    public static void capture(ServerLevel level,ChunkAccess chunk) {
        if (level.dimension()!=Level.OVERWORLD) return;
        var cp=chunk.getPos();
        Point middle=new Point(cp.getMiddleBlockX(),cp.getMiddleBlockZ());
        if (!nearRoad(level.getSeed(),middle,true) && !nearRoad(level.getSeed(),middle,false)) return;
        int sea=level.getSeaLevel();
        var pos=new BlockPos.MutableBlockPos();
        var columns=new byte[256];
        var surface=new int[128];
        for (int i=0;i<columns.length;i++) {
            int x=cp.getBlockX(i&15),z=cp.getBlockZ(i>>4);
            int bed=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z);
            int value=RoadLayout.clamp(bed-sea+BED_OFFSET,0,31);
            if (wet(chunk,pos.set(x,sea,z))) value|=WET_AT_SEA;
            if (wet(chunk,pos.setY(sea-1))) value|=WET_BELOW_SEA;
            columns[i]=(byte)value;
            int top=chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
            var state=chunk.getBlockState(pos.set(x,top,z));
            // Inland glaciers have bedrock or gravel beneath the ice. A frozen river still has water.
            while (state.is(RoadTerrain.ICE) && pos.getY()>chunk.getMinBuildHeight())
                state=chunk.getBlockState(pos.move(net.minecraft.core.Direction.DOWN));
            int surveyed=top*2+(state.getFluidState().is(RoadTerrain.WATER)?1:0);
            surface[i>>1]|=(surveyed&0xffff)<<((i&1)*16);
        }
        var tag=new CompoundTag();
        tag.putByteArray("columns",columns);
        RoadData.COLUMNS.set(chunk,tag);
        // A later neighbour must see the same original grade, even after this chunk has been played in.
        var survey=new CompoundTag();
        survey.putIntArray("heights",surface);
        if (RoadMaterials.isTfc(level)) {
            int x=cp.getMiddleBlockX(),z=cp.getMiddleBlockZ();
            int top=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z);
            for (int y=top;y>=Math.max(chunk.getMinBuildHeight(),top-95);y--) {
                String rock=RoadMaterials.quarry(chunk.getBlockState(pos.set(x,y,z)));
                if (rock!=null) { survey.putString("quarry",rock); break; }
            }
        }
        RoadData.SURFACE.set(chunk,survey);
    }

    private static boolean nearRoad(long seed,Point middle,boolean eastWest) {
        var road=band(seed,eastWest,middle);
        return Math.abs(road.index()*SPACING-middle.across(eastWest))<=64+PierLayout.LENGTH+PierLayout.MAX_SIDE+16;
    }

    static int surface(WorldGenRegion region,Point p) {
        var center=region.getCenter();int x=p.x()>>4,z=p.z()>>4;
        if (Math.abs(x-center.x)>SURVEY_RADIUS || Math.abs(z-center.z)>SURVEY_RADIUS) return Integer.MIN_VALUE;
        var chunk=region.getChunk(x,z,RoadVersion.CARVERS);
        if (!RoadData.SURFACE.has(chunk)) return Integer.MIN_VALUE;
        int[] heights=RoadData.SURFACE.get(chunk).getIntArray("heights");
        int i=(p.x()&15)|((p.z()&15)<<4);
        return heights.length==128?(short)(heights[i>>1]>>>((i&1)*16)):Integer.MIN_VALUE;
    }

    static String quarry(WorldGenRegion region,Point p) {
        var cp=region.getCenter();
        int x=p.x()>>4,z=p.z()>>4;
        if (Math.abs(x-cp.x)<=SURVEY_RADIUS && Math.abs(z-cp.z)<=SURVEY_RADIUS) {
            var chunk=region.getChunk(x,z,RoadVersion.CARVERS);
            if (RoadData.SURFACE.has(chunk)) {
                String found=RoadData.SURFACE.get(chunk).getString("quarry");
                if (!found.isEmpty()) return found;
            }
        }
        return "andesite";
    }

    private static boolean wet(ChunkAccess chunk,BlockPos pos) {
        var state=chunk.getBlockState(pos);
        return state.getFluidState().is(RoadTerrain.WATER) || state.is(RoadTerrain.ICE);
    }

    static int column(RoadTerrain terrain,Point p) {
        var chunk=terrain.level.getChunk(p.x()>>4,p.z()>>4);
        if (RoadData.COLUMNS.has(chunk)) {
            byte[] columns=RoadData.COLUMNS.get(chunk).getByteArray("columns");
            if (columns.length==256) return columns[(p.x()&15)|((p.z()&15)<<4)];
        }
        // Older partial chunks can already be past CARVERS when the mod is installed.
        int sea=terrain.level.getSeaLevel(),bed=terrain.seabed(p);
        return RoadLayout.clamp(bed-sea+BED_OFFSET,0,31) | (bed<sea ? WET_AT_SEA : 0) | (bed<sea-1 ? WET_BELOW_SEA : 0);
    }

    static int bed(int column,int sea) { return sea+(column&31)-BED_OFFSET; }
    static boolean wet(int column,int sea,int water) {
        return (column & (water==sea ? WET_AT_SEA : WET_BELOW_SEA))!=0;
    }
}
