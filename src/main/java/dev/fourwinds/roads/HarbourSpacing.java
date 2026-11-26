package dev.fourwinds.roads;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import static dev.fourwinds.roads.RoadLayout.*;

/** Elect a headland from the seed terrain before any of its neighbours are decorated. */
final class HarbourSpacing {
    static final int MIN_DISTANCE=256;
    private static final int GRID=32, REACH=24, TOLERANCE=20;
    private record Slot(boolean eastWest,int index,int t) {
        Road road(long seed) { return new Road(seed,eastWest,index); }
    }
    private record Site(Slot slot,Point center,int shore,int outward,int rank) {}
    private static final Site NONE=new Site(null,null,0,0,0);
    private static final class Coast {
        final Cache<Long,Integer> depths=CacheBuilder.newBuilder().maximumSize(32768).build();
        final Cache<Slot,Site> sites=CacheBuilder.newBuilder().maximumSize(8192).build();
        final Cache<Slot,Boolean> winners=CacheBuilder.newBuilder().maximumSize(4096).build();
    }
    private static final Map<ServerLevel,Coast> WORLDS=new WeakHashMap<>();
    private final RoadTerrain terrain;
    private final Coast coast;
    private HarbourSpacing(RoadTerrain terrain) {
        this.terrain=terrain;
        synchronized (WORLDS) { coast=WORLDS.computeIfAbsent(terrain.level.getLevel(),k->new Coast()); }
    }

    static boolean lighthouse(RoadTerrain terrain,Road road,int t,PierLayout pier) {
        var chart=new HarbourSpacing(terrain);
        Point lamp=pier.point(PierLayout.TOWER,PierLayout.TOWER_SIDE);
        for (int a=Math.floorDiv(t-REACH-GRID/2,GRID)*GRID;a<=t+REACH+GRID/2;a+=GRID) {
            var site=chart.site(new Slot(road.eastWest(),road.index(),a));
            if (site==NONE || site.outward!=pier.outward() || lamp.distanceTo(site.center)>TOLERANCE || !chart.winner(site)) continue;
            // The slot gets exactly one land-to-sea transition, including at islands and divided coves.
            int nearest=Integer.MIN_VALUE,best=REACH+1;
            for (int i=site.shore-REACH;i<=site.shore+REACH;i++) {
                int d=Math.abs(i-site.shore);
                if (d>=best) continue;
                int out=site.outward;
                if (chart.water(road,i) && !chart.water(road,i-out) && !chart.water(road,i-4*out)
                        && chart.water(road,i+4*out) && chart.water(road,i+12*out)) { nearest=i;best=d; }
            }
            if (nearest==t) return true;
        }
        return false;
    }
    private boolean water(Road road,int t) {
        int h=ShoreTerrain.surface(terrain.level,road.point(t,0));
        return h!=Integer.MIN_VALUE && (h&1)!=0 && (h>>1)<=terrain.level.getSeaLevel();
    }
    private Site site(Slot slot) {
        var cached=coast.sites.getIfPresent(slot);
        if (cached!=null) return cached;
        var road=slot.road(terrain.level.getSeed());
        int water=terrain.level.getSeaLevel()-1;
        Site site=NONE;
        for (int out:new int[]{-1,1}) {
            boolean marine=false;
            for (int d:new int[]{12,28,44}) {
                Point p=road.point(slot.t+out*d,0);
                if (terrain.seaLevelBiome(p.x(),p.z()).is(RoadTerrain.SEA)) { marine=true;break; }
            }
            if (!marine) continue;
            int first=slot.t-GRID/2,last=slot.t+GRID/2;
            // A real wharf needs a dry bank, a boat lane and a shelf shallow enough to carry its piles.
            for (int t=first;t<last;t++) {
                if (bed(road,t,0)>=water || bed(road,t-out,0)<water) continue;
                boolean open=false;
                for (int d=16;d<=36;d+=4) {
                    Point p=road.point(t+out*d,0);
                    if (terrain.seaLevelBiome(p.x(),p.z()).is(RoadTerrain.SEA)) open=true;
                }
                if (!open || bed(road,t-8*out,0)<water || bed(road,t-16*out,0)<water) continue;
                int penalty=0;
                for (int step:new int[]{4,8,12,16,24,32}) {
                    int depth=water-bed(road,t+step*out,0);
                    if (depth<1 || step<=16 && depth>18) { penalty=10000;break; }
                    if (step<=16) penalty+=Math.max(0,depth-12);
                }
                if (penalty==10000) continue;
                Point shore=road.point(t,0);
                for (int u:new int[]{-7,7}) {
                    Point edge=road.eastWest()?new Point(shore.x()+10*out,shore.z()+u):new Point(shore.x()+u,shore.z()+10*out);
                    int depth=water-terrain.seabed(edge);
                    penalty+=Math.max(0,1-depth)*8+Math.max(0,depth-16)*4;
                }
                int slope=Math.max(0,bed(road,t-4*out,0)-water-4);
                int score=4*penalty+slope*12+(road.width()==5?0:8)+roll(road.seed()+40841,shore.x(),shore.z(),26);
                Point center=road.eastWest()?new Point(shore.x()+out*PierLayout.TOWER,shore.z()):
                        new Point(shore.x(),shore.z()+out*PierLayout.TOWER);
                if (site==NONE || score<site.rank) site=new Site(slot,center,t,out,score);
            }
        }
        coast.sites.put(slot,site);return site;
    }
    private int bed(Road road,int t,int off) {
        Point p=road.point(t,off);
        long key=((long)p.x()<<32)|Integer.toUnsignedLong(p.z());
        Integer saved=coast.depths.getIfPresent(key);
        if (saved!=null) return saved;
        int height=terrain.seabed(p);coast.depths.put(key,height);return height;
    }
    private boolean winner(Site site) {
        var cached=coast.winners.getIfPresent(site.slot);
        if (cached!=null) return cached;
        boolean decided=firstOnCoast(site);
        coast.winners.put(site.slot,decided);return decided;
    }
    private boolean firstOnCoast(Site own) {
        int range=MIN_DISTANCE+2*TOLERANCE,search=range+GRID/2+PierLayout.TOWER+MAX_BEND;
        for (boolean ew:new boolean[]{true,false}) {
            int t=own.center.along(ew),cross=own.center.across(ew);
            int first=Math.floorDiv(cross-search,SPACING);
            for (int index=first;index*SPACING<=cross+search;index++) {
                for (int a=Math.floorDiv(t-search,GRID)*GRID;a<=t+search;a+=GRID) {
                    Slot key=new Slot(ew,index,a);
                    if (key.equals(own.slot)) continue;
                    Point p=key.road(terrain.level.getSeed()).point(a,0);
                    if (p.distanceTo(own.center)>range+GRID/2+PierLayout.TOWER) continue;
                    Site other=site(key);
                    if (other!=NONE && other.center.distanceTo(own.center)<range && (other.rank<own.rank || other.rank==own.rank && precedes(key,own.slot))) return false;
                }
            }
        }
        return true;
    }
    private static boolean precedes(Slot a,Slot b) {
        if (a.eastWest!=b.eastWest) return a.eastWest;
        return a.index!=b.index?a.index<b.index:a.t<b.t;
    }
}
