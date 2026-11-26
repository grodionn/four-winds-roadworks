package dev.fourwinds.roads;

import java.util.function.IntBinaryOperator;
import static dev.fourwinds.roads.RoadLayout.*;

/** A straight deck between two full-width seats in the terrain. Coordinates are along and across the road. */
record BridgeAlignment(int left, int right, Line line) {
    static final int BANK_SEARCH = 18;
    static final int MAX_SHIFT = 18;
    private static final int[] SKEWS = {0,-2,2,-4,4,-6,6,-8,8};

    record Line(int origin,int across,int skew) {
        int at(int t) { return across+Math.floorDiv((t-origin)*skew+4,8); }
        int edge(int t,int side) {
            return outside(side,at(t-1),at(t),at(t+1));
        }
    }
    int center(int t) { return line.at(t); }

    static BridgeAlignment fit(Path path, int firstBank, int lastBank, int deck, int junction,
                               int leftTie,int rightTie,IntBinaryOperator terrain) {
        int middle=Math.floorDiv(firstBank+lastBank,2);
        int bestCost=Integer.MAX_VALUE;
        BridgeAlignment best=null;
        for (int skew:SKEWS) {
            int middleAcross=path.center(middle);
            for (int across=middleAcross-MAX_SHIFT;across<=middleAcross+MAX_SHIFT;across++) {
                var direction=new Line(middle,across,skew);
                int left=seat(path,terrain,firstBank,-1,direction,deck);
                int right=seat(path,terrain,lastBank,1,direction,deck);
                if (left==Integer.MIN_VALUE || right==Integer.MIN_VALUE || left<leftTie || right>rightTie) continue;
                var candidate=new BridgeAlignment(left,right,direction);
                if (!candidate.reaches(path,junction) || !candidate.reaches(path,Math.max(leftTie,left-24))
                        || !candidate.reaches(path,Math.min(rightTie,right+24))) continue;
                int cost=candidate.cost(path,deck,terrain);
                if (cost<bestCost) { bestCost=cost;best=candidate; }
            }
            // Prefer square masonry on the block grid wherever both cliff seats fit.
            if (skew==0 && best!=null) break;
        }
        return best;
    }

    private int cost(Path path,int deck,IntBinaryOperator terrain) {
        int cost=right-left+3*Math.abs(line.skew);
        for (int side:new int[]{-1,1}) {
            int end=side<0?left:right,c=center(end);
            cost+=5*Math.abs(path.center(end)-c)+2*Math.abs(path.center(end+side*8)-c);
        }
        boolean wet=false;
        for (int t=left;t<=right;t++) {
            int c=center(t);
            if (Math.abs(path.center(t)-c)>MAX_SHIFT || path instanceof Road road && Math.abs(road.index()*SPACING-c)>64)
                return Integer.MAX_VALUE;
            int cut=Math.min(t-left,right-t)<=1?7:4;
            for (int offset=line.edge(t,-1);offset<=line.edge(t,1);offset++) {
                int ground=terrain.applyAsInt(t,c+offset);
                if (ground==Integer.MIN_VALUE || (ground>>1)>deck+cut) return Integer.MAX_VALUE;
                wet|=offset==0 && (ground&1)!=0;
                cost+=Math.max(0,(ground>>1)-deck);
            }
        }
        return wet?cost:Integer.MAX_VALUE;
    }

    private static int seat(Path path,IntBinaryOperator terrain,int bank,int side,Line line,int deck) {
        boolean previous=false;
        for (int d=0;d<=BANK_SEARCH;d++) {
            int t=bank+side*d;
            int c=line.at(t);
            if (!path.contains(t) || Math.abs(path.center(t)-c)>MAX_SHIFT) break;
            boolean solid=true;
            for (int offset=line.edge(t,-1);offset<=line.edge(t,1);offset++) {
                int ground=terrain.applyAsInt(t,c+offset);
                if (ground==Integer.MIN_VALUE || (ground&1)!=0 || (ground>>1)<deck-3) { solid=false;break; }
            }
            if (solid && previous) return t-side;
            previous=solid;
        }
        return Integer.MIN_VALUE;
    }

    int distance(int t) { return Math.max(0,Math.max(left-t,t-right)); }

    private boolean reaches(Path path,int t) {
        int distance=distance(t);
        return distance>24 || Math.abs(path.center(t)-center(Math.clamp(t,left,right)))<=Math.max(0,distance-2);
    }

    /** Leave a straight landing. The road can move at most one block sideways per row after it. */
    static int approach(int course,int axis,int distance) {
        int reach=Math.max(0,distance-2);
        return Math.clamp(course,axis-reach,axis+reach);
    }

    // Across a diagonal voxel row the cart also occupies the adjacent row. Put the rail outside that envelope.
    static int deckDepth(int offset,int previous,int center,int next) {
        return Math.min(offset-outside(-1,previous,center,next),outside(1,previous,center,next)-offset);
    }

    static int availableRise(int distance) { return Math.max(0,distance-1)/2; }
    private static int outside(int side,int previous,int center,int next) {
        int outer=side<0?Math.min(center,Math.min(previous,next)):Math.max(center,Math.max(previous,next));
        return 2*side+Integer.compare(outer,center);
    }
}
