package dev.fourwinds.roads;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import static dev.fourwinds.roads.RoadLayout.*;

final class RoadGrade {
    private static final int GRID=4;
    private record SectionKey(boolean eastWest,int index,int span) {}
    private static final class Nodes {
        final Cache<Long,java.util.List<RoadSection.Parcel>> parcels=CacheBuilder.newBuilder().maximumSize(2048).build();
        final Cache<Long,Integer> heights=CacheBuilder.newBuilder().maximumSize(65536).build();
        final Cache<Long,RoadSection.Junction> junctions=CacheBuilder.newBuilder().maximumSize(8192).build();
        final Cache<SectionKey,RoadSection> sections=CacheBuilder.newBuilder().maximumSize(2048).build();
    }
    private static final Map<ServerLevel,Nodes> WORLDS=new WeakHashMap<>();
    private final ServerLevel level;
    private final WorldGenRegion region;
    private final Nodes nodes;
    private final int floor;
    private final Map<SectionKey,RoadSection> local=new HashMap<>();
    private final Map<Road,Road> roads=new HashMap<>();
    private final Map<Path,Map<Integer,Integer>> spurHeights=new HashMap<>();

    RoadGrade(WorldGenRegion region) {
        this.region=region;level=region.getLevel();
        synchronized (WORLDS) { nodes=WORLDS.computeIfAbsent(level,k->new Nodes()); }
        floor=level.getChunkSource().getGenerator() instanceof FlatLevelSource?level.getMinBuildHeight():level.getSeaLevel()+1;
    }

    Road follow(Road road) {
        return roads.computeIfAbsent(road,r->new Road(r.seed(),r.eastWest(),r.index(),t->section(r,t).center(r,t)));
    }

    Spur connect(Spur spur) {
        Point door=spur.point(spur.end(),0);
        Road trunk=follow(nearest(level.getSeed(),!spur.eastWest(),door));
        int turn=trunk.center(spur.across());
        if (turn==spur.turn()) return spur;
        int maxBend=Math.abs(turn-spur.end())/4;
        return new Spur(spur.eastWest(),turn,spur.end(),spur.across(),Math.clamp(spur.bend(),-maxBend,maxBend),spur.village(),spur.pixies());
    }

    int along(Path path,int t) {
        if (path instanceof Road road) return section(road,t).elevation(t);
        int f=Math.floorMod(t,GRID),start=t-f;
        return RoadSection.interpolate(spurHeight(path,start),spurHeight(path,start+GRID),f,GRID);
    }

    private int spurHeight(Path path,int t) {
        return spurHeights.computeIfAbsent(path,k->new HashMap<>()).computeIfAbsent(t,k->sampleSpur(path,k));
    }

    private int sampleSpur(Path path,int t) {
        Point p=path.point(t,0);
        int y=at(p.x(),p.z());
        // Village lanes are short. Limit their descent using the terrain along the actual lane.
        for (int d=GRID;floor+d<y;d+=GRID) {
            Point a=path.point(t-d,0),b=path.point(t+d,0);
            y=Math.min(y,Math.min(at(a.x(),a.z()),at(b.x(),b.z()))+d);
        }
        return y;
    }

    private RoadSection section(Road road,int t) {
        int nominal=Math.floorDiv(t,SPACING),phase=Math.floorMod(t,SPACING);
        if (phase<RoadSection.VILLAGE_SHIFT && t<junction(road,nominal).point().along(road.eastWest())) nominal--;
        else if (phase>=SPACING-RoadSection.VILLAGE_SHIFT && t>=junction(road,nominal+1).point().along(road.eastWest())) nominal++;
        var key=new SectionKey(road.eastWest(),road.index(),nominal);
        return local.computeIfAbsent(key,k->{
            var old=nodes.sections.getIfPresent(k);
            if (old!=null) return old;
            int a=k.span*SPACING+SPACING/2,b=k.index*SPACING;
            var plots=VillageLinks.parcels(region,k.eastWest?a:b,k.eastWest?b:a,k.eastWest?160:72,k.eastWest?72:160,nodes.parcels);
            var made=new RoadSection(road,junction(road,k.span),junction(road,k.span+1),level.getSeaLevel(),this::at,plots);
            nodes.sections.put(k,made);return made;
        });
    }

    RoadSection.Junction junction(Road road,int number) {
        int x=(road.eastWest()?number:road.index())*SPACING,z=(road.eastWest()?road.index():number)*SPACING;
        long key=((long)x<<32)|Integer.toUnsignedLong(z);
        var cached=nodes.junctions.getIfPresent(key);
        if (cached!=null) return cached;
        var plots=VillageLinks.parcels(region,x,z,88,88,nodes.parcels);
        var made=RoadSection.survey(x,z,level.getSeaLevel(),this::raw,plots);
        nodes.junctions.put(key,made);return made;
    }

    int at(int x,int z) {
        int dx=Math.floorMod(x,GRID),dz=Math.floorMod(z,GRID),a=x-dx,b=z-dz;
        int h=RoadSection.interpolate(raw(a,b),raw(a+GRID,b),dx,GRID);
        if (dz==0) return h;
        return RoadSection.interpolate(h,RoadSection.interpolate(raw(a,b+GRID),raw(a+GRID,b+GRID),dx,GRID),dz,GRID);
    }

    private int raw(int x,int z) {
        long key=((long)x<<32)|Integer.toUnsignedLong(z);
        Integer saved=nodes.heights.getIfPresent(key);
        if (saved!=null) return saved;
        var source=level.getChunkSource();
        int h=source.getGenerator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,source.randomState())-1;
        h=Math.clamp(h,floor,level.getMaxBuildHeight()-9);
        nodes.heights.put(key,h);return h;
    }
}
