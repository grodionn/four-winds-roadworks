package dev.fourwinds.roads;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import static dev.fourwinds.roads.RoadLayout.*;

final class RoadFeature extends Feature<NoneFeatureConfiguration> {
    private record Cell(Point p, Path path, int t, int offset, int width, boolean edgeTile, int y, int rise,
                        RoadTerrain.RoadColumn ground, boolean bridge, boolean junction, boolean arcade, boolean streetVerge, BridgeSurvey.Span span,
                        TunnelWorks.Row bore) {
        boolean parapet() { return (bridge || arcade) && edgeTile() && !junction && bore==null; }
        int headroom() {
            if (bore!=null) return bore.headroom(depth(width,offset));
            if (ground.rockCover(y)) return vaultClearance(depth(width,offset),true);
            if (!bridge && !arcade && ground.water() && ground.y()>y) return RoadTerrain.TUNNEL_HEIGHT;
            return 0;
        }
    }
    private record Marker(Point p, int y, Spur destination) {}
    private record Shoulder(Point p, RoadTerrain.RoadColumn ground, int y) {}
    private record VergeStone(Point p, int y, Block block) {}
    private record RoadsideWall(Point p,int bottom,int top,boolean retaining) {}
    private static final int VERGE_WIDTH = 6;

    RoadFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!(context.level() instanceof WorldGenRegion level) || level.getLevel().dimension() != Level.OVERWORLD) return false;
        var chunk = level.getCenter();
        var owner=level.getChunk(chunk.x,chunk.z);
        var survey=RoadData.SURFACE.get(owner);
        if (survey.getBoolean("roadsPlaced")) return false;
        long seed = level.getSeed();
        Point middle = new Point(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        var grade=new RoadGrade(level);
        var villageLinks = VillageLinks.nearby(level,grade);
        var paths = new ArrayList<Path>();
        var eastWest = band(seed, true, middle);
        var northSouth = band(seed, false, middle);
        boolean nearEW=near(eastWest,middle),nearNS=near(northSouth,middle);
        if (!nearEW && !nearNS && villageLinks.isEmpty()) return false;
        var terrain = new RoadTerrain(level,grade);
        if (nearEW) { eastWest=terrain.grade.follow(eastWest); paths.add(eastWest); }
        if (nearNS) { northSouth=terrain.grade.follow(northSouth); paths.add(northSouth); }
        paths.addAll(villageLinks);
        terrain.tunnels.routes(paths);
        var cells = new ArrayList<Cell>();
        var markers = new ArrayList<Marker>();
        var shoulders = new ArrayList<Shoulder>();
        var stones = new ArrayList<VergeStone>();
        var walls = new ArrayList<RoadsideWall>();
        var jambs=new ArrayList<TunnelWorks.Flank>();
        if (nearEW && nearNS) {
            Point junction=terrain.grade.junction(eastWest,northSouth.index()).point();
            if (junction.distanceTo(middle)<=32) {
                Marker sign=signSite(terrain,eastWest,junction.along(true),null,northSouth);
                if (sign==null) sign=signSite(terrain,northSouth,junction.along(false),null,eastWest);
                if (sign!=null) markers.add(sign);
            }
        }
        for (Spur link:villageLinks) {
            var turn=link.point(link.turn(),0);
            if (turn.distanceTo(middle)>32) continue;
            var trunk=terrain.grade.follow(nearest(seed,!link.eastWest(),turn));
            var sign=signSite(terrain,link,link.turn(),link,trunk);
            if (sign!=null) markers.add(sign);
        }
        var ownedSigns=markers.stream().filter(m->(m.p.x()>>4)==chunk.x && (m.p.z()>>4)==chunk.z).toList();
        var villageTrails=villageLinks.stream().filter(s->s.inPixieVillage(middle)).toList();
        var clearance=terrain.clearance;
        for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                if (CoastalPier.reserved(owner,x,z)) continue;
                var p = new Point(x, z);
                Cell cell = plan(terrain, paths, p);
                if (!villageTrails.isEmpty() && (cell==null || cell.path instanceof Spur) && villageTrail(terrain,p,villageTrails,clearance)) continue;
                boolean flanked=false;
                if (cell != null) cells.add(cell);
                else {
                    cutwater(terrain,paths,p);
                    var flank=terrain.tunnels.flank(paths,p);
                    if (flank!=null) { jambs.add(flank); flanked=true; }
                    else {
                        var wall=roadsideWall(terrain,paths,p);
                        if (wall!=null) walls.add(wall);
                    }
                }
                if (cell==null && !flanked && markers.stream().noneMatch(m->m.p.distanceTo(p)<=1)) {
                    Shoulder shoulder = shoulder(terrain, paths, p);
                    if (shoulder != null) shoulders.add(shoulder);
                    VergeStone stone = vergeStone(terrain,paths,p,seed,shoulder);
                    if (stone != null) stones.add(stone);
                }
            }
        }

        for (Shoulder s : shoulders) terrain.blendShoulder(s.p, s.ground, s.y);
        for (RoadsideWall wall : walls) {
            if (wall.retaining) terrain.faceBank(wall.p,wall.bottom,wall.top);
            else terrain.sealSide(wall.p,wall.bottom,wall.top);
        }
        for (var flank:jambs) terrain.tunnels.build(flank,clearance);
        for (Cell cell : cells) {
            if (build(terrain,cell,seed)
                    && markers.stream().noneMatch(m -> m.p.equals(cell.p))) {
                clearance.record(level,new BlockPos(cell.p.x(),cell.y,cell.p.z()),cell.headroom(),cell.parapet());
            }
        }
        for (Marker marker:markers) for (int x=marker.p.x()-1;x<=marker.p.x()+1;x++) for (int z=marker.p.z()-1;z<=marker.p.z()+1;z++) {
            if ((x>>4)!=chunk.x || (z>>4)!=chunk.z) continue;
            Point p=new Point(x,z);
            clearance.signSpace(level,new BlockPos(x,marker.y+2,z));
            if (p.equals(marker.p) || paths.stream().anyMatch(path->inside(terrain,path,p))) continue;
            var ground=terrain.column(p);
            if (ground.valid() && Math.abs(ground.y()-marker.y)<=1 && !terrain.protectedAt(p,ground.y(),ground.y()+4,marker.destination!=null)
                    && terrain.clear(p,ground.y(),ground.y(),3))
                clearance.record(level,new BlockPos(x,ground.y(),z),3,false);
        }
        for (Marker marker:ownedSigns) {
            var g=terrain.column(marker.p);
            if (g.valid() && !g.water() && !g.sea() && Math.abs(g.y()-marker.y)<=1 && !CoastalPier.reserved(owner,marker.p.x(),marker.p.z())
                    && !terrain.protectedAt(marker.p,marker.y-1,marker.y+4,marker.destination!=null)) mark(terrain,marker);
        }
        for (VergeStone stone : stones) terrain.placeVergeStone(stone.p,stone.y,stone.block);
        boolean pier = CoastalPier.placeAtEnds(terrain,eastWest,northSouth);
        var stop=RoadsideStop.find(terrain,paths);
        clearance.save(level,stop);
        survey.putBoolean("roadsPlaced",true);
        return pier || stop!=null || !cells.isEmpty() || !markers.isEmpty() || !shoulders.isEmpty() || !stones.isEmpty();
    }

    private static boolean near(Road road,Point p) {
        return Math.abs(road.index()*SPACING-p.across(road.eastWest()))<=64+13+VERGE_WIDTH;
    }

    private static boolean villageTrail(RoadTerrain terrain,Point p,List<Spur> spurs,RoadClearance clearance) {
        for (var spur:spurs) {
            int t=p.along(spur.eastWest());
            if (!spur.contains(t)) continue;
            int offset=p.across(spur.eastWest())-spur.center(t);
            if (Math.abs(offset)>1+VERGE_WIDTH) continue;
            if (Math.abs(offset)<=1) {
                int ground=ShoreTerrain.surface(terrain.level,p);
                if (ground!=Integer.MIN_VALUE && (ground&1)==0) {
                    var pos=new BlockPos(p.x(),ground>>1,p.z());
                    var material=offset==0?Blocks.DIRT_PATH:Blocks.COARSE_DIRT;
                    clearance.trail(terrain.level,pos,terrain.materials.state(material.defaultBlockState()));
                }
            }
            return true;
        }
        return false;
    }

    private static Shoulder shoulder(RoadTerrain terrain, List<Path> paths, Point p) {
        Path nearest = null;
        int distance = VERGE_WIDTH + 1, edgeOffset = 0;
        for (Path path : paths) {
            int t = p.along(path.eastWest());
            if (!path.contains(t)) continue;
            if (Math.abs(p.across(path.eastWest())-path.center(t))>BridgeAlignment.MAX_SHIFT+2+VERGE_WIDTH) continue;
            int offset = p.across(path.eastWest()) - terrain.bridges.along(path).center(t);
            int d = -depth(path.width(),offset);
            if (d <= 0) return null;
            if (d < distance) {
                distance = d;
                nearest = path;
                edgeOffset = edge(path.width(),Integer.signum(offset));
            }
        }
        if (nearest == null) return null;
        int t = p.along(nearest.eastWest());
        Point edge = terrain.bridges.along(nearest).point(t, edgeOffset);
        var ground = terrain.column(p);
        var roadEdge = terrain.column(edge);
        var spine=terrain.column(nearest.point(t,0));
        if (!ground.valid() || !roadEdge.valid() || ground.water() || roadEdge.water() || ground.sea() || roadEdge.sea()
                || spine.water() || spine.sea() || !ground.state().is(RoadTerrain.GROUND)) return null;
        int deck = surface(terrain, nearest, edge);
        int surveyed=ShoreTerrain.surface(terrain.level,edge);
        if (surveyed==Integer.MIN_VALUE || (surveyed&1)!=0) return null;
        int edgeCut=(surveyed>>1)-deck;
        if (edgeCut < -3 || edgeCut>9 || terrain.bridges.along(nearest).built(t)!=null
                || terrain.tunnels.at(nearest,t)!=null) return null;
        boolean soil=RoadTerrain.softVerge(ground.state()) || ground.state().is(BlockTags.SAND)
                || ground.state().is(Blocks.GRAVEL) || RoadMaterials.quarry(ground.state())!=null && !RoadTerrain.rockSurface(ground.state());
        int y=RoadLayout.bankHeight(distance,ground.y(),deck,soil);
        if (y == ground.y() || terrain.protectedAt(p, Math.min(y,ground.y()) - 1, Math.max(y,ground.y()) + 2, false)) return null;
        return new Shoulder(p, ground, y);
    }

    private static VergeStone vergeStone(RoadTerrain terrain, List<Path> paths, Point p, long seed, Shoulder shoulder) {
        for (Path path : paths) {
            int t = p.along(path.eastWest());
            if (!path.contains(t) || Math.abs(Math.floorMod(t+SPACING/2,SPACING)-SPACING/2) < 9) continue;
            if (path instanceof Spur s && (Math.abs(t-s.turn()) < 10 || Math.abs(t-s.end()) < 5)) continue;
            int group = Math.floorDiv(t,32);
            int band = path instanceof Road r ? r.index() : ((Spur)path).across();
            long salt = seed ^ (path.eastWest() ? 7933L : 9199L);
            int phase = Math.floorMod(t,32) - (4 + roll(salt, band, group, 23));
            if (phase < 0 || phase > 1) continue;
            int style = roll(salt+13,band,group,5);
            if (style==0 || style==4) continue;
            if (phase == 1 && style != 2 && style != 3) continue;
            int side = roll(salt+23,band,group,2) == 0 ? -1 : 1;
            int offset = edge(path.width(),side) + side * (1 + phase + style%2);
            if (!p.equals(terrain.bridges.along(path).point(t,offset))) continue;
            var ground = terrain.column(p);
            int y = shoulder == null ? ground.y() : shoulder.y;
            var spine = terrain.column(path.point(t,0));
            if (!ground.valid() || !ground.state().is(RoadTerrain.GROUND) || ground.water() || ground.sea() || spine.water() || spine.sea()
                    || Math.abs(y-surface(terrain,path,p))>2 || terrain.protectedAt(p,y-1,y+2,false)) continue;
            Block stone = Blocks.STONE_BUTTON;
            return new VergeStone(p,y,stone);
        }
        return null;
    }

    private static Cell plan(RoadTerrain terrain, List<Path> paths, Point p) {
        Path selected = null;
        BridgeSurvey.Span span=null;
        int bestDepth = Integer.MIN_VALUE, hits = 0, selectedOffset = 0;
        boolean arcade=false;
        for (Path path : paths) {
            int t = p.along(path.eastWest());
            if (!path.contains(t)) continue;
            int across=p.across(path.eastWest());
            if (Math.abs(across-path.center(t))>BridgeAlignment.MAX_SHIFT+3) continue;
            var survey=terrain.bridges.along(path);
            var crossing=survey.built(t);
            if (crossing!=null || survey.surveyedWet(t)) {
                // Test the wet row itself, including when this tile is its landward abutment.
                int river=survey.span(t)!=null || survey.surveyedWet(t)?t:survey.span(t-1)!=null?t-1:t+1;
                if (!survey.reachesLand(river)) {
                    if (survey.span(t)!=null || survey.surveyedWet(t) || terrain.column(p).water()) continue;
                    crossing=null;
                }
            }
            int off = across-survey.center(t);
            if (Math.abs(off) > 3) continue;
            boolean elevated=crossing==null && survey.viaduct(t)!=null;
            int remaining = crossing!=null || elevated?survey.deckDepth(t,off):depth(path.width(),off);
            if (remaining<0 && path.width()<5 && survey.landingTrack(t,off)) remaining=0;
            if (remaining < 0) continue;
            hits++;
            if (remaining > bestDepth) { bestDepth = remaining; selected = path; selectedOffset = off; span=crossing; arcade=elevated; }
        }
        if (selected == null) return null;
        int t = p.along(selected.eastWest());
        var ground = terrain.column(p);
        // A viaduct on a dry coastal ledge still needs its seaward wheel track and parapet.
        // The surveyed centre below must remain on land.
        if (!ground.valid() || span==null && !arcade && ground.sea()) return null;
        Point center=terrain.bridges.along(selected).point(t,0);
        var spine = terrain.column(center);
        if (span==null && spine.sea() || CoastalPier.reserved(terrain.level.getChunk(center.x()>>4,center.z()>>4),center.x(),center.z())) return null;
        int y = surface(terrain,selected,p);
        boolean bridge=span!=null;
        if (y < terrain.level.getMinBuildHeight() + 1 || y + 6 >= terrain.level.getMaxBuildHeight()) return null;
        boolean riverbed=bridge && (selected.width()!=5 || terrain.bridges.along(selected).span(t)!=null);
        var bore=riverbed || arcade ? null : terrain.tunnels.at(selected,t);
        if (bore!=null && !bore.covered()) bore=null;
        boolean rockCover=ground.rockCover(y);
        boolean tunnel=bore!=null || rockCover || ground.water() && ground.y()>y;
        int bottom=tunnel?y-1:Math.min(y-2,ground.y()),top=bore!=null || rockCover?y+10:tunnel?y+RoadTerrain.TUNNEL_HEIGHT+1:Math.max(y+6,ground.y());
        // Taiga street templates include unbuilt grass banks on both sides of the actual village path.
        boolean naturalBank=ground.state().is(Blocks.GRASS_BLOCK) || ground.state().is(Blocks.PODZOL);
        boolean streetVerge=(bridge || arcade) && terrain.openStreetVerge(p,ground,y);
        if ((ground.state().is(Blocks.DIRT_PATH) && !tunnel || terrain.protectedAt(p,bottom,top,selected instanceof Spur,
                naturalBank || streetVerge || tunnel && ground.y()>=top+1))
                && !terrain.crossesAboveStreet(p,bottom,y)) return null;
        int rise = 0;
        if (hits == 1) {
            int dx=selected.eastWest()?1:0, dz=selected.eastWest()?0:1;
            int next = surface(terrain,selected,new Point(p.x()+dx,p.z()+dz));
            int prev = surface(terrain,selected,new Point(p.x()-dx,p.z()-dz));
            // Place a stair in the higher floor tile, with its low half facing the approach.
            if (prev < y && next >= y) rise = 1;
            else if (next < y && prev >= y) rise = -1;
            // Let the outer wheel track and grass meet the downhill verge at its own level.
            if (bore==null && !bridge && !arcade && prev==y && next==y && selected.width()>=4 && depth(selected.width(),selectedOffset)==0
                    && ground.y()==y-1 && !ground.water() && RoadTerrain.softVerge(ground.state())) y--;
        }
        return new Cell(p, selected, t, selectedOffset, bridge || arcade ? 5 : selected.width(),bestDepth==0,
                y, rise, ground, bridge, hits > 1,arcade,streetVerge,span,bore);
    }

    private static boolean inside(RoadTerrain terrain,Path path,Point p) {
        int t=p.along(path.eastWest());
        return path.contains(t) && depth(path.width(),p.across(path.eastWest())-terrain.bridges.along(path).center(t))>=0;
    }

    private static Marker signSite(RoadTerrain terrain,Path path,int turn,Spur village,Road crossing) {
        var lane=terrain.bridges.along(path);
        Marker plinth=null;
        for (int reach:new int[]{6,9,12,16,20}) for (int along:new int[]{1,-1}) {
            int t=turn+reach*along;
            if (!path.contains(t) || village!=null && Math.abs(t-village.end())<2 || lane.built(t)!=null) continue;
            int grade=surface(terrain,path,lane.point(t,0));
            for (int side:new int[]{-1,1}) {
                Point p=lane.point(t,edge(path.width(),side)+side);
                if (Math.abs(p.across(crossing.eastWest())-crossing.center(p.along(crossing.eastWest())))<crossing.width()/2+3) continue;
                int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
                for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
                    int observed=ShoreTerrain.surface(terrain.level,new Point(p.x()+dx,p.z()+dz));
                    if (observed==Integer.MIN_VALUE || (observed&1)!=0) { low=Integer.MIN_VALUE;continue; }
                    low=Math.min(low,observed>>1);high=Math.max(high,observed>>1);
                }
                int base=ShoreTerrain.surface(terrain.level,p)>>1;
                if (low>=grade-1 && high<=grade+1 && high-low<=1) return new Marker(p,base,village);
                // A flat, dry shelf below a village lane can carry one extra course under the post.
                if (plinth==null && village!=null && low==grade-2 && high<=grade-1 && base==low)
                    plinth=new Marker(p,base+1,village);
            }
        }
        return plinth;
    }

    private static RoadsideWall roadsideWall(RoadTerrain terrain,List<Path> paths,Point p) {
        int low=Integer.MAX_VALUE, high=Integer.MIN_VALUE;
        RoadsideWall bank=null;
        for (Path path : paths) {
            int t=p.along(path.eastWest());
            if (Math.abs(p.across(path.eastWest())-path.center(t))>BridgeAlignment.MAX_SHIFT+3) continue;
            int distance=depth(path.width(),p.across(path.eastWest())-terrain.bridges.along(path).center(t));
            if (distance>=0 && path.contains(t)) return null;
            if (distance < -1) continue;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Point neighbor=new Point(p.x()+side.getStepX(),p.z()+side.getStepZ());
                if (!inside(terrain,path,neighbor)) continue;
                var ground=terrain.column(neighbor);
                if (!ground.valid() || ground.sea()) continue;
                int y=surface(terrain,path,neighbor);
                if (ground.needsTunnel(y)) {
                    low=Math.min(low,y);
                    high=Math.max(high,y+RoadTerrain.TUNNEL_HEIGHT+1);
                } else {
                    var outer=terrain.column(p);
                    if (outer.valid() && !outer.water() && outer.y()>=y+2 && outer.y()<=y+6)
                        bank=new RoadsideWall(p,y+1,Math.min(y+3,outer.y()-1),true);
                }
            }
        }
        return high<low ? bank : new RoadsideWall(p,low,high,false);
    }

    static int surface(RoadTerrain terrain,Path path,Point p) {
        int t=p.along(path.eastWest());
        int y=terrain.bridges.along(path).grade(t);
        if (path instanceof Road road) {
            int number=Math.floorDiv(t+SPACING/2,SPACING);
            var landing=terrain.grade.junction(road,number);
            if (Math.abs(t-landing.point().along(road.eastWest()))>32 && Math.abs(t-number*SPACING)>=SPACING-RoadSection.VILLAGE_SHIFT-32) {
                number+=Integer.signum(t-number*SPACING);
                landing=terrain.grade.junction(road,number);
            }
            Point cross=landing.point();
            int junction=cross.along(road.eastWest());
            int distance=Math.abs(t-junction);
            if (distance<=32) {
                Road other=terrain.grade.follow(new Road(road.seed(),!road.eastWest(),number));
                var a=terrain.bridges.along(road);
                var b=terrain.bridges.along(other);
                int ot=cross.along(other.eastWest());
                var one=a.junctionSpan(junction);
                var two=b.junctionSpan(ot);
                boolean joinedDecks=one!=null && two!=null;
                if (a.span(t)!=null && !joinedDecks) return y;
                int shared;
                if (one==null && two==null) {
                    int x=a.grade(junction),z=b.grade(ot);
                    if (x==landing.y() && z==landing.y()) return y;
                    shared=Math.floorDiv(x+z+1,2);
                } else shared=one!=null && two!=null?Math.max(one.y(),two.y()):one!=null?one.y():two.y();
                int allowance=Math.max(0,Math.min(distance,18)-(joinedDecks?6:3))/3+Math.max(0,distance-18);
                int desired=RoadLayout.clamp(y,shared-allowance,shared+allowance);
                if (desired!=y) y=joinedDecks?desired:a.constrainLanding(t,desired);
            }
        }
        if (path instanceof Spur spur) {
            Point turn=spur.point(spur.turn(),0);
            Road trunk=terrain.grade.follow(nearest(terrain.level.getSeed(),!spur.eastWest(),turn));
            int landing=surface(terrain,trunk,turn);
            int allowance=Math.max(0,Math.abs(t-spur.turn())-trunk.width()/2);
            y=RoadLayout.clamp(y,landing-allowance,landing+allowance);
            int distance=Math.abs(t-spur.end());
            if (distance<=12) {
                var entry=terrain.column(spur.point(spur.end(),0));
                if (entry.valid()) return RoadLayout.clamp(y,entry.deck()-distance,entry.deck()+distance);
            }
        }
        return y;
    }

    private static boolean build(RoadTerrain terrain, Cell c, long seed) {
        int clearance=c.headroom();
        if (!terrain.clear(c.p,c.y,c.ground.y(),clearance)) return false;
        if (clearance>0) {
            if (!c.bridge && !c.arcade) terrain.tunnels.bed(c.p,c.y,c.t);
            if (c.bore!=null) terrain.tunnels.roof(c.p,c.path,c.bore,c.offset);
            else terrain.sealRoof(c.p,c.y+clearance+1);
        }
        if (c.bridge || c.arcade) { crossing(terrain,c); return true; }
        Block footing = c.ground.state().is(BlockTags.SAND) ? Blocks.SANDSTONE : Blocks.COBBLESTONE;
        if (c.y>=c.ground.y()+5 && terrain.crossesAboveStreet(c.p,c.ground.y(),c.y)) {
            // The village street passes under the embankment. Keep its right of way open.
            terrain.put(c.p,c.y-1,Blocks.STONE_BRICKS);
        } else terrain.foundation(c.p,c.ground,c.y,footing);
        int route=c.path instanceof Road road?road.index():((Spur)c.path).across();
        long salt=seed ^ (route*0x9e3779b97f4a7c15L) ^ (c.path.eastWest()?4637L:7607L);
        int patch=field(salt,c.t,c.offset,13,4);
        double drift=(field(salt+51,c.t,0,21,4)-50)/110.0;
        double mid=c.width==4?-.5:0;
        int leftRut=(int)Math.round(mid+drift-1);
        boolean wheel=c.offset==leftRut || c.offset==leftRut+2;
        int age=wear(seed,c.path,c.t);
        boolean stoneBed=c.ground.y()>=c.y+2 || RoadTerrain.rockSurface(c.ground.state());
        boolean sandy=c.ground.state().is(BlockTags.SAND);
        boolean grassy=RoadTerrain.softVerge(c.ground.state());
        Block paving,darkPaving;
        var floor=new BlockPos(c.p.x(),c.y,c.p.z());
        String soil=terrain.materials.localSoil(c.ground.state());
        if (c.rise!=0) {
            int f=c.path.eastWest()?c.rise:0,g=c.path.eastWest()?0:c.rise;
            int uphill=surface(terrain,c.path,new Point(c.p.x()+f,c.p.z()+g));
            // A lone change of level gets a half-height landing. Only sustained climbs need stair treads.
            boolean earthen=c.width<5 && !stoneBed && !sandy;
            if (uphill==c.y) paving=sandy?Blocks.SANDSTONE_SLAB:earthen?RoadGame.EARTH_SLAB:Blocks.COBBLESTONE_SLAB;
            else paving=sandy?Blocks.SANDSTONE_STAIRS:earthen?RoadGame.EARTH_STAIRS:Blocks.COBBLESTONE_STAIRS;
            darkPaving=earthen?(uphill==c.y?Blocks.COBBLESTONE_SLAB:Blocks.COBBLESTONE_STAIRS):paving;
        } else {
            boolean median=c.width==4 && c.offset==leftRut+1 && patch>57 && age>44;
            boolean nativeVerge=!c.junction && !stoneBed && !sandy && !wheel && c.ground.y()==c.y
                    && c.ground.state().getFluidState().isEmpty() && grassy && (median
                    || c.edgeTile() && patch<(c.width==5?34:c.width==4?77:72));
            if (nativeVerge) {
                if (soil!=null) terrain.clearance.soilFinish(terrain.level,floor,c.ground.y(),c.ground.state(),c.ground.state());
                return true;
            }
            paving=paving(c,sandy,stoneBed,wheel,patch,age,salt,false);
            darkPaving=soil==null?paving:paving(c,sandy,stoneBed,wheel,patch,age,salt,true);
        }
        if ((paving instanceof FallingBlock || soil!=null && darkPaving instanceof FallingBlock) && RoadApi.replaceable(terrain.level.getBlockState(floor.below())))
            terrain.put(c.p,c.y-1,footing);
        var light=RoadMaterials.withSoil(terrain.materials.state(floorState(c,paving)),soil);
        terrain.level.setBlock(floor,light,2);
        if (soil!=null && (darkPaving!=paving || paving==Blocks.DIRT_PATH || paving==Blocks.COARSE_DIRT
                || paving==RoadGame.EARTH_STAIRS || paving==RoadGame.EARTH_SLAB)) {
            var dark=RoadMaterials.withSoil(terrain.materials.state(floorState(c,darkPaving)),soil);
            terrain.clearance.soilFinish(terrain.level,floor,c.ground.y(),light,dark);
        }
        return true;
    }

    private static BlockState floorState(Cell c,Block block) {
        var state=block.defaultBlockState();
        return block instanceof StairBlock && c.rise!=0?state.setValue(StairBlock.FACING,direction(c.path.eastWest(),c.rise)):state;
    }

    private static int field(long seed,int t,int offset,int length,int width) {
        int row=Math.floorDiv(t,length),col=Math.floorDiv(offset,width);
        int a=Math.floorMod(t,length),b=Math.floorMod(offset,width);
        int left=RoadSection.interpolate(roll(seed,row,col,100),roll(seed,row+1,col,100),a,length);
        int right=RoadSection.interpolate(roll(seed,row,col+1,100),roll(seed,row+1,col+1,100),a,length);
        return RoadSection.interpolate(left,right,b,width);
    }

    private static Block paving(Cell c,boolean sandy,boolean rock,boolean wheel,int patch,int age,long salt,boolean darkSoil) {
        // Short staggered repairs, broad mineral patches and long wheel tracks have different scales.
        int course=Math.floorDiv(c.t+Math.floorMod(c.offset,2),3);
        int sett=roll(salt+71,course,c.offset,100);
        if (sandy) {
            boolean red=c.ground.state().is(Blocks.RED_SAND);
            if (!rock && !c.junction && (c.edgeTile() && !wheel || c.width<5 && patch<46))
                return red?Blocks.RED_SAND:Blocks.SAND;
            return c.junction || patch>65? (red?Blocks.CUT_RED_SANDSTONE:Blocks.CUT_SANDSTONE)
                    : (red?Blocks.RED_SANDSTONE:Blocks.SANDSTONE);
        }
        if (rock || c.junction || c.width==5 && age+patch/3<52) {
            if (c.edgeTile() && patch<38) return rock?Blocks.MOSSY_COBBLESTONE:Blocks.GRAVEL;
            if (c.junction || c.width==5 && patch>46) return sett<74?Blocks.STONE_BRICKS:Blocks.COBBLESTONE;
            return patch>58 && sett<62?Blocks.ANDESITE:Blocks.COBBLESTONE;
        }
        if (c.path instanceof Spur spur) {
            boolean landing=Math.min(Math.abs(c.t-spur.end()),Math.abs(c.t-spur.turn()))<5;
            if (landing && patch>34) return Blocks.GRAVEL;
            if (darkSoil) return c.offset==0 || patch>61?Blocks.GRAVEL:Blocks.COARSE_DIRT;
            if (c.offset==0) return patch>80?Blocks.GRAVEL:Blocks.DIRT_PATH;
            return patch<49?Blocks.COARSE_DIRT:Blocks.DIRT_PATH;
        }
        if (c.width==4) {
            if (darkSoil) {
                if (wheel || !c.edgeTile()) return patch<27 && !wheel?Blocks.COARSE_DIRT:Blocks.GRAVEL;
                return patch<31?Blocks.COARSE_DIRT:Blocks.GRAVEL;
            }
            if (wheel) return patch>54 && age<69?Blocks.GRAVEL:Blocks.DIRT_PATH;
            if (c.edgeTile()) return patch>76?Blocks.GRAVEL:Blocks.COARSE_DIRT;
            return patch<37?Blocks.GRAVEL:Blocks.COARSE_DIRT;
        }
        if (wheel) return age>70 && patch<44?Blocks.DIRT_PATH:Blocks.GRAVEL;
        if (c.edgeTile()) return patch<50?Blocks.COARSE_DIRT:patch>73?Blocks.MOSSY_COBBLESTONE:Blocks.GRAVEL;
        return patch<41?Blocks.GRAVEL:sett<76?Blocks.COBBLESTONE:Blocks.TUFF;
    }

    private static Direction direction(boolean eastWest, int sign) {
        return eastWest ? (sign > 0 ? Direction.EAST : Direction.WEST) : (sign > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static void cutwater(RoadTerrain terrain,List<Path> paths,Point p) {
        for (Path path:paths) {
            int t=p.along(path.eastWest());
            if (Math.abs(p.across(path.eastWest())-path.center(t))>BridgeAlignment.MAX_SHIFT+4) continue;
            var span=terrain.bridges.along(path).span(t);
            if (span==null || span.timber() || !span.bay().support(t) || !terrain.bridges.along(path).reachesLand(t)) continue;
            int offset=p.across(path.eastWest())-span.center();
            if (terrain.bridges.along(path).deckDepth(t,offset)!=-1) continue;
            var bed=terrain.column(p);
            if (!bed.water() || bed.sea() || span.y()-bed.y()<4) continue;
            for (int y=span.y()-3;y>=terrain.level.getMinBuildHeight();y--) {
                var pos=new BlockPos(p.x(),y,p.z());
                var old=terrain.level.getBlockState(pos);
                if (old.hasBlockEntity() || !(old.isAir() || old.getFluidState().is(RoadTerrain.WATER) || RoadTerrain.canClear(old))
                        || terrain.protectedAt(p,y,y,false)) break;
                var nose=y==span.y()-3?Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING,direction(!path.eastWest(),-Integer.signum(offset)))
                        : (y<=bed.y()?Blocks.MOSSY_COBBLESTONE:Blocks.STONE_BRICKS).defaultBlockState();
                terrain.put(p,y,nose,span.palette());
            }
        }
    }

    private static void crossing(RoadTerrain terrain,Cell c) {
        BridgeBay bay=c.bridge?c.span.bay():terrain.bridges.along(c.path).viaduct(c.t);
        RoadMaterials palette=c.bridge?c.span.palette():terrain.materials.construction("andesite");
        boolean timber=c.bridge && c.span.timber(),bent=timber?bay.support(c.t):bay.masonryPier(c.t);
        Block tread=timber?(c.rise==0?Blocks.SPRUCE_PLANKS:Blocks.SPRUCE_STAIRS)
                :(c.rise==0?Blocks.STONE_BRICKS:Blocks.STONE_BRICK_STAIRS);
        terrain.put(c.p,c.y,floorState(c,tread),palette);
        if (c.parapet()) {
            Block cap=timber?Blocks.SPRUCE_SLAB:Blocks.STONE_BRICK_SLAB;
            if (timber && bay.support(c.t)) cap=Blocks.STRIPPED_SPRUCE_LOG;
            terrain.put(c.p,c.y+1,cap.defaultBlockState(),palette);
        }
        int depth=timber?(bent && c.edgeTile()?c.y-terrain.level.getMinBuildHeight():bent || c.edgeTile()?1:0)
                :bent?c.y-terrain.level.getMinBuildHeight():bay.soffit(c.t);
        if (terrain.crossesAboveStreet(c.p,c.ground.y(),c.y)) depth=Math.min(depth,1);
        for (int d=1;d<=depth;d++) {
            var pos=new BlockPos(c.p.x(),c.y-d,c.p.z());
            var original=terrain.level.getBlockState(pos);
            boolean wet=original.getFluidState().is(RoadTerrain.WATER);
            if (original.hasBlockEntity() || !(original.isAir() || wet || original.is(RoadTerrain.ICE) || RoadTerrain.canClear(original))
                    || terrain.protectedAt(c.p,pos.getY(),pos.getY(),c.path instanceof Spur,c.streetVerge)) break;
            BlockState state;
            if (timber) {
                Direction.Axis longitudinal=c.path.eastWest()?Direction.Axis.X:Direction.Axis.Z;
                Direction.Axis transverse=c.path.eastWest()?Direction.Axis.Z:Direction.Axis.X;
                var axis=d>1?Direction.Axis.Y:bent?transverse:longitudinal;
                state=Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,axis);
            } else if (!bent && d==depth && bay.bevel(c.t)) {
                var spring=direction(c.path.eastWest(),c.t-bay.left()<bay.right()-c.t?-1:1);
                state=Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF,Half.TOP)
                        .setValue(StairBlock.FACING,spring).setValue(StairBlock.WATERLOGGED,wet);
            } else {
                boolean backing=c.edgeTile() && !bent && d+1<depth || bent && !c.edgeTile() && d>2;
                Block block=wet && c.edgeTile()?Blocks.MOSSY_COBBLESTONE:backing?Blocks.COBBLESTONE:Blocks.STONE_BRICKS;
                state=block.defaultBlockState();
            }
            terrain.put(c.p,pos.getY(),state,palette);
        }
    }

    private static void mark(RoadTerrain terrain, Marker m) {
        var ground = terrain.column(m.p);
        if (!terrain.clear(m.p, m.y, ground.y())) return;
        if (ground.y()<m.y) terrain.put(m.p,m.y-1,Blocks.COBBLESTONE);
        terrain.put(m.p, m.y, Blocks.MOSSY_COBBLESTONE);
        terrain.put(m.p, m.y + 1, Blocks.OAK_FENCE);
        var pos = new BlockPos(m.p.x(), m.y + 2, m.p.z());
        Direction frontNormal = m.destination == null ? Direction.SOUTH :
                m.destination.eastWest() ? Direction.NORTH : Direction.WEST;
        terrain.put(m.p, pos.getY(), Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION,frontNormal.get2DDataValue()*4));
        List<Component> front, back;
        if (m.destination != null) {
            var s = m.destination;
            int dx = s.village().x() - m.p.x(), dz = s.village().z() - m.p.z();
            int remaining = s.end() - m.p.along(s.eastWest());
            Direction to = Math.abs(remaining) >= 3 ? direction(s.eastWest(), remaining)
                    : Math.abs(dx) >= Math.abs(dz) ? direction(true, dx) : direction(false, dz);
            long distance = Math.round(Math.hypot(dx,dz));
            front = turnSign(s,to,frontNormal.getOpposite(),distance);
            back = turnSign(s,to,frontNormal,distance);
        } else {
            front = crossing("north", "south", "west_east");
            back = crossing("south", "north", "east_west");
        }
        // A protochunk's sign has no Level yet. Store its vanilla NBT for promotion to a live block entity.
        var tag = RoadMaterials.entityTag(pos,terrain.level.getBlockState(pos),terrain.level);
        RoadApi.writeSign(terrain.level, tag, front, back);
        terrain.level.getChunk(pos).setBlockEntityNbt(tag);
    }

    private static List<Component> turnSign(Spur spur, Direction to, Direction viewer, long distance) {
        String arrow = to == viewer ? "↑" : to == viewer.getOpposite() ? "↓" : to == viewer.getClockWise() ? "→" : "←";
        Component line = arrow.equals("→") ? words(to.getName()).copy().append(" →")
                : RoadGame.literal(arrow+" ").append(words(to.getName()));
        return List.of(words(spur.pixies() ? "pixies" : "village"), line, words("distance",distance));
    }

    private static List<Component> crossing(String ahead, String behind, String sides) {
        return List.of(words("crossing"), RoadGame.literal("↑ ").append(words(ahead)),
                words(sides), RoadGame.literal("↓ ").append(words(behind)));
    }

    private static Component words(String key, Object... args) {
        return RoadGame.translated("sign.fourwindsroadworks." + key, args);
    }
}
