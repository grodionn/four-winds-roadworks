package dev.fourwinds.roads;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.fourwinds.roads.RoadLayout.*;

/** Road construction for the pre-Caves & Cliffs terrain and population pipeline. */
final class LegacyRoads {
    enum Material { AIR, STONE, DRESSED, COBBLE, WORN, GRAVEL, PATH, EARTH, SLIM_POST, FILL, PLANK, POST, BEAM, LOW_STONE, LOW_TIMBER, STAIR, TIMBER_STAIR }
    interface Land {
        long seed();
        int sea();
        int bed(int x, int z);
        boolean wet(int x, int z);
        boolean ocean(int x, int z);
        boolean natural(int x, int y, int z);
        boolean occupied(int x, int y, int z);
        boolean firm(int x,int y,int z);
        boolean openForStop(int x,int y,int z);
        void put(int x, int y, int z, Material material, int dx, int dz);
        void waypost(int x, int y, int z, Spur destination);
        void lighthouse(PierLayout plan, int minX, int minZ);
        List<Point> villages(int x, int z);
    }
    private static final class Cache<V> extends LinkedHashMap<Long, V> {
        Cache() { super(128, 0.75f, true); }
        @Override protected boolean removeEldestEntry(Map.Entry<Long, V> eldest) { return size() > 2048; }
    }
    private final LegacyHarbours harbours = new LegacyHarbours(this);
    private final Cache<RoadSection> east = new Cache<>(), north = new Cache<>();
    private final Cache<RoadSection.Junction> nodes = new Cache<>();
    private final Map<Path,Map<Integer,Integer>> contours = new HashMap<>();
    private final Map<Path,Map<Integer,BridgeBay>> drySpans = new HashMap<>();

    private static long pair(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }
    private static int grade(Land land, int x, int z) {
        return land.wet(x,z) ? Math.max(land.bed(x,z),land.sea()+1) : land.bed(x,z);
    }
    private RoadSection.Junction junction(Land land, int x, int z) {
        return nodes.computeIfAbsent(pair(x,z), k -> RoadSection.survey(x*SPACING,z*SPACING,land.sea(),(a,b)->grade(land,a,b)));
    }
    private RoadSection.Junction junction(Land land, Road road, int span) {
        return road.eastWest() ? junction(land,span,road.index()) : junction(land,road.index(),span);
    }
    private RoadSection section(Land land, Road road, int t) {
        int span=Math.floorDiv(t,SPACING), phase=Math.floorMod(t,SPACING);
        if (phase<RoadSection.JUNCTION_SHIFT && t<junction(land,road,span).point().along(road.eastWest())) span--;
        else if (phase>=SPACING-RoadSection.JUNCTION_SHIFT && t>=junction(land,road,span+1).point().along(road.eastWest())) span++;
        final int s=span;
        return (road.eastWest()?east:north).computeIfAbsent(pair(road.index(),s), k ->
                new RoadSection(road,junction(land,road,s),junction(land,road,s+1),land.sea(),(a,b)->grade(land,a,b),
                        java.util.Collections.emptyList(),(a,b)->land.wet(a,b)?1:0));
    }
    private int center(Land land, Path path, int t) {
        return path instanceof Road ? section(land,(Road)path,t).center((Road)path,t) : path.center(t);
    }
    private int plannedHeight(Land land, Path path, int t) {
        if (path instanceof Road) return section(land,(Road)path,t).elevation(t);
        int a=t-Math.floorMod(t,4);
        Point first=path.point(a,0), last=path.point(a+4,0);
        return RoadSection.interpolate(grade(land,first.x(),first.z()),grade(land,last.x(),last.z()),t-a,4);
    }
    private int contour(Land land,Path path,int t) {
        int planned=plannedHeight(land,path,t);
        if (!path.contains(t)) return planned;
        if (path instanceof Road) {
            Road road=(Road)path;
            int station=junction(land,road,Math.floorDiv(t+SPACING/2,SPACING)).point().along(path.eastWest());
            if (Math.abs(t-station)<=CONTOUR_STEP+3) return planned;
        } else {
            Spur spur=(Spur)path;
            if (Math.min(Math.abs(t-spur.turn()),Math.abs(t-spur.end()))<=CONTOUR_STEP) return planned;
        }
        Point p=on(land,path,t);
        if (land.wet(p.x(),p.z())) return planned;
        return groundedKnot(planned,plannedHeight(land,path,t-CONTOUR_STEP),
                plannedHeight(land,path,t+CONTOUR_STEP),land.bed(p.x(),p.z()));
    }
    int height(Land land,Path path,int t) {
        int a=Math.floorDiv(t,CONTOUR_STEP)*CONTOUR_STEP,b=a+CONTOUR_STEP;
        Map<Integer,Integer> knots=contours.computeIfAbsent(path,p->new HashMap<>());
        int left=knots.computeIfAbsent(a,k->contour(land,path,k)),right=knots.computeIfAbsent(b,k->contour(land,path,k));
        if (left==plannedHeight(land,path,a) && right==plannedHeight(land,path,b)) return plannedHeight(land,path,t);
        return RoadSection.interpolate(left,right,t-a,CONTOUR_STEP);
    }
    Point on(Land land, Path path, int t) {
        int across=center(land,path,t);
        return path.eastWest()?new Point(t,across):new Point(across,t);
    }
    private boolean wet(Land land, Path path, int t) {
        Point p=on(land,path,t);
        return land.wet(p.x(),p.z());
    }
    private static boolean inTile(int x, int z, int minX, int minZ) {
        return x>=minX && x<minX+16 && z>=minZ && z<minZ+16;
    }

    void generate(Land land, int minX, int minZ) {
        contours.clear();
        drySpans.clear();
        Point middle=new Point(minX+8,minZ+8);
        Road ew=band(land.seed(),true,middle), ns=band(land.seed(),false,middle);
        java.util.ArrayList<Path> paths=new java.util.ArrayList<>();
        if (Math.abs(middle.z()-ew.index()*SPACING)<=74) paths.add(ew);
        if (Math.abs(middle.x()-ns.index()*SPACING)<=74) paths.add(ns);
        for (Point village:land.villages(middle.x(),middle.z())) {
            Road er=nearest(land.seed(),true,village), nr=nearest(land.seed(),false,village);
            int dz=center(land,er,village.x())-village.z(), dx=center(land,nr,village.z())-village.x();
            boolean alongX=Math.abs(dx)<Math.abs(dz);
            int delta=alongX?dx:dz;
            if (Math.abs(delta)<=14 || Math.abs(delta)>106) continue;
            Point door=new Point(village.x()+(alongX?8*Integer.signum(delta):0),village.z()+(alongX?0:8*Integer.signum(delta)));
            Spur original=toRoad(land.seed(),door,alongX,village,false);
            int turn=center(land,alongX?nr:er,original.across());
            Spur link=new Spur(alongX,turn,original.end(),original.across(),original.bend(),village,false);
            if (link.near(middle,24)) paths.add(link);
        }
        for(int z=minZ;z<minZ+16;z++) for(int x=minX;x<minX+16;x++) {
            Path best=null;int off=0,tile=0;
            for(Path path:paths) {
                int t=path.eastWest()?x:z, across=path.eastWest()?z:x;
                if (!path.contains(t)) continue;
                int offset=across-center(land,path,t);
                if (offset<edge(path.width(),-1)-2 || offset>edge(path.width(),1)+2) continue;
                boolean inside=depth(path.width(),offset)>=0;
                if (best==null || inside && depth(best.width(),off)<0 ||
                        inside==(depth(best.width(),off)>=0) && path.width()>best.width()) {
                    best=path;off=offset;tile=t;
                }
            }
            if (best!=null) buildColumn(land,best,tile,x,z,off);
        }
        for (Path path : paths) if (path instanceof Road) {
            Road road = (Road)path;
            harbours.build(land, road, minX, minZ);
            LegacyRoadside.place(this, land, road, minX, minZ, paths);
        }
        if (paths.contains(ew) && paths.contains(ns)) {
            RoadSection.Junction j=junction(land,ns.index(),ew.index());
            int x=j.point().x()+5,z=j.point().z()+5;
            if (inTile(x,z,minX,minZ)) marker(land,x,z,null);
        }
        for (Path path:paths) if (path instanceof Spur) {
            Spur spur=(Spur)path;
            Point p=spur.point(spur.signTurn(),3);
            if (inTile(p.x(),p.z(),minX,minZ)) marker(land,p.x(),p.z(),spur);
        }
    }

    private void marker(Land land,int x,int z,Spur target) {
        int bed=land.bed(x,z);
        if (land.wet(x,z) || land.occupied(x,bed,z) || !land.natural(x,bed+1,z)) return;
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) if (Math.abs(land.bed(x+dx,z+dz)-bed)>1) return;
        land.waypost(x,bed+1,z,target);
    }

    private boolean gap(Land land,Path path,int t) {
        if (!path.contains(t)) return false;
        Point p=on(land,path,t);
        return !land.wet(p.x(),p.z()) && height(land,path,t)-land.bed(p.x(),p.z())>4;
    }

    private BridgeBay drySpan(Land land,Path path,int t) {
        Map<Integer,BridgeBay> rows=drySpans.computeIfAbsent(path,p->new HashMap<>());
        if (!rows.containsKey(t)) {
            BridgeBay bay=null;
            if (gap(land,path,t)) {
                int l=t,r=t;
                while (t-l<=24 && gap(land,path,l-1)) l--;
                while (r-t<=24 && gap(land,path,r+1)) r++;
                bay=l==r?new BridgeBay(t,t+1):BridgeBay.at(t,l,r,r-l<=24,path.width()<5,0);
            }
            rows.put(t,bay);
        }
        return rows.get(t);
    }

    private void spanColumn(Land land,Path path,int x,int z,int t,int offset,int depth,int bed,int deck,
                            BridgeBay bay,boolean timber,boolean rail) {
        int bottom=timber ? (bay.support(t)&&depth==0?bed:deck-1) : bay.masonryPier(t)?bed:Math.max(bed,deck-bay.soffit(t));
        for(int y=Math.max(1,bottom);y<deck;y++) put(land,x,y,z,timber?(y<deck-1?Material.POST:Material.BEAM):Material.STONE,path,1);
        boolean bearing=bay.support(t) && bay.right()-bay.left()>=5;
        put(land,x,deck,z,timber?Material.PLANK:bearing?Material.DRESSED:Material.STONE,path,1);
        clear(land,x,deck+1,z,4);
        if (rail && depth==0 && (path.width()>=4 || offset<0))
            put(land,x,deck+1,z,timber?Material.LOW_TIMBER:
                    bearing?Material.STONE:Material.LOW_STONE,path,1);
    }

    private void buildColumn(Land land,Path path,int t,int x,int z,int offset) {
        int bed=land.bed(x,z),deck=height(land,path,t),depth=depth(path.width(),offset);
        if (land.occupied(x,deck,z)) return;
        boolean water=wet(land,path,t);
        if (water) {
            int left=t, right=t;
            while (t-left<=56 && wet(land,path,left)) left--;
            while (right-t<=56 && wet(land,path,right)) right++;
            if (right-left>56 || land.ocean(x,z) || depth<0) return;
            // A dry bank may already carry a raised approach. Tie into its finished walking
            // height, even if a short wet channel lies between the two outer timber bents.
            int ay=Math.max(land.sea()+1,height(land,path,left)),by=Math.max(land.sea()+1,height(land,path,right));
            if (Math.abs(ay-by)>(right-left+1)/2) return;
            deck=RoadSection.interpolate(ay,by,t-left,right-left);
            if (deck-bed>22) return;
            boolean timber=path.width()<5 && right-left<=20;
            BridgeBay bay=BridgeBay.at(t,left,right,true,timber,0);
            spanColumn(land,path,x,z,t,offset,depth,bed,deck,bay,timber,right-left>7 || gap(land,path,left) || gap(land,path,right));
            return;
        }
        if (deck<3 || deck>245) return;
        BridgeBay crossing=drySpan(land,path,t);
        if (crossing!=null && depth>=0) {
            spanColumn(land,path,x,z,t,offset,depth,bed,deck,crossing,path.width()<5,true);
            return;
        }
        if (land.wet(x,z)) return;
        boolean bore=bed>=deck+8;
        if (depth<0) {
            if (bore && depth>=-2) {
                int h=vaultClearance(depth,true);
                clear(land,x,deck+1,z,h);
                if (depth==-2 && Math.floorMod(t,7)==0) for(int y=deck+1;y<=deck+h;y++) put(land,x,y,z,Material.POST,path,1);
            }
            return;
        }
        Tread tread=path.width()<5 && !bore?track(land.seed(),path,t,offset):null;
        if (depth==0 && !bore && (deck-bed>3 || tread==Tread.TURF && bed<=deck+1)) return;
        if (!bore && deck<=bed && land.firm(x,deck,z) && land.openForStop(x,deck-1,z)) {
            clear(land,x,deck+1,z,Math.max(5,bed-deck+1));
            return;
        }
        Material fill=tread!=null && deck-bed<=4?Material.EARTH:Material.FILL;
        for(int y=bed+1;y<deck;y++) put(land,x,y,z,fill,path,1);
        int wear=wear(land.seed(),path,t);
        Material mat=path.width()==5 ? (depth==0?Material.COBBLE:wear<32?Material.WORN:Material.STONE)
                : tread==Tread.GRAVEL?Material.GRAVEL:tread==Tread.PATH?Material.PATH:tread==null?Material.COBBLE:Material.EARTH;
        put(land,x,deck,z,mat,path,1);
        int ceiling=bore?vaultClearance(depth,true):Math.max(5,bed-deck+1);
        clear(land,x,deck+1,z,ceiling);
        if (bore && Math.floorMod(t,7)==0) put(land,x,deck+ceiling,z,Material.BEAM,path,1);
        int ahead=height(land,path,t+1),back=height(land,path,t-1);
        if (ahead==deck+1 || back==deck+1) put(land,x,deck+1,z,Material.STAIR,path,ahead>deck?1:-1);
    }
    private static void clear(Land land,int x,int first,int z,int n) {
        for(int y=first;y<first+n;y++) if (land.natural(x,y,z)) land.put(x,y,z,Material.AIR,0,0);
    }
    private static void put(Land land,int x,int y,int z,Material mat,Path path,int direction) {
        if (land.natural(x,y,z)) land.put(x,y,z,mat,path.eastWest()?direction:0,path.eastWest()?0:direction);
    }
}
