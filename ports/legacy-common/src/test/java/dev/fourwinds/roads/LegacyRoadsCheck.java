package dev.fourwinds.roads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import static dev.fourwinds.roads.RoadLayout.*;

public final class LegacyRoadsCheck {
    private static class Coast implements LegacyRoads.Land {
        final Map<Long, LegacyRoads.Material> blocks = new HashMap<>();
        final Set<Point> lights = new HashSet<>();
        int tileX, tileZ;
        boolean reserve;

        public long seed() { return 38473917L; }
        public int sea() { return 63; }
        public boolean wet(int x, int z) { return Math.abs(x) > 228; }
        public boolean ocean(int x, int z) { return wet(x,z); }
        public int bed(int x, int z) { return wet(x,z) ? 57 : 64; }
        public boolean occupied(int x, int y, int z) { return reserve && x > 228; }
        public boolean natural(int x, int y, int z) { return true; }
        public List<Point> villages(int x, int z) { return Collections.emptyList(); }

        private long position(int x, int y, int z) {
            return ((long)(x & 0x3ffffff) << 38) | ((long)(z & 0x3ffffff) << 12) | y;
        }
        public void put(int x, int y, int z, LegacyRoads.Material material, int dx, int dz) {
            require(x >= tileX && x < tileX + 16 && z >= tileZ && z < tileZ + 16, "wrote outside population tile");
            require(!occupied(x,y,z), "overwrote another structure");
            blocks.put(position(x,y,z), material);
        }
        public boolean firm(int x,int y,int z) { return y<=bed(x,z); }
        public boolean openForStop(int x,int y,int z) { return y>bed(x,z); }
        public void waypost(int x, int y, int z, Spur destination) { }
        public void lighthouse(PierLayout pier, int x, int z) {
            Point centre = pier.point(PierLayout.TOWER, PierLayout.TOWER_SIDE);
            if (centre.x() >= x && centre.x() < x+16 && centre.z() >= z && centre.z() < z+16) lights.add(centre);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Coast generate(List<Point> tiles, boolean reserve) {
        Coast land = new Coast();
        land.reserve = reserve;
        LegacyRoads roads = new LegacyRoads();
        for (Point tile : tiles) {
            land.tileX = tile.x();
            land.tileZ = tile.z();
            roads.generate(land,land.tileX,land.tileZ);
        }
        return land;
    }

    private static void checkRavine() {
        Coast land=new Coast() {
            @Override public int bed(int x,int z) { return x>=66 && x<=70?50:70; }
            @Override public boolean wet(int x,int z) { return false; }
            @Override public boolean firm(int x,int y,int z) { return !(x==83 && y==69) && super.firm(x,y,z); }
            @Override public boolean openForStop(int x,int y,int z) { return x==83 && y==69 || super.openForStop(x,y,z); }
        };
        LegacyRoads roads=new LegacyRoads();
        Road lane=new Road(land.seed(),true,1);
        for (int x=48;x<=80;x+=16) for (int z=96;z<=240;z+=16) {
            land.tileX=x;land.tileZ=z;
            roads.generate(land,x,z);
        }
        for (int x=66;x<=70;x++) {
            Point p=roads.on(land,lane,x);
            int y=roads.height(land,lane,x);
            require(land.blocks.get(land.position(x,y,p.z()))==LegacyRoads.Material.PLANK,
                    "missing narrow crossing at "+x+", "+y+", "+p.z()+": "+land.blocks.get(land.position(x,y,p.z())));
            for (int off:new int[]{-2,1}) {
                if (x==66 || x==70) {
                    for (int h=51;h<y-1;h++)
                        require(land.blocks.get(land.position(x,h,p.z()+off))==LegacyRoads.Material.POST,"suspended trestle");
                } else require(land.blocks.get(land.position(x,52,p.z()+off))==null,"gap filled with a solid wall");
            }
            require(land.blocks.get(land.position(x,y,p.z()+2))==null,"lane widened on crossing");
        }
        Point lip=roads.on(land,lane,83);
        require(roads.height(land,lane,83)==70 && !land.blocks.containsKey(land.position(83,70,lip.z())),
                "replaced the last natural block of a cave roof with a dirt track");
    }

    public static void main(String[] args) {
        checkRavine();
        List<Point> tiles = new ArrayList<>();
        for (int offset : new int[]{-256, 192}) {
            for (int x = offset; x < offset+64; x += 16)
                for (int z = 112; z < 256; z += 16) tiles.add(new Point(x,z));
        }
        // Check both the aligned 1.16.5 population grid and the eight-block offset on 1.12.2.
        for (int shift : new int[]{0,8}) {
            List<Point> grid = new ArrayList<>();
            for (Point tile : tiles) grid.add(new Point(tile.x()+shift,tile.z()+shift));
            Coast coast = generate(grid,false);
            require(coast.lights.size() == 2, "missing lighthouse on an eligible coast " + coast.lights.size());
            Point[] lights = coast.lights.toArray(new Point[0]);
            require(lights[0].distanceTo(lights[1]) >= 264, "adjacent lighthouses");
            for (Point light : lights) {
                long deck = coast.position(light.x(), 64, light.z());
                require(coast.blocks.get(deck) == LegacyRoads.Material.STONE, "missing tower foundation");
                boolean joined = false;
                for (int z = light.z()-6; z <= light.z()+6; z++) {
                    boolean continuous = true;
                    int facing = Integer.signum(light.x());
                    for (int d = -2; d <= 7; d++) {
                        int x = facing * (228 + d);
                        LegacyRoads.Material surface = coast.blocks.get(coast.position(x,64,z));
                        if (surface == null || surface == LegacyRoads.Material.AIR) continuous = false;
                    }
                    joined |= continuous;
                }
                require(joined, "pier has no continuous approach");
            }
            Collections.shuffle(grid,new Random(903));
            Coast shuffled = generate(grid,false);
            require(shuffled.blocks.equals(coast.blocks) && shuffled.lights.equals(coast.lights), "chunk-order seam");
            Coast reserved = generate(grid,true);
            require(reserved.lights.size() == 1, "harbour overlapped a reserved structure");
        }
        System.out.println("Classic crossings, coasts, foundations, approaches, spacing and population order passed.");
    }
}
