package dev.fourwinds.roads;

import java.util.function.IntPredicate;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import static dev.fourwinds.roads.RoadLayout.*;

/** Coordinates in the carpenter's plan. Positive steps run to sea, positive side faces the tower. */
final class PierLayout {
    private final Point shore;
    private final boolean eastWest;
    private final int outward, side, water, entrance;

    PierLayout(Point shore, boolean eastWest, int outward, int side, int water, int entrance) {
        this.shore = shore;
        this.eastWest = eastWest;
        this.outward = outward;
        this.side = side;
        this.water = water;
        this.entrance = entrance;
    }
    Point shore() { return shore; }
    boolean eastWest() { return eastWest; }
    int outward() { return outward; }
    int side() { return side; }
    int water() { return water; }
    int entrance() { return entrance; }

    static final int LENGTH=16, MIN_SIDE=-12, MAX_SIDE=12, TOWER=10, TOWER_SIDE=3;
    static final int HEIGHT=34, FIRE_HEIGHT=24;
    enum Outlet { COAST, OPEN_SEA, PACK_ICE }

    static boolean hasHarbourPriority(Road road,int t,int outward) {
        Point middle=road.point(t+outward*LENGTH/2,0);
        Road other=nearest(road.seed(),!road.eastWest(),middle);
        int separation=Math.abs(other.center(middle.along(other.eastWest()))-middle.across(other.eastWest()));
        // Leave room for both landing lengths and the tower apron where two roads meet the same cove.
        return separation>2*LENGTH+5 || road.width()>other.width()
                || road.width()==other.width() && road.eastWest();
    }

    // Distances are signed along the road. Negative samples are on the landward side.
    static boolean suitableShore(int water,int entrance,IntPredicate marine,IntPredicate dry,
                                 IntUnaryOperator seabed,IntFunction<Outlet> outlet) {
        if (Math.abs(entrance-(water+2))>4) return false;
        // A shelf of submerged rock behind the landing can be decorated into either a pool or an isolated boulder.
        if (seabed.applyAsInt(-2)<water-1 && seabed.applyAsInt(-8)<water && seabed.applyAsInt(-16)<=water) return false;
        boolean land=false;
        for (int d=2;d<=16;d+=2) {
            if (marine.test(-d)) return false;
            land |= dry.test(-d);
        }
        if (!land && seabed.applyAsInt(-24)<water) return false;
        for (int d=1;d<=LENGTH;d++) if (!marine.test(d)) return false;
        boolean openSea=false;
        for (int d=16;d<=36;d+=4) {
            if (seabed.applyAsInt(d)>=water) return false;
            Outlet stretch=outlet.apply(d);
            if (stretch==Outlet.PACK_ICE) return false;
            openSea |= stretch==Outlet.OPEN_SEA;
        }
        return openSea;
    }

    int deck() { return water+2; }
    int floor(int step) {
        int difference = entrance-deck();
        return deck()+Integer.signum(difference)*Math.max(0,Math.abs(difference)-step);
    }
    Point point(int step,int offset) {
        return new Point(shore.x()+(eastWest ? step*outward : offset*side),
                         shore.z()+(eastWest ? offset*side : step*outward));
    }
    boolean stoneFoot(int s,int u) {
        int x=Math.abs(s-TOWER), z=Math.abs(u-TOWER_SIDE);
        return Math.max(x,z)<=5 && x+z<=8;
    }
    boolean boarding(int s,int u) { return s>=10 && s<=11 && u>=-10 && u<=-7; }
    boolean boatStep(int s,int u) { return s==12 && u>=-10 && u<=-7; }
    boolean platform(int s,int u) {
        if (stoneFoot(s,u)) return true;
        if (s<3) return Math.abs(u)<=(s==0?2:1);
        if (s==3) return u>=-2 && u<=8;
        if (s==4) return u>=-2 && u<=10;
        boolean quay=u>=-4 && u<=1 || s>=12 && s<=15 && u>=-10 && u<-4;
        boolean store=s>=5 && s<=10 && u>=2 && u<=11;
        return quay || store;
    }
    // The jib and both roofs can overhang the water beyond the edge of the planking.
    int clearance(int s,int u) {
        if (stoneFoot(s,u)) return HEIGHT;
        if (s>=4 && s<=11 && u>=5 && u<=11 || s>=12 && s<=16 && u>=-12 && u<=-5) return 8;
        return platform(s,u) || boarding(s,u) ? 3 : 0;
    }
    boolean pile(int s,int u) {
        if (stoneFoot(s,u)) return false;
        switch(s) {
            case 0: return Math.abs(u)==2;
            case 3: return u==-2 || u==1 || u==8;
            case 5: return u==-4 || u==10;
            case 8: return u==-4 || u==11;
            case 10: return u==10 || u==-10;
            case 12: case 15: return u==-10 || u==-6 || u==-4;
            case 16: return u==-3 || u==1;
            default: return false;
        }
    }
}
