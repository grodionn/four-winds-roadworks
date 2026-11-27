package dev.fourwinds.roads;

final class RoadLayout {
    static final int SPACING = 176;
    static final int START_RADIUS = 8;
    static final int MAX_BEND = 23;

    static final class Point {
        private final int x;
        private final int z;

        Point(int x, int z) {
            this.x = x;
            this.z = z;
        }
        public int x() { return x; }
        public int z() { return z; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Point)) return false;
            Point rhs = (Point)other;
            return x == rhs.x
                && z == rhs.z;
        }

        @Override public int hashCode() {
            int hash = x;
            hash = 31 * hash + z;
            return hash;
        }

        int along(boolean eastWest) { return eastWest ? x : z; }
        int across(boolean eastWest) { return eastWest ? z : x; }
        int distanceTo(Point p) { return Math.max(Math.abs(x - p.x), Math.abs(z - p.z)); }
    }

    interface Path {
        boolean eastWest();
        int center(int along);
        int width();
        default boolean contains(int along) { return true; }
        default Point point(int along, int offset) {
            int across = center(along) + offset;
            return eastWest() ? new Point(along, across) : new Point(across, along);
        }
    }

    static int clamp(int value, int min, int max) {
        if (min > max) throw new IllegalArgumentException("Empty road height range");
        return Math.max(min, Math.min(max, value));
    }

    static double clamp(double value, double min, double max) {
        if (min > max) throw new IllegalArgumentException("Empty road coordinate range");
        return Math.max(min, Math.min(max, value));
    }

    static int edge(int width, int side) { return side < 0 ? -(width / 2) : (width - 1) / 2; }
    static int depth(int width, int offset) { return Math.min(offset - edge(width,-1), edge(width,1) - offset); }

    static final int CONTOUR_STEP=16;

    // Leave enough run on either side of a lowered station for the road to climb back to its old grade.
    static int groundedKnot(int planned,int left,int right,int ground) {
        int rise=CONTOUR_STEP/2;
        int spare=Math.max(0,rise-Math.max(0,Math.max(left,right)-planned));
        return Math.max(planned-spare,Math.min(planned,ground+1));
    }

    enum Tread { TURF, PATH, EARTH, GRAVEL }

    // A worn track has a connected walking line and an irregular margin. Sample along the route,
    // so neither the width nor a stretch of exposed gravel restarts at a chunk boundary.
    static Tread track(long seed,Path path,int t,int offset) {
        int route=path instanceof Road?((Road)path).index():((Spur)path).across();
        long salt=seed ^ (route*0x9e3779b97f4a7c15L) ^ (path.eastWest()?4637L:7607L);
        int lean=field(salt+51,t,0,17,3);
        int first=path.width()==3 ? (lean<50?-1:0) : (lean<31?-2:lean>69?0:-1);
        int wear=field(salt,t,offset,9,3);
        if (offset<first || offset>first+1) {
            if (depth(path.width(),offset)>0) return Tread.EARTH;
            if (path.width()==3 && (path.center(t-1)-path.center(t)==offset || path.center(t+1)-path.center(t)==offset)) return Tread.EARTH;
            if (offset<first-1 || offset>first+2 || wear<64) return Tread.TURF;
            return Tread.EARTH;
        }
        if (wear>74) return Tread.GRAVEL;
        return wear<35 || offset==first && wear<48 ? Tread.EARTH : Tread.PATH;
    }

    static int field(long seed,int t,int offset,int length,int width) {
        int row=Math.floorDiv(t,length),col=Math.floorDiv(offset,width);
        int a=Math.floorMod(t,length),b=Math.floorMod(offset,width);
        int left=RoadSection.interpolate(roll(seed,row,col,100),roll(seed,row+1,col,100),a,length);
        int right=RoadSection.interpolate(roll(seed,row,col+1,100),roll(seed,row+1,col+1,100),a,length);
        return RoadSection.interpolate(left,right,b,width);
    }

    static int bankHeight(int distance,int natural,int deck,boolean soil) {
        if (natural>deck && soil) {
            int slope=deck+(2*distance+1)/3;
            return Math.max(natural-Math.min(5,7-distance),Math.min(natural,slope));
        }
        if (distance<=2 && deck-natural>distance && deck-natural<=4) return natural+1;
        return natural;
    }

    static int stopStation(Road road,int group) {
        long salt=road.seed()^(road.eastWest()?14387L:19471L);
        int half=roll(salt,road.index(),group,2);
        return group*2*SPACING+half*SPACING+(3+roll(salt+89,road.index(),group,5))*16+7;
    }

    static boolean sustainedCover(int t,java.util.function.IntPredicate cover) {
        if (!cover.test(t)) return false;
        int length=1;
        for (int sign:new int[]{-1,1})
            for (int d=1;length<4 && cover.test(t+sign*d);d++) length++;
        return length>=4;
    }

    // Depth is measured inward from the edge of the open road. Negative depths are the side walks.
    static int vaultClearance(int depth,boolean betweenRibs) {
        return 7+RoadLayout.clamp(depth,-3,1)+(betweenRibs?1:0);
    }

    private static final class Knot {
        private final int t;
        private final int offset;

        Knot(int t, int offset) {
            this.t = t;
            this.offset = offset;
        }

        double slopeTo(Knot next) { return (next.offset-offset) / (double)(next.t-t); }
    }

    static final class Road implements Path {
        private final long seed;
        private final boolean eastWest;
        private final int index;
        private final java.util.function.IntUnaryOperator course;

        Road(long seed, boolean eastWest, int index, java.util.function.IntUnaryOperator course) {
            this.seed = seed;
            this.eastWest = eastWest;
            this.index = index;
            this.course = course;
        }
        public long seed() { return seed; }
        public boolean eastWest() { return eastWest; }
        public int index() { return index; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Road)) return false;
            Road rhs = (Road)other;
            return seed == rhs.seed
                && eastWest == rhs.eastWest
                && index == rhs.index
                && java.util.Objects.equals(course, rhs.course);
        }

        @Override public int hashCode() {
            int hash = Long.hashCode(seed);
            hash = 31 * hash + Boolean.hashCode(eastWest);
            hash = 31 * hash + index;
            hash = 31 * hash + java.util.Objects.hashCode(course);
            return hash;
        }

        Road(long seed,boolean eastWest,int index) { this(seed,eastWest,index,null); }
        @Override public int width() { return Math.floorMod(index, 3) == 0 ? 5 : 4; }

        private Knot knot(int number) {
            int span = Math.floorDiv(number,3), part = Math.floorMod(number,3);
            int start = span * SPACING;
            if (part == 0) return new Knot(start,0);
            long channel = seed ^ (eastWest ? 531L : 961L);
            int shape = roll(channel,index,span,12);
            int side = (shape & 1) == 0 ? 1 : -1;
            int t = (part == 1 ? 40 : 122) + roll(channel + 37,index,number,12);
            int amplitude = 16 + roll(channel + 73,index,number,MAX_BEND-15);
            if (part == 2 && shape < 8) side = -side;
            if (shape >= 8 && part == 1 + (shape & 1)) amplitude -= 10;
            return new Knot(start + t,side * amplitude);
        }

        private static double tangent(Knot a, Knot b, Knot c) {
            double incoming = a.slopeTo(b), outgoing = b.slopeTo(c);
            if (incoming * outgoing <= 0) return 0;
            return Math.copySign(Math.min(Math.abs(incoming), Math.abs(outgoing)), incoming);
        }

        @Override public int center(int along) {
            if (course!=null) return course.applyAsInt(along);
            return seedCenter(along);
        }

        int seedCenter(int along) {
            int first = Math.floorDiv(along,SPACING) * 3;
            if (Math.floorMod(along,SPACING) == 0) return index * SPACING;
            if (along >= knot(first+2).t) first += 2;
            else if (along >= knot(first+1).t) first++;
            Knot a=knot(first), b=knot(first+1);
            int length=b.t-a.t;
            double f=(along-a.t)/(double)length, g=1-f;
            // Shape-preserving Hermite tangents carry a bend through a junction without overshooting either verge.
            double offset = g*g*((1+2*f)*a.offset + f*length*tangent(knot(first-1),a,b))
                    + f*f*((3-2*f)*b.offset - g*length*tangent(a,b,knot(first+2)));
            return index*SPACING + (int)Math.round(offset);
        }
    }

    // Along the spur, t runs from the turnoff on the trunk road to the village entrance.
    static final class Spur implements Path {
        private final boolean eastWest;
        private final int turn;
        private final int end;
        private final int across;
        private final int bend;
        private final Point village;
        private final boolean pixies;

        Spur(boolean eastWest, int turn, int end, int across, int bend, Point village, boolean pixies) {
            this.eastWest = eastWest;
            this.turn = turn;
            this.end = end;
            this.across = across;
            this.bend = bend;
            this.village = village;
            this.pixies = pixies;
        }
        public boolean eastWest() { return eastWest; }
        public int turn() { return turn; }
        public int end() { return end; }
        public int across() { return across; }
        public int bend() { return bend; }
        public Point village() { return village; }
        public boolean pixies() { return pixies; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Spur)) return false;
            Spur rhs = (Spur)other;
            return eastWest == rhs.eastWest
                && turn == rhs.turn
                && end == rhs.end
                && across == rhs.across
                && bend == rhs.bend
                && java.util.Objects.equals(village, rhs.village)
                && pixies == rhs.pixies;
        }

        @Override public int hashCode() {
            int hash = Boolean.hashCode(eastWest);
            hash = 31 * hash + turn;
            hash = 31 * hash + end;
            hash = 31 * hash + across;
            hash = 31 * hash + bend;
            hash = 31 * hash + java.util.Objects.hashCode(village);
            hash = 31 * hash + Boolean.hashCode(pixies);
            return hash;
        }

        boolean inPixieVillage(Point p) {
            return pixies && Math.abs((p.x>>4)-(village.x>>4))<=1 && Math.abs((p.z>>4)-(village.z>>4))<=1;
        }
        @Override public int width() { return 3; }
        @Override public boolean contains(int t) { return t >= Math.min(turn, end) && t <= Math.max(turn, end); }
        @Override public int center(int t) {
            if (turn == end) return across;
            double f = RoadLayout.clamp((t - turn) / (double)(end - turn), 0, 1);
            double arch = 4 * f * (1 - f);
            return across + (int)Math.round(bend * arch * arch);
        }
        int signTurn() { return turn + Integer.signum(end - turn) * Math.min(6, Math.abs(end - turn)); }
        Point sign() { return point(signTurn(),3); }
        boolean near(Point p, int margin) {
            int t = p.along(eastWest);
            return t >= Math.min(turn, end) - margin && t <= Math.max(turn, end) + margin
                    && Math.abs(p.across(eastWest) - across) <= margin + Math.abs(bend);
        }
    }

    static Road band(long seed,boolean eastWest,Point p) {
        return new Road(seed,eastWest,Math.floorDiv(p.across(eastWest)+SPACING/2,SPACING));
    }

    static Road nearest(long seed, boolean eastWest, Point p) {
        int across = p.across(eastWest), along = p.along(eastWest);
        int index = Math.floorDiv(across, SPACING);
        Road a = new Road(seed, eastWest, index), b = new Road(seed, eastWest, index + 1);
        return Math.abs(a.center(along) - across) <= Math.abs(b.center(along) - across) ? a : b;
    }

    // Interpolate maintenance along the route, so a worn stretch survives bends and chunk boundaries.
    static int wear(long seed, Path path, int along) {
        int span = path instanceof Spur ? 22 : path.width() == 5 ? 56 : 38;
        int route = path instanceof Road ? ((Road)path).index() : ((Spur)path).across();
        long salt = seed ^ (path.eastWest() ? 88279L : 99709L);
        int i = Math.floorDiv(along,span), f = Math.floorMod(along,span);
        int a = roll(salt,route,i,100), b = roll(salt,route,i+1,100);
        return (a*(span-f)+b*f)/span;
    }

    static Spur toRoad(long seed, Point door, boolean eastWest, Point village, boolean pixies) {
        int end = door.along(eastWest), across = door.across(eastWest);
        int turn = nearest(seed, !eastWest, door).center(across);
        int bend = (roll(seed, door.x, door.z, 21) - 10) * Math.min(40, Math.abs(end - turn)) / 40;
        return new Spur(eastWest, turn, end, across, bend, village, pixies);
    }

    static int roll(long seed, int x, int z, int bound) {
        long h = seed ^ (x * 0x9e3779b97f4a7c15L) ^ (z * 0xc2b2ae3d27d4eb4fL);
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        return (int)Long.remainderUnsigned(h ^ (h >>> 31), bound);
    }
}
