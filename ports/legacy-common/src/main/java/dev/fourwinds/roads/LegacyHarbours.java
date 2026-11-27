package dev.fourwinds.roads;

import java.util.LinkedHashMap;
import java.util.Map;
import static dev.fourwinds.roads.RoadLayout.*;
import static dev.fourwinds.roads.LegacyRoads.Material.*;

/** Plan from the unmodified terrain, then build only the part owned by the current population tile. */
final class LegacyHarbours {
    private static final int CELL = 384, WINDOW = 60, MAX_DEPTH = 18;
    private static final int[] SIGNS = {-1, 1};
    private final LegacyRoads network;
    private final Map<Long,Site> lamps = new LinkedHashMap<Long,Site>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,Site> eldest) { return size() > 256; }
    };
    private static final class Site {
        final Road road;
        final int station;
        final PierLayout plan;
        Site(Road road, int station, PierLayout plan) { this.road = road; this.station = station; this.plan = plan; }
    }

    LegacyHarbours(LegacyRoads network) { this.network = network; }

    void build(LegacyRoads.Land land, Road road, int minX, int minZ) {
        int along = road.eastWest() ? minX : minZ;
        int cross = road.eastWest() ? minZ : minX;
        for (int t = along - PierLayout.LENGTH; t <= along + 15 + PierLayout.LENGTH; t++) {
            Point bank = network.on(land, road, t);
            int across = bank.across(road.eastWest());
            if (across < cross - PierLayout.MAX_SIDE || across > cross + 15 - PierLayout.MIN_SIDE || land.wet(bank.x(),bank.z())) continue;
            for (int out : SIGNS) {
                Site site = site(land, road, t, bank, out);
                if (site == null) continue;
                PierLayout p = site.plan;
                boolean tower = elected(land, site);
                int last = tower ? PierLayout.LENGTH : 8;
                boolean blocked = false;
                for (int s = 0; s <= last && !blocked; s++) for (int u = tower ? -12 : -2; u <= (tower ? 12 : 2); u++) {
                    if (tower && p.clearance(s,u) == 0) continue;
                    Point q = p.point(s,u);
                    if (land.occupied(q.x(),p.floor(s),q.z())) { blocked = true; break; }
                }
                if (blocked) continue;
                for (int s = 0; s <= last; s++) for (int u = tower ? -12 : -2; u <= (tower ? 12 : 2); u++) {
                    Point q = p.point(s,u);
                    if (q.x()<minX || q.x()>=minX+16 || q.z()<minZ || q.z()>=minZ+16) continue;
                    boolean boarding = tower && p.boarding(s,u);
                    boolean stone = tower && p.stoneFoot(s,u);
                    boolean platform = tower ? p.platform(s,u) : Math.abs(u) <= (s >= 6 ? 2 : 1);
                    if (!platform && !boarding) continue;
                    int deck = boarding ? p.deck()-1 : p.floor(s);
                    boolean pile = tower ? p.pile(s,u) : (s == 0 || s == 4 || s == 8) && Math.abs(u) == (s == 8 ? 2 : 1);
                    if (pile || stone) for (int y = land.bed(q.x(),q.z())+1; y < deck; y++)
                        land.put(q.x(),y,q.z(),stone ? COBBLE : POST,0,0);
                    land.put(q.x(),deck,q.z(),stone ? STONE : PLANK,0,0);
                    if (!tower && s == 8 && Math.abs(u) == 2) land.put(q.x(),deck+1,q.z(),POST,0,0);
                    if (s > 0 && p.floor(s-1) > deck && Math.abs(u) <= 1)
                        land.put(q.x(),deck+1,q.z(),TIMBER_STAIR,road.eastWest() ? -out : 0,road.eastWest() ? 0 : -out);
                }
                if (tower) land.lighthouse(p,minX,minZ);
            }
        }
    }

    private Site site(LegacyRoads.Land land, Road road, int t, Point bank, int out) {
        Point next = network.on(land,road,t+out);
        if (!land.wet(next.x(),next.z()) || !PierLayout.hasHarbourPriority(road,t,out)) return null;
        int sign = roll(land.seed()+97213,bank.x(),bank.z(),2)==0 ? 1 : -1;
        for (int side : new int[]{sign,-sign}) {
            PierLayout p = new PierLayout(bank,road.eastWest(),out,side,land.sea()-1,network.height(land,road,t));
            if (!PierLayout.suitableShore(p.water(),p.entrance(),
                    d -> wet(land,p.point(d,0)), d -> !wet(land,p.point(d,0)), d -> bed(land,p.point(d,0)),
                    d -> ocean(land,p.point(d,0)) ? PierLayout.Outlet.OPEN_SEA : PierLayout.Outlet.COAST)) continue;
            boolean fit = true;
            for (int s=0; s<=PierLayout.LENGTH && fit; s++) for (int u=PierLayout.MIN_SIDE; u<=PierLayout.MAX_SIDE; u++) {
                if (p.clearance(s,u)==0) continue;
                Point q = p.point(s,u);
                int floor = bed(land,q);
                if (floor>p.floor(s) || floor<p.water()-MAX_DEPTH || s>2 && !wet(land,q)) { fit=false; break; }
            }
            if (fit) return new Site(road,t,p);
        }
        return null;
    }
    private static int bed(LegacyRoads.Land l, Point p) { return l.bed(p.x(),p.z()); }
    private static boolean wet(LegacyRoads.Land l, Point p) { return l.wet(p.x(),p.z()); }
    private static boolean ocean(LegacyRoads.Land l, Point p) { return l.ocean(p.x(),p.z()); }

    // One winning offshore light within each 120-block window leaves at least 264 blocks
    // between neighbouring lights. Dry banks outside the window can still have a working jetty.
    private boolean elected(LegacyRoads.Land land, Site site) {
        Point light = site.plan.point(PierLayout.TOWER,PierLayout.TOWER_SIDE);
        int gx = Math.floorDiv(light.x(),CELL), gz = Math.floorDiv(light.z(),CELL);
        int x = gx*CELL + CELL/2, z = gz*CELL + CELL/2;
        if (Math.abs(light.x()-x)>WINDOW || Math.abs(light.z()-z)>WINDOW) return false;
        long key = ((long)gx<<32) | (gz&0xffffffffL);
        if (!lamps.containsKey(key)) lamps.put(key, winner(land,x,z));
        Site best = lamps.get(key);
        return best!=null && best.road.eastWest()==site.road.eastWest() && best.road.index()==site.road.index()
                && best.station==site.station && best.plan.outward()==site.plan.outward();
    }
    private Site winner(LegacyRoads.Land land, int x, int z) {
        Site best=null;
        int rank=Integer.MAX_VALUE;
        for (boolean ew : new boolean[]{true,false}) {
            int along=ew?x:z, across=ew?z:x;
            int near=Math.floorDiv(across+SPACING/2,SPACING);
            for (int index=near-1;index<=near+1;index++) {
                if (Math.abs(index*SPACING-across)>WINDOW+80) continue;
                Road road=new Road(land.seed(),ew,index);
                for (int t=along-WINDOW-PierLayout.TOWER;t<=along+WINDOW+PierLayout.TOWER;t++) {
                    Point bank=network.on(land,road,t);
                    if (Math.abs(bank.across(ew)-across)>WINDOW+PierLayout.TOWER_SIDE || wet(land,bank)) continue;
                    for (int out:SIGNS) {
                        Site candidate=site(land,road,t,bank,out);
                        if (candidate==null) continue;
                        Point light=candidate.plan.point(PierLayout.TOWER,PierLayout.TOWER_SIDE);
                        if (Math.abs(light.x()-x)>WINDOW || Math.abs(light.z()-z)>WINDOW) continue;
                        int score=roll(land.seed()+84012,light.x(),light.z(),100000);
                        if (score<rank) { rank=score;best=candidate; }
                    }
                }
            }
        }
        return best;
    }
}
