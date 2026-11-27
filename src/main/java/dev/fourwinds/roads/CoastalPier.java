package dev.fourwinds.roads;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import static dev.fourwinds.roads.RoadLayout.*;
import static dev.fourwinds.roads.PierLayout.*;

final class CoastalPier {
    private static final int MAX_DEPTH=18;
    private static final TagKey<Biome> PACK_ICE=TagKey.create(RoadApi.BIOME,FourWinds.id("pack_ice"));

    static boolean placeAtEnds(RoadTerrain terrain,Road... roads) {
        var chunk=terrain.level.getCenter();
        boolean placed=false;
        for (Road road:roads) {
            int first=road.eastWest()?chunk.getMinBlockX():chunk.getMinBlockZ();
            for (int t=first;t<first+16;t++) {
                Point p=road.point(t,0);
                if (p.x()>>4!=chunk.x || p.z()>>4!=chunk.z) continue;
                if (reserved(terrain.level.getChunk(chunk.x,chunk.z),p.x(),p.z())) continue;
                int water=waterTop(terrain,p);
                if (water==Integer.MIN_VALUE || !sea(terrain,p,water)) continue;
                for (int out:new int[]{-1,1}) {
                    Point back=road.point(t-out,0);
                    if (sea(terrain,back,water)) continue;
                    var plan=plan(terrain,road,t,out,water);
                    if (plan==null) continue;
                    var approach=gangway(terrain,road,t,plan);
                    if (approach==null) continue;
                    if (HarbourSpacing.lighthouse(terrain,road,t,plan)) {
                        var bottoms=new HashMap<Point,Integer>();
                        if (!fits(terrain,plan,bottoms)) continue;
                        var id=FourWinds.id(terrain.materials.usesLanterns() ? "coastal_lighthouse" : "tfc/coastal_lighthouse");
                        var drawing=terrain.level.getLevel().getStructureManager().get(id);
                        if (drawing.isEmpty()) continue;
                        build(terrain,plan,bottoms,drawing.get());
                    } else {
                        var jetty=landing(terrain,plan);
                        if (jetty==null) continue;
                        buildGangway(terrain,plan,jetty);
                    }
                    buildGangway(terrain,plan,approach);
                    placed=true;
                }
            }
        }
        return placed;
    }

    private static boolean wet(BlockState state) {
        return state.getFluidState().is(RoadTerrain.WATER) || state.is(RoadTerrain.ICE);
    }
    static boolean reserved(ChunkAccess chunk,int x,int z) {
        return RoadData.FOOTPRINT.has(chunk) && RoadData.FOOTPRINT.get(chunk).get((x&15)|((z&15)<<4));
    }
    private static void reserve(RoadTerrain t,Point q) {
        RoadData.FOOTPRINT.get(t.level.getChunk(q.x()>>4,q.z()>>4)).set((q.x()&15)|((q.z()&15)<<4));
    }
    private static BlockPos pos(Point p,int y) { return new BlockPos(p.x(),y,p.z()); }
    private static int waterTop(RoadTerrain t,Point p) {
        int sea=t.level.getSeaLevel();
        for (int y=sea;y>=sea-1;y--) if (wet(t.level.getBlockState(pos(p,y)))) return y;
        return Integer.MIN_VALUE;
    }
    private static boolean sea(RoadTerrain t,Point p,int water) {
        var pos=pos(p,water);
        var state=t.level.getBlockState(pos);
        return wet(state) && t.marine(pos,state);
    }
    private static PierLayout plan(RoadTerrain terrain,Road road,int t,int out,int water) {
        Point shore=road.point(t,0), back=road.point(t-out,0);
        if (!hasHarbourPriority(road,t,out)) return null;
        if (terrain.unstableTidalShelf(back,water)) return null;
        int entrance=Math.max(water+1,RoadFeature.surface(terrain,road,back));
        if (water+2+HEIGHT>=terrain.level.getMaxBuildHeight()) return null;
        IntFunction<Point> along=d->road.point(t+out*d,0);
        if (!suitableShore(water,entrance,d->sea(terrain,along.apply(d),water),
                d->waterTop(terrain,along.apply(d))==Integer.MIN_VALUE,d->terrain.seabed(along.apply(d)),
                d->outlet(terrain,along.apply(d)))) return null;
        int side=roll(road.seed()+3149,shore.x(),shore.z(),2)==0 ? 1 : -1;
        var preferred=new PierLayout(shore,road.eastWest(),out,side,water,entrance);
        var other=nearest(road.seed(),!road.eastWest(),shore);
        int cost=shoreConflicts(terrain,preferred,other);
        if (cost==0) return preferred;
        var mirror=new PierLayout(shore,road.eastWest(),out,-side,water,entrance);
        return shoreConflicts(terrain,mirror,other)<cost ? mirror : preferred;
    }

    // Choose the side from the seed's undecorated seafloor, biomes and road layout.
    // A bridge in a neighbouring chunk must have the same vote before and after it gets built.
    private static int shoreConflicts(RoadTerrain terrain,PierLayout p,Road crossing) {
        int conflicts=0,seaLevel=terrain.level.getSeaLevel();
        for (int s=0;s<=LENGTH;s++) for (int u=MIN_SIDE;u<=MAX_SIDE;u++) {
            boolean fairway=s>=10 && u<=MIN_SIDE+1;
            if (!fairway && p.clearance(s,u)==0) continue;
            Point q=p.point(s,u);
            int column=ShoreTerrain.column(terrain,q);
            boolean wet=ShoreTerrain.wet(column,seaLevel,p.water());
            if (s>=3 && !wet || ShoreTerrain.bed(column,seaLevel)<p.water()-MAX_DEPTH && (p.pile(s,u) || p.stoneFoot(s,u))) conflicts++;
            int t=q.along(crossing.eastWest());
            Point center=crossing.point(t,0);
            if (Math.abs(q.across(crossing.eastWest())-center.across(crossing.eastWest()))>2) continue;
            if (terrain.seabed(center)<p.water()
                    && terrain.marine(pos(center,p.water()),Blocks.WATER.defaultBlockState())) continue;
            if (!wet || !terrain.marine(pos(q,p.water()),Blocks.WATER.defaultBlockState())) conflicts++;
        }
        return conflicts;
    }

    private static Outlet outlet(RoadTerrain terrain,Point p) {
        var biome=terrain.seaLevelBiome(p.x(),p.z());
        if (biome.is(PACK_ICE)) return Outlet.PACK_ICE;
        return biome.is(RoadTerrain.SEA) ? Outlet.OPEN_SEA : Outlet.COAST;
    }

    private static boolean openBerth(RoadTerrain terrain,PierLayout p) {
        // Two water columns abreast from the boarding step to the seaward end fit a boat and its passenger.
        for (int s=10;s<=LENGTH;s++) for (int u=MIN_SIDE;u<=MIN_SIDE+1;u++) {
            BlockPos water=pos(p.point(s,u),p.water());
            var surface=terrain.level.getBlockState(water);
            if (!surface.getFluidState().is(RoadTerrain.WATER) || surface.is(RoadTerrain.ICE)) return false;
            var shape=surface.getCollisionShape(terrain.level,water);
            if (!shape.isEmpty() && shape.max(Direction.Axis.Y)>.75) return false;
            for (int h=1;h<=2;h++) {
                var head=water.above(h);
                var state=terrain.level.getBlockState(head);
                if (!state.getFluidState().isEmpty() || !state.getCollisionShape(terrain.level,head).isEmpty()) return false;
            }
        }
        return true;
    }

    private static boolean freeOfStructures(RoadTerrain terrain,BoundingBox box) {
        for (int x=box.minX()>>4;x<=box.maxX()>>4;x++) for (int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) {
            if (RoadVersion.full(terrain.level.getChunk(x,z,RoadVersion.CARVERS))) return false;
            for (var start:VillageAccess.inChunk(terrain.level,new ChunkPos(x,z))) {
                for (var piece:start.getPieces()) if (piece.getBoundingBox().intersects(box)) return false;
            }
        }
        return true;
    }

    // Even at a chunk corner, every template column is within the one-chunk FEATURES write radius.
    private static boolean fits(RoadTerrain terrain,PierLayout p,Map<Point,Integer> bottoms) {
        if (!openBerth(terrain,p)) return false;
        Point a=p.point(0,MIN_SIDE), b=p.point(LENGTH,MAX_SIDE);
        var box=BoundingBox.fromCorners(pos(a,p.water()-MAX_DEPTH),pos(b,p.deck()+HEIGHT));
        if (!freeOfStructures(terrain,box)) return false;
        for (int s=0;s<=LENGTH;s++) for (int u=MIN_SIDE;u<=MAX_SIDE;u++) {
            int clearance=p.clearance(s,u);
            if (clearance==0) continue;
            Point q=p.point(s,u);
            if (reserved(terrain.level.getChunk(q.x()>>4,q.z()>>4),q.x(),q.z())) return false;
            boolean landing=p.boarding(s,u);
            // A tidal meadow can put its turf and plants back during the next chunk's vegetation pass.
            if (s>=3 && waterTop(terrain,q)!=p.water()) return false;
            int first=landing ? p.deck()-1 : p.floor(s);
            int top=Math.max(first+2,p.deck()+clearance);
            for (int y=first;y<=top;y++) {
                var state=terrain.level.getBlockState(pos(q,y));
                if (s<=2 && y==first && !state.hasBlockEntity() && state.is(RoadTerrain.GROUND)) continue;
                if (state.hasBlockEntity() || !(state.isAir() || RoadApi.replaceable(state))) return false;
            }
            if (p.pile(s,u) || p.stoneFoot(s,u)) {
                int ground=bottom(terrain,q,p);
                if (ground==Integer.MIN_VALUE) return false;
                bottoms.put(q,ground);
            }
        }
        return true;
    }

    private static int bottom(RoadTerrain terrain,Point q,PierLayout p) {
        for (int y=Math.max(p.deck(),p.entrance())+2;y>=Math.max(terrain.level.getMinBuildHeight(),p.water()-MAX_DEPTH);y--) {
            var pos=pos(q,y); var state=terrain.level.getBlockState(pos);
            if (state.hasBlockEntity()) break;
            if (state.isAir() || RoadApi.replaceable(state) || state.is(RoadTerrain.ICE)
                    || state.getFluidState().is(RoadTerrain.WATER) && !state.isCollisionShapeFullBlock(terrain.level,pos)) continue;
            if (state.is(RoadTerrain.GROUND) && state.isFaceSturdy(terrain.level,pos,Direction.UP)) return y;
            break;
        }
        return Integer.MIN_VALUE;
    }

    private record Board(Point point,int floor,int bed,BlockState surface) {}

    // In an estuary the first salt-water column can be several steps beyond the actual shoreline.
    private static List<Board> gangway(RoadTerrain terrain,Road road,int t,PierLayout pier) {
        int land=1;
        for (;land<=14;land++) {
            Point q=road.point(t-land*pier.outward(),0);
            int a=ShoreTerrain.surface(terrain.level,q);
            int b=ShoreTerrain.surface(terrain.level,road.point(t-(land+1)*pier.outward(),0));
            if (a==Integer.MIN_VALUE || b==Integer.MIN_VALUE) return null;
            if (((a|b)&1)==0) break;
        }
        if (land>14) return null;
        if (land==1) return List.of();
        Point end=road.point(t-land*pier.outward(),0);
        int bank=RoadFeature.surface(terrain,road,end);
        if (Math.abs(pier.entrance()-bank)>land || bank<pier.water()) return null;
        var boards=new ArrayList<Board>();
        var footprint=BoundingBox.fromCorners(pos(pier.shore(),pier.water()-MAX_DEPTH),pos(end,Math.max(bank,pier.entrance())+2));
        Direction out=direction(pier,1,0);
        for (int i=1;i<land;i++) {
            int floor=RoadSection.interpolate(pier.entrance(),bank,i,land);
            int prev=RoadSection.interpolate(pier.entrance(),bank,i-1,land);
            int next=RoadSection.interpolate(pier.entrance(),bank,i+1,land);
            BlockState surface=floor>prev?stair(out.getOpposite()):floor>next?stair(out):Blocks.SPRUCE_PLANKS.defaultBlockState();
            for (int u=-1;u<=1;u++) {
                Point q=road.point(t-i*pier.outward(),u);
                var chunk=terrain.level.getChunk(q.x()>>4,q.z()>>4,RoadVersion.CARVERS);
                if (reserved(chunk,q.x(),q.z())) return null;
                footprint.encapsulate(pos(q,floor));
                for (int y=floor;y<=floor+2;y++) {
                    var state=terrain.level.getBlockState(pos(q,y));
                    if (state.hasBlockEntity() || !(state.isAir() || RoadApi.replaceable(state)
                            || y==floor && state.is(RoadTerrain.GROUND))) return null;
                }
                int bed=Integer.MIN_VALUE;
                if (u!=0 && (i==1 || i==land-1 || (i-1)%4==0)) {
                    bed=bottom(terrain,q,pier);
                    if (bed==Integer.MIN_VALUE) return null;
                }
                boards.add(new Board(q,floor,bed,surface));
            }
        }
        return freeOfStructures(terrain,footprint)?boards:null;
    }

    // A secondary landing has two rows of piles, a turning head and a low boat step.
    private static List<Board> landing(RoadTerrain terrain,PierLayout pier) {
        var boards=new ArrayList<Board>();
        var box=BoundingBox.fromCorners(pos(pier.point(0,-1),pier.water()-MAX_DEPTH),pos(pier.point(10,3),pier.deck()+3));
        if (!freeOfStructures(terrain,box)) return null;
        for (int s=0;s<=10;s++) for (int u=-1;u<=(s>=7?3:1);u++) {
            Point q=pier.point(s,u);
            var chunk=terrain.level.getChunk(q.x()>>4,q.z()>>4,RoadVersion.CARVERS);
            if (reserved(chunk,q.x(),q.z()) || s>=3 && waterTop(terrain,q)!=pier.water()) return null;
            boolean step=s>=7 && u==3;
            int y=step?pier.deck()-1:pier.floor(s);
            for (int h=0;h<=2;h++) {
                var state=terrain.level.getBlockState(pos(q,y+h));
                if (state.hasBlockEntity() || !(state.isAir() || RoadApi.replaceable(state) || h==0 && s<=2 && state.is(RoadTerrain.GROUND))) return null;
            }
            boolean pile=(s==1 || s==5 || s==9) && (u==-1 || u==(s==9?2:1));
            int bed=pile?bottom(terrain,q,pier):Integer.MIN_VALUE;
            if (pile && bed==Integer.MIN_VALUE) return null;
            var surface=step?Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP):
                    s>0 && y>pier.floor(s-1)?stair(direction(pier,1,0)):
                    y>pier.floor(s+1)?stair(direction(pier,-1,0)):
                    pile?Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState():Blocks.SPRUCE_PLANKS.defaultBlockState();
            boards.add(new Board(q,y,bed,surface));
        }
        for (int s=7;s<=10;s++) for (int u=4;u<=5;u++) {
            Point q=pier.point(s,u);
            var pos=pos(q,pier.water());var state=terrain.level.getBlockState(pos);
            if (!state.getFluidState().is(RoadTerrain.WATER) || state.is(RoadTerrain.ICE)
                    || !terrain.level.getBlockState(pos.above()).getCollisionShape(terrain.level,pos.above()).isEmpty()) return null;
        }
        return boards;
    }

    private static void buildGangway(RoadTerrain terrain,PierLayout pier,List<Board> boards) {
        var timber=terrain.materials.construction("andesite");
        for (Board board:boards) {
            Point q=board.point();
            reserve(terrain,q);
            if (board.bed()!=Integer.MIN_VALUE) {
                var log=Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
                rememberFoot(terrain,q,Math.min(board.bed()+1,board.floor()-1),pier.water()-MAX_DEPTH,timber.state(log));
                for (int y=board.bed()+1;y<board.floor();y++) terrain.put(q,y,log,timber);
            }
            for (int y=board.floor()+1;y<=board.floor()+2;y++)
                if (!terrain.level.getBlockState(pos(q,y)).isAir()) terrain.put(q,y,Blocks.AIR.defaultBlockState(),timber);
            terrain.put(q,board.floor(),board.surface(),timber);
        }
    }

    private static void rememberFoot(RoadTerrain terrain,Point q,int top,int limit,BlockState material) {
        var chunk=terrain.level.getChunk(q.x()>>4,q.z()>>4);
        var data=RoadData.COLUMNS.get(chunk);
        if (!data.contains("feet",Tag.TAG_LIST)) data.put("feet",new ListTag());
        var foot=new CompoundTag();
        foot.putLong("top",pos(q,top).asLong());
        foot.putInt("limit",limit);
        foot.put("material",NbtUtils.writeBlockState(material));
        data.getList("feet",Tag.TAG_COMPOUND).add(foot);
    }

    // A neighbouring underwater feature can remove the sediment a new pile was resting on.
    static void finishFeet(ServerLevel level,ChunkAccess chunk,CompoundTag data) {
        var feet=data.getList("feet",Tag.TAG_COMPOUND);
        for (int i=0;i<feet.size();i++) {
            var foot=feet.getCompound(i);
            var top=BlockPos.of(foot.getLong("top"));
            var material=RoadApi.readBlock(level,foot.getCompound("material"));
            int limit=foot.getInt("limit");
            var p=top.mutable();
            BlockState below=chunk.getBlockState(p);
            while (p.getY()>=limit && (below.is(material.getBlock()) || footingGap(below))) {
                p.move(Direction.DOWN);
                below=chunk.getBlockState(p);
            }
            if (p.getY()<limit || !below.isFaceSturdy(chunk,p,Direction.UP)) continue;
            while (p.getY()<top.getY()) {
                p.move(Direction.UP);
                if (footingGap(chunk.getBlockState(p))) chunk.setBlockState(p,material,false);
            }
        }
    }

    private static boolean footingGap(BlockState state) {
        return !state.hasBlockEntity() && (state.isAir() || RoadApi.replaceable(state)
                || state.getBlock() instanceof LiquidBlock && state.getFluidState().is(RoadTerrain.WATER));
    }

    private static Direction direction(PierLayout p,int s,int u) {
        Point a=p.shore(), b=p.point(s,u);
        return RoadApi.direction(b.x()-a.x(),b.z()-a.z());
    }
    private static BlockState stair(Direction up) {
        return Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,up);
    }
    private static void build(RoadTerrain terrain,PierLayout p,Map<Point,Integer> bottoms,StructureTemplate drawing) {
        // The octagonal base in the drawing is andesite down to its first exposed course.
        RoadMaterials palette=terrain.materials.construction("andesite");
        for (int s=0;s<=LENGTH;s++) for (int u=MIN_SIDE;u<=MAX_SIDE;u++) {
            if (p.clearance(s,u)==0) continue;
            Point q=p.point(s,u);
            reserve(terrain,q);
            boolean landing=p.boarding(s,u), masonry=p.stoneFoot(s,u);
            if (!p.platform(s,u) && !landing) continue;
            int floor=landing ? p.deck()-1 : p.floor(s);
            Integer bed=bottoms.get(q);
            if (bed!=null) {
                rememberFoot(terrain,q,Math.min(bed+1,floor-1),p.water()-MAX_DEPTH,
                        palette.state((masonry?Blocks.COBBLESTONE:Blocks.STRIPPED_SPRUCE_LOG).defaultBlockState()));
                for (int y=bed+1;y<floor;y++) {
                    Block support=Blocks.STRIPPED_SPRUCE_LOG;
                    if (masonry) support=y>=p.water()-1 && u<TOWER_SIDE && s>TOWER-2
                            ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE;
                    terrain.put(q,y,support.defaultBlockState(),palette);
                }
            } else if (!landing && (s==4 || s==8 || s==12 || s==15)) {
                var axis=direction(p,0,1).getAxis();
                terrain.put(q,floor-1,Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,axis),palette);
            }
            BlockState surface;
            if (landing) surface=Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP);
            else if (p.boatStep(s,u)) surface=stair(direction(p,1,0));
            else if (masonry) surface=Blocks.STONE_BRICKS.defaultBlockState();
            else if (s>0 && floor>p.floor(s-1)) surface=stair(direction(p,1,0));
            else if (floor>p.floor(s+1)) surface=stair(direction(p,-1,0));
            else if (p.pile(s,u)) surface=Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
            else surface=Blocks.SPRUCE_PLANKS.defaultBlockState();
            terrain.put(q,floor,surface,palette);
        }
        // Positive template X runs out to sea and positive Z toward the keeper's store.
        Direction forward=direction(p,1,0);
        Rotation rotation=switch(forward) {
            case EAST -> Rotation.NONE;
            case SOUTH -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_180;
            default -> Rotation.COUNTERCLOCKWISE_90;
        };
        Mirror mirror=rotation.rotate(Direction.SOUTH)==direction(p,0,1) ? Mirror.NONE : Mirror.LEFT_RIGHT;
        var settings=RoadVersion.dry(new StructurePlaceSettings().setMirror(mirror).setRotation(rotation)
                .setKnownShape(true).setIgnoreEntities(true));
        var origin=pos(p.point(0,MIN_SIDE),p.deck()+1);
        if (drawing.placeInWorld(terrain.level,origin,origin,settings,RoadGame.random(origin.asLong()),2)) {
            terrain.materials.fuelSignal(terrain.level,pos(p.point(TOWER,TOWER_SIDE),p.deck()+FIRE_HEIGHT));
        }
    }
}
