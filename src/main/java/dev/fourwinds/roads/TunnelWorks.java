package dev.fourwinds.roads;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import static dev.fourwinds.roads.RoadLayout.*;

/** A bore is surveyed across its whole width before carving any individual tile. */
final class TunnelWorks {
    enum Kind { ROCK, PORTAL, REVEAL, BENT, WING }
    record Row(Kind kind,int grade,int distance,boolean masonry,RoadMaterials stone) {
        boolean covered() { return kind!=Kind.WING; }
        int headroom(int depth) { return vaultClearance(depth,kind==Kind.ROCK); }
    }
    record Flank(Point point,Path path,Row row,int offset,int depth) {}
    private final RoadTerrain terrain;
    private final Map<Path,Survey> surveys=new HashMap<>();
    private List<Path> routes=List.of();
    TunnelWorks(RoadTerrain terrain) { this.terrain=terrain; }
    void routes(List<Path> paths) { routes=paths; }
    Row at(Path path,int t) { return surveys.computeIfAbsent(path,Survey::new).row(t); }

    private final class Survey {
        final Path path;
        final Map<Integer,Boolean> covered=new HashMap<>(),buried=new HashMap<>();
        final Map<Integer,Row> rows=new HashMap<>();
        Survey(Path path) { this.path=path; }
        boolean cover(int t) {
            return covered.computeIfAbsent(t,i->{
                if (!path.contains(i)) return false;
                var channel=terrain.bridges.along(path);
                Point center=channel.point(i,0);
                int middle=ShoreTerrain.surface(terrain.level,center);
                if (middle==Integer.MIN_VALUE || (middle&1)!=0) return false;
                int y=RoadFeature.surface(terrain,path,center);
                if ((middle>>1)<y+10 || y+11>=terrain.level.getMaxBuildHeight() || channel.span(i)!=null) return false;
                for (int off=edge(path.width(),-1)-2;off<=edge(path.width(),1)+2;off++) {
                    int sample=ShoreTerrain.surface(terrain.level,channel.point(i,off));
                    int d=depth(path.width(),off);
                    // A narrow shelf can have a roof over the road and a forty-block drop at one jamb.
                    int required=d<0?-4:vaultClearance(d,true)+1;
                    if (sample==Integer.MIN_VALUE || (sample&1)!=0 || (sample>>1)<y+required) return false;
                }
                return true;
            });
        }
        boolean inside(int t) {
            return buried.computeIfAbsent(t,i->sustainedCover(i,this::cover));
        }
        boolean mouth(int t) { return inside(t) && (!inside(t-1) || !inside(t+1)); }
        boolean canProject(int t) {
            if (!path.contains(t)) return false;
            var channel=terrain.bridges.along(path);
            if (channel.span(t)!=null) return false;
            int y=RoadFeature.surface(terrain,path,channel.point(t,0));
            for (int off=edge(path.width(),-1)-2;off<=edge(path.width(),1)+2;off++) {
                int ground=ShoreTerrain.surface(terrain.level,channel.point(t,off));
                if (ground==Integer.MIN_VALUE || (ground&1)!=0 || (ground>>1)<y-1) return false;
            }
            return true;
        }
        Row row(int t) {
            if (rows.containsKey(t)) return rows.get(t);
            Row value=classify(t); rows.put(t,value); return value;
        }
        Row classify(int t) {
            if (!path.contains(t)) return null;
            var channel=terrain.bridges.along(path);
            boolean under=inside(t);
            int portal=t,distance=0;
            if (!mouth(t)) {
                for (distance=1;distance<=6;distance++) {
                    if (mouth(t-distance)) { portal=t-distance; break; }
                    if (mouth(t+distance)) { portal=t+distance; break; }
                }
            }
            if (channel.span(t)!=null) return null;
            Point center=channel.point(t,0);
            int y=RoadFeature.surface(terrain,path,center);
            if (!under && distance>6) {
                int ground=ShoreTerrain.surface(terrain.level,center);
                if (ground==Integer.MIN_VALUE || (ground&1)!=0 || (ground>>1)<y+10 || y+11>=terrain.level.getMaxBuildHeight()) return null;
                // On a sheer ledge the uphill side opens into a tall gallery; never span an absent downhill footing.
                return new Row(Kind.ROCK,y,0,path.width()==5,terrain.materials.construction("andesite"));
            }
            // Keep intersections, reading niches and protected buildings clear of projecting masonry.
            boolean trim=true;
            for (Path other:routes) {
                if (other instanceof Spur s && (s.sign().distanceTo(center)<=7 || s.village().distanceTo(center)<=7)) trim=false;
                int i=center.along(other.eastWest());
                if (other!=path && other.contains(i) && Math.abs(center.across(other.eastWest())-other.center(i))<=8) trim=false;
            }
            if (path instanceof Road road) {
                int number=Math.floorDiv(t+SPACING/2,SPACING);
                if (Math.abs(t-number*SPACING)<=RoadSection.JUNCTION_SHIFT+7 &&
                        Math.abs(t-terrain.grade.junction(road,number).point().along(road.eastWest()))<=7) trim=false;
            }
            int route=path instanceof Road road?road.index():((Spur)path).across();
            Kind kind=Math.floorMod(t,8)==roll(terrain.level.getSeed(),route,path.eastWest()?41:43,8)?Kind.BENT:Kind.ROCK;
            if (distance<=6) {
                int out=!inside(portal-1)?-1:1;
                int front=portal;
                for (int i=1;i<=3 && canProject(portal+i*out);i++) {
                    front=portal+i*out;
                    Point site=channel.point(front,0);
                    if ((ShoreTerrain.surface(terrain.level,site)>>1)<=RoadFeature.surface(terrain,path,site)+2) break;
                }
                int inward=(front-t)*out;
                if (inward< -3 || inward>1 && !under && distance>3) return null;
                if (inward<=2 || !under) kind=inward<0?Kind.WING:inward==0?Kind.PORTAL:Kind.REVEAL;
                distance=Math.abs(inward);
            }
            if (kind!=Kind.ROCK && trim) {
                for (int off=edge(path.width(),-1)-4;off<=edge(path.width(),1)+4;off++) {
                    Point p=channel.point(t,off);
                    if (terrain.protectedAt(p,y-4,y+10,false)) { trim=false; break; }
                }
            }
            if (!trim && !under) return null;
            if (!trim) kind=Kind.ROCK;
            boolean masonry=path.width()==5;
            RoadMaterials stone=terrain.materials.construction(masonry || kind==Kind.ROCK || kind==Kind.BENT
                    ?"andesite":ShoreTerrain.quarry(terrain.level,channel.point(portal,0)));
            if (kind==Kind.PORTAL || kind==Kind.REVEAL || kind==Kind.WING) {
                for (int d:new int[]{0,-1,1,-2,2}) {
                    var bridge=channel.built(portal+d);
                    if (bridge!=null && !bridge.timber()) { stone=bridge.palette(); break; }
                }
            }
            return new Row(kind,y,distance,masonry,stone);
        }
    }

    private static Direction across(Path path,int sign) {
        return path.eastWest()?(sign<0?Direction.NORTH:Direction.SOUTH):(sign<0?Direction.WEST:Direction.EAST);
    }
    static BlockState timber(Direction.Axis axis) {
        return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,axis);
    }
    private static BlockState knee(Block stair,Direction towardSupport) {
        return stair.defaultBlockState().setValue(StairBlock.HALF,Half.TOP).setValue(StairBlock.FACING,towardSupport);
    }
    void bed(Point p,int deck,int along) {
        var pos=new BlockPos.MutableBlockPos(p.x(),deck-1,p.z());
        if (terrain.level.getBlockState(pos).isFaceSturdy(terrain.level,pos,Direction.UP)) return;
        int start=along-Math.floorMod(along,8);
        var bay=new BridgeBay(start,start+8);
        int depth=bay.masonryPier(along)?deck-terrain.level.getMinBuildHeight():Math.max(2,bay.soffit(along));
        for (int d=1;d<=depth;d++) {
            var old=terrain.level.getBlockState(pos.setY(deck-d));
            if (old.hasBlockEntity() || old.isFaceSturdy(terrain.level,pos,Direction.UP)
                    || terrain.protectedAt(p,deck-d,deck-d,false)) break;
            terrain.put(p,deck-d,Blocks.COBBLESTONE.defaultBlockState(),terrain.materials.construction("andesite"));
        }
    }
    void roof(Point p,Path path,Row row,int off) {
        int depth=depth(path.width(),off),roof=row.grade+1+row.headroom(depth);
        if (row.kind==Kind.ROCK) {
            terrain.sealRoof(p,roof);
            return;
        }
        Direction outward=across(path,Integer.signum(off));
        boolean crown=depth>=1;
        if (row.kind==Kind.BENT && !row.masonry) {
            // Each pair of knees rises from the post to a single continuous nine-block-high tie.
            set(p,roof,crown?timber(outward.getAxis()):knee(Blocks.SPRUCE_STAIRS,outward),row);
            for (int y=roof+1;y<=row.grade+9;y++) set(p,y,timber(outward.getAxis()),row);
        } else {
            var face=crown?Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP):
                    knee(Blocks.STONE_BRICK_STAIRS,outward);
            set(p,roof,face,row);
            Block course=crown && off==0 && row.kind!=Kind.REVEAL?Blocks.CHISELED_STONE_BRICKS:Blocks.STONE_BRICKS;
            set(p,roof+1,course.defaultBlockState(),row);
        }
    }

    Flank flank(List<Path> paths,Point p) {
        for (Path path:paths) {
            int t=p.along(path.eastWest());
            if (!path.contains(t) || Math.abs(p.across(path.eastWest())-path.center(t))>BridgeAlignment.MAX_SHIFT+5) continue;
            var channel=terrain.bridges.along(path);
            int off=p.across(path.eastWest())-channel.center(t);
            int d=-depth(path.width(),off);
            if (d<1 || d>4) continue;
            Row row=at(path,t);
            if (row==null) continue;
            // A portal can stand on an abutment. Only the unframed gallery ends at the bridge.
            if (row.kind==Kind.ROCK && (channel.built(t)!=null || channel.viaduct(t)!=null)) continue;
            if (row.kind==Kind.WING && d!=(row.distance==3?4:3)) continue;
            if (d==4 && row.kind!=Kind.PORTAL && row.kind!=Kind.WING) continue;
            if (d==3 && row.kind==Kind.ROCK) continue;
            var g=terrain.column(p);
            int original=ShoreTerrain.surface(terrain.level,p);
            if (original==Integer.MIN_VALUE || (original&1)!=0 || (original>>1)<row.grade-(d<=2?4:1)
                    || g.sea() || terrain.protectedAt(p,row.grade-2,row.grade+10,false)) continue;
            return new Flank(p,path,row,off,d);
        }
        return null;
    }
    void build(Flank f,RoadClearance clearance) {
        Row row=f.row;Point p=f.point;int y=row.grade;
        if (row.kind==Kind.WING && terrain.column(p).y()>y+5) return;
        if (row.covered() && f.depth<=2) {
            boolean post=f.depth==2 && row.kind!=Kind.ROCK;
            int height=row.headroom(-f.depth);
            if (f.depth==2 && row.kind==Kind.ROCK && retainCliff(f,height)) return;
            if (!terrain.clear(p,y,terrain.column(p).y(),height)) return;
            if (post) {
                if (!footing(p,row)) return;
                pier(f);
            } else {
                bed(p,y,p.along(f.path.eastWest()));
                Block curb=f.depth==1?Blocks.STONE_BRICKS:Blocks.COBBLESTONE;
                terrain.put(p,y,curb.defaultBlockState(),row.stone);
                var beam=new BlockPos(p.x(),y+height+1,p.z());
                var roof=terrain.level.getBlockState(beam.above());
                if (f.depth==2 && !row.masonry && row.kind==Kind.ROCK
                        && RoadTerrain.naturalGround(roof) && roof.isFaceSturdy(terrain.level,beam.above(),Direction.DOWN)) {
                    Direction.Axis length=f.path.eastWest()?Direction.Axis.X:Direction.Axis.Z;
                    set(p,y+height+1,timber(length),row);
                } else roof(p,f.path,row,f.offset);
                clearance.record(terrain.level,new BlockPos(p.x(),y,p.z()),height,false);
            }
            return;
        }
        if (f.depth==3 && row.kind==Kind.BENT) {
            // Open a recess behind the pier. Its base, shaft and shoulder can be seen from the side.
            if (terrain.column(p).y()>=y+6 && terrain.clear(p,y,terrain.column(p).y(),4)) {
                terrain.put(p,y,Blocks.COBBLESTONE.defaultBlockState(),row.stone);
                terrain.sealRoof(p,y+5);
                clearance.record(terrain.level,new BlockPos(p.x(),y,p.z()),4,false);
            }
            return;
        }
        if (!footing(p,row)) return;
        int height=row.kind==Kind.WING?4-row.distance:f.depth==4?3:5;
        for (int h=1;h<=height;h++) {
            BlockState stone;
            if (h==height && row.kind==Kind.PORTAL)
                stone=Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,across(f.path,-Integer.signum(f.offset)));
            else if (h==height) stone=Blocks.STONE_BRICK_SLAB.defaultBlockState();
            else if (row.kind==Kind.PORTAL && h==height-1) stone=Blocks.POLISHED_ANDESITE.defaultBlockState();
            else stone=(h==1?Blocks.COBBLESTONE:Blocks.STONE_BRICKS).defaultBlockState();
            set(p,y+h,stone,row);
        }
    }

    // An open rock gallery has one exposed side. Keep the actual uphill face beside it:
    // cutting both verges into steps lets a spring run down the cut and across the carriageway.
    private boolean retainCliff(Flank f,int height) {
        var path=f.path;int t=f.point.along(path.eastWest()),side=Integer.signum(f.offset);
        var across=terrain.bridges.along(path).point(t,edge(path.width(),-side)-2*side);
        int valley=ShoreTerrain.surface(terrain.level,across);
        int y=f.row.grade;
        if (valley==Integer.MIN_VALUE || (valley>>1)>y-3) return false;
        var foot=new BlockPos(f.point.x(),y,f.point.z());
        if (!terrain.level.getBlockState(foot).isFaceSturdy(terrain.level,foot,Direction.UP)) return false;
        var hill=terrain.bridges.along(path).point(t,f.offset+side);
        if ((ShoreTerrain.surface(terrain.level,hill)>>1)<y+height+3) return false;
        // Keep solid native rock. Line open joints in the retaining face up to the springing.
        terrain.lineOpening(f.point,y+1,y+height+1);
        return true;
    }
    private boolean footing(Point p,Row row) {
        int y=row.grade;
        for (int h=0;y+h>=terrain.level.getMinBuildHeight();h--) {
            var pos=new BlockPos(p.x(),y+h,p.z());
            var old=terrain.level.getBlockState(pos);
            if (old.isFaceSturdy(terrain.level,pos,Direction.UP)) return true;
            if (terrain.protectedAt(p,y+h,y+h,false) || !set(p,y+h,Blocks.COBBLESTONE.defaultBlockState(),row)) return false;
        }
        return false;
    }
    private void pier(Flank f) {
        Row row=f.row;Point p=f.point;
        boolean wood=row.kind==Kind.BENT && !row.masonry;
        int cap=wood?9:7;
        for (int h=1;h<=cap;h++) {
            BlockState block;
            if (wood && h>=2) block=timber(h>=7?across(f.path,1).getAxis():Direction.Axis.Y);
            else if (h==1 || h==5) block=Blocks.POLISHED_ANDESITE.defaultBlockState();
            else if (h>=2 && h<=4 && row.kind!=Kind.REVEAL)
                block=Blocks.STONE_BRICK_WALL.defaultBlockState().setValue(WallBlock.UP,true);
            else block=Blocks.STONE_BRICKS.defaultBlockState();
            set(p,row.grade+h,block,row);
        }
    }
    private boolean set(Point p,int y,BlockState state,Row row) {
        var pos=new BlockPos(p.x(),y,p.z());
        var old=terrain.level.getBlockState(pos);
        if (old.hasBlockEntity() || !(RoadTerrain.canClear(old) || old.is(RoadTerrain.GROUND)
                || old.is(row.stone.state(Blocks.STONE_BRICKS.defaultBlockState()).getBlock())
                || old.getFluidState().is(RoadTerrain.WATER))) return false;
        terrain.put(p,y,state,row.stone);
        return true;
    }
}
