package dev.fourwinds.roads;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;

/** Reconcile neighbouring decorators while the chunk is still exclusively owned by world generation. */
public final class RoadClearance {
    private final ArrayList<BlockState> states=new ArrayList<>();
    private final IntArrayList lanes=new IntArrayList();
    private final IntArrayList trails=new IntArrayList();
    private final IntArrayList soilFinishes=new IntArrayList();
    private final IntArrayList signArms=new IntArrayList();
    private final it.unimi.dsi.fastutil.bytes.ByteArrayList headrooms=new it.unimi.dsi.fastutil.bytes.ByteArrayList();

    void record(WorldGenRegion level, BlockPos floor,int headroom,boolean rim) {
        var deck=level.getBlockState(floor);
        var above=level.getBlockState(floor.above());
        int upper=rim || above.getBlock() instanceof StairBlock ? index(above)+1 : 0;
        int pos=position(level,floor);
        lanes.add(headroom>0?~pos:pos); lanes.add(index(deck)); lanes.add(upper);
        headrooms.add((byte)(headroom>0?headroom:6));
    }
    private int index(BlockState state) {
        int i=states.indexOf(state);
        if (i>=0) return i;
        states.add(state);
        return states.size()-1;
    }
    void trail(WorldGenRegion level,BlockPos floor,BlockState cover) {
        trails.add(position(level,floor));
        trails.add(index(cover));
    }
    void soilFinish(WorldGenRegion level,BlockPos floor,int naturalY,BlockState light,BlockState dark) {
        soilFinishes.add(position(level,floor));
        soilFinishes.add(Math.min(floor.getY(),naturalY)-1);
        soilFinishes.add(index(light));
        soilFinishes.add(index(dark));
    }
    void signSpace(WorldGenRegion level,BlockPos board) { signArms.add(position(level,board)); }
    private static int position(WorldGenRegion level,BlockPos p) {
        return ((p.getY()-level.getMinBuildHeight())<<8) | ((p.getZ()&15)<<4) | (p.getX()&15);
    }
    void save(WorldGenRegion level,RoadsideStop stop) {
        if (lanes.isEmpty() && trails.isEmpty() && signArms.isEmpty() && stop==null) return;
        var tag=new CompoundTag();
        var palette=new ListTag();
        for (var state:states) palette.add(NbtUtils.writeBlockState(state));
        tag.put("palette",palette);
        tag.putIntArray("lanes",lanes.toIntArray());
        if (!soilFinishes.isEmpty()) tag.putIntArray("soilFinishes",soilFinishes.toIntArray());
        if (!trails.isEmpty()) tag.putIntArray("trails",trails.toIntArray());
        if (!signArms.isEmpty()) tag.putIntArray("signArms",signArms.toIntArray());
        tag.putByteArray("headrooms",headrooms.toByteArray());
        if (stop!=null) tag.put("stop",stop.save());
        var c=level.getCenter();
        RoadData.PENDING.set(level.getChunk(c.x,c.z),tag);
    }
    public static void discardLegacy(ChunkAccess chunk) {
        // A full chunk saved by 1.4.1 may already contain player edits.
        if (RoadData.PENDING.has(chunk)) {
            RoadData.PENDING.remove(chunk);
            chunk.setUnsaved(true);
        }
    }

    public static void beforeLighting(ServerLevel level,ChunkAccess chunk) {
        if (!(chunk instanceof ProtoChunk) || chunk instanceof ImposterProtoChunk || RoadVersion.lit(chunk)) return;
        if (RoadData.COLUMNS.has(chunk)) {
            CoastalPier.finishFeet(level,chunk,RoadData.COLUMNS.get(chunk));
            RoadData.COLUMNS.remove(chunk);
            chunk.setUnsaved(true);
        }
        if (!RoadData.PENDING.has(chunk)) return;
        var data=RoadData.PENDING.get(chunk);
        var palette=data.getList("palette",Tag.TAG_COMPOUND).stream()
                .map(tag->RoadApi.readBlock(level,(CompoundTag)tag)).toList();
        int[] rows=data.getIntArray("lanes");
        byte[] headrooms=data.getByteArray("headrooms");
        var cp=chunk.getPos();
        for (int i=0;i+2<rows.length;i+=3) {
            int key=rows[i]<0?~rows[i]:rows[i];
            var floor=new BlockPos(cp.getBlockX(key&15),level.getMinBuildHeight()+(key>>>8),cp.getBlockZ((key>>>4)&15));
            if (CoastalPier.reserved(chunk,floor.getX(),floor.getZ())) continue;
            restore(chunk,floor,palette.get(rows[i+1]));
            int headroom=i/3<headrooms.length?headrooms[i/3]:rows[i]<0?4:6;
            for (int h=1;h<=headroom;h++) {
                var goal=h==1 && rows[i+2]>0 ? palette.get(rows[i+2]-1) : Blocks.AIR.defaultBlockState();
                restore(chunk,floor.above(h),goal);
            }
        }
        // TFC's forest soil patches cross chunk borders. Match the undisturbed layer below the
        // original surface only after all neighbouring decorators have had their turn.
        int[] finishes=data.getIntArray("soilFinishes");
        for (int i=0;i+3<finishes.length;i+=4) {
            int key=finishes[i];
            var floor=new BlockPos(cp.getBlockX(key&15),level.getMinBuildHeight()+(key>>>8),cp.getBlockZ((key>>>4)&15));
            if (CoastalPier.reserved(chunk,floor.getX(),floor.getZ())) continue;
            var placed=palette.get(finishes[i+2]);
            var now=chunk.getBlockState(floor);
            String currentSoil=RoadMaterials.soil(now);
            if (now!=placed && (currentSoil==null || RoadMaterials.withSoil(placed,currentSoil).getBlock()!=now.getBlock())) continue;
            int sampleY=finishes[i+1];
            String soil=sampleY<chunk.getMinBuildHeight()?null:RoadMaterials.soil(chunk.getBlockState(floor.atY(sampleY)));
            boolean dark="mollisol".equals(soil) || "andisol".equals(soil);
            var finish=RoadMaterials.withSoil(dark?palette.get(finishes[i+3]):placed,soil==null?RoadVersion.DEFAULT_SOIL:soil);
            if (now!=finish) chunk.setBlockState(floor,finish,false);
        }
        int[] trails=data.getIntArray("trails");
        for (int i=0;i+1<trails.length;i+=2) {
            int key=trails[i];
            var floor=new BlockPos(cp.getBlockX(key&15),level.getMinBuildHeight()+(key>>>8),cp.getBlockZ((key>>>4)&15));
            // Ice and Fire can place a cottage or its own street from the next chunk. Only dress bare soil.
            if (CoastalPier.reserved(chunk,floor.getX(),floor.getZ()) || !RoadTerrain.softVerge(chunk.getBlockState(floor))) continue;
            var cover=chunk.getBlockState(floor.above());
            var high=chunk.getBlockState(floor.above(2));
            if (cover.hasBlockEntity() || high.hasBlockEntity() || !cover.getFluidState().isEmpty() || !high.isAir()
                    || !cover.isAir() && (!RoadTerrain.canClear(cover) || !cover.getCollisionShape(level,floor.above()).isEmpty())) continue;
            if (!cover.isAir()) chunk.setBlockState(floor.above(),Blocks.AIR.defaultBlockState(),false);
            var tread=RoadMaterials.withSoil(palette.get(trails[i+1]),RoadMaterials.soil(chunk.getBlockState(floor)));
            chunk.setBlockState(floor,tread,false);
        }
        if (data.contains("stop")) RoadsideStop.load(data.getCompound("stop")).finish(level,chunk);
        for (int key:data.getIntArray("signArms")) {
            var board=new BlockPos(cp.getBlockX(key&15),level.getMinBuildHeight()+(key>>>8),cp.getBlockZ((key>>>4)&15));
            for (int h=0;h<=1;h++) {
                var pos=board.above(h);
                if (chunk.getBlockState(pos).is(net.minecraft.tags.BlockTags.LEAVES))
                    chunk.setBlockState(pos,Blocks.AIR.defaultBlockState(),false);
            }
        }
        RoadData.PENDING.remove(chunk);
        chunk.setUnsaved(true);
    }
    private static void restore(ChunkAccess chunk,BlockPos pos,BlockState goal) {
        var now=chunk.getBlockState(pos);
        if (now!=goal && !now.hasBlockEntity() && (RoadTerrain.canClear(now) || RoadTerrain.naturalGround(now)
                || now.getBlock() instanceof LiquidBlock && now.getFluidState().is(RoadTerrain.WATER))) {
            chunk.setBlockState(pos,goal,false);
        }
    }
}
