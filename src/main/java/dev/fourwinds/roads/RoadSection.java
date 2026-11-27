package dev.fourwinds.roads;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntBinaryOperator;
import static dev.fourwinds.roads.RoadLayout.*;

/** One surveyed route between two shared, level junctions. */
final class RoadSection {
    static final int STEP=8, SHIFT=40, JUNCTION_SHIFT=24;
    static final int VILLAGE_SHIFT=64;
    private static final int[] OFFSETS={0,-4,4,-8,8,-12,12,-16,16,-20,20,-24,24,-28,28,-32,32,-36,36,-40,40};
    private static final int[] BYPASS_OFFSETS=java.util.stream.IntStream.rangeClosed(0,48)
            .map(i->(i+1)/2*(i%2==0?4:-4)).toArray();
    static final class Junction {
        private final Point point;
        private final int y;

        Junction(Point point, int y) {
            this.point = point;
            this.y = y;
        }
        public Point point() { return point; }
        public int y() { return y; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Junction)) return false;
            Junction rhs = (Junction)other;
            return java.util.Objects.equals(point, rhs.point)
                && y == rhs.y;
        }

        @Override public int hashCode() {
            int hash = java.util.Objects.hashCode(point);
            hash = 31 * hash + y;
            return hash;
        }
    }
    static final class Parcel {
        private final int x1;
        private final int z1;
        private final int x2;
        private final int z2;
        private final int bottom;
        private final int top;
        private final boolean street;

        Parcel(int x1, int z1, int x2, int z2, int bottom, int top, boolean street) {
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
            this.bottom = bottom;
            this.top = top;
            this.street = street;
        }
        public int x1() { return x1; }
        public int z1() { return z1; }
        public int x2() { return x2; }
        public int z2() { return z2; }
        public int bottom() { return bottom; }
        public int top() { return top; }
        public boolean street() { return street; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Parcel)) return false;
            Parcel rhs = (Parcel)other;
            return x1 == rhs.x1
                && z1 == rhs.z1
                && x2 == rhs.x2
                && z2 == rhs.z2
                && bottom == rhs.bottom
                && top == rhs.top
                && street == rhs.street;
        }

        @Override public int hashCode() {
            int hash = x1;
            hash = 31 * hash + z1;
            hash = 31 * hash + x2;
            hash = 31 * hash + z2;
            hash = 31 * hash + bottom;
            hash = 31 * hash + top;
            hash = 31 * hash + Boolean.hashCode(street);
            return hash;
        }

        boolean intersects(int minX,int minZ,int maxX,int maxZ) {
            return x2>=minX && x1<=maxX && z2>=minZ && z1<=maxZ;
        }
    }
    private static int occupation(List<Parcel> plots,int x,int z,int y,int radius) {
        int cost=0;
        for (Parcel p:plots) if (y+8>=p.bottom && y-3<=p.top && p.intersects(x-radius,z-radius,x+radius,z+radius)) {
            int depth=Math.min(Math.min(x-p.x1,p.x2-x),Math.min(z-p.z1,p.z2-z))+radius;
            cost=Math.max(cost,(p.street?1800:12000)+Math.max(0,depth)*100);
        }
        return cost;
    }
    private final int[] stations,centers,heights;

    static Junction survey(int x,int z,int sea,IntBinaryOperator sample) {
        return survey(x,z,sea,sample,java.util.Collections.emptyList());
    }
    static Junction survey(int x,int z,int sea,IntBinaryOperator sample,List<Parcel> plots) {
        int origin=sample.applyAsInt(x,z);
        Junction best=new Junction(new Point(x,z),origin);
        if (origin<=sea+5 && occupation(plots,x,z,origin,8)==0) return best;
        int bestCost=Integer.MAX_VALUE;
        int limit=occupation(plots,x,z,origin,24)>0?VILLAGE_SHIFT:JUNCTION_SHIFT;
        for (int ring=0;ring<=limit && ring<bestCost;ring+=4)
            for (int dx=-ring;dx<=ring;dx+=4) for (int dz=-ring;dz<=ring;dz+=4) {
            if (Math.max(Math.abs(dx),Math.abs(dz))!=ring || Math.abs(dx)+Math.abs(dz)>=bestCost) continue;
            int px=x+dx,pz=z+dz,y=sample.applyAsInt(px,pz);
            if (y<=sea+4 && plots.isEmpty()) continue;
            int low=y,high=y, approaches=0;
            for (int i=-1;i<=1;i++) for (int j=-1;j<=1;j++) {
                if (i==0 && j==0) continue;
                int apron=sample.applyAsInt(px+4*i,pz+4*j);
                low=Math.min(low,apron);high=Math.max(high,apron);
                if (i*j==0) approaches+=Math.max(0,Math.abs(sample.applyAsInt(px+12*i,pz+12*j)-y)-3);
            }
            int cost=12*(high-low)+4*approaches+Math.abs(dx)+Math.abs(dz)+2*Math.abs(y-origin)+occupation(plots,px,pz,y,8);
            if (limit==VILLAGE_SHIFT) for (int offset:new int[]{-16,16})
                cost+=occupation(plots,px+offset,pz,y,4)+occupation(plots,px,pz+offset,y,4);
            // A level patch on top of a spur is a poor crossroads if all four exits start with a precipice.
            for (int reach:new int[]{24,40}) for (int dir=0;dir<4 && cost<bestCost;dir++) {
                int sx=dir==0?1:dir==1?-1:0,sz=dir==2?1:dir==3?-1:0;
                int bestExit=Integer.MAX_VALUE;
                for (int side=-reach/2;side<=reach/2;side+=4) {
                    int exit=sample.applyAsInt(px+sx*reach+sz*side,pz+sz*reach+sx*side);
                    bestExit=Math.min(bestExit,Math.abs(exit-y));
                    if (bestExit<=reach/4) break;
                }
                cost+=10*Math.max(0,bestExit-reach/4);
            }
            if (cost<bestCost) { bestCost=cost;best=new Junction(new Point(px,pz),y); }
        }
        return best;
    }

    RoadSection(Road road,int start,int sea,int firstY,int lastY,IntBinaryOperator sample) {
        this(road,new Junction(road.point(start,0),firstY),new Junction(road.point(start+SPACING,0),lastY),sea,sample);
    }

    RoadSection(Road road,Junction first,Junction last,int sea,IntBinaryOperator sample) {
        this(road,first,last,sea,sample,java.util.Collections.emptyList());
    }
    RoadSection(Road road,Junction first,Junction last,int sea,IntBinaryOperator sample,List<Parcel> plots) {
        boolean ew=road.eastWest();
        int start=first.point.along(ew),end=last.point.along(ew),length=end-start;
        int count=(length+STEP/2)/STEP+1;
        stations=new int[count];centers=new int[count];heights=new int[count];
        int[] base=new int[count],spine=new int[count];
        int enter=first.point.across(ew)-road.seedCenter(start),leave=last.point.across(ew)-road.seedCenter(end);
        for (int i=0;i<count;i++) {
            int t=stations[i]=start+(length*i+(count-1)/2)/(count-1);
            base[i]=road.seedCenter(t)+interpolate(0,enter,Math.max(0,56-(t-start)),56)
                    +interpolate(0,leave,Math.max(0,56-(end-t)),56);
            if (i==1 && enter!=0 || i==count-2 && leave!=0) {
                int root=i==1?first.point.across(ew):last.point.across(ew);
                base[i]=RoadLayout.clamp(base[i],root-3,root+3);
            }
            spine[i]=height(road,t,base[i],sample);
        }
        // Round along the same piecewise-linear reference used by the solver so a bend stays connected.
        for (int i=1;i<count;i++) base[i]=RoadLayout.clamp(base[i],base[i-1]-(stations[i]-stations[i-1]),base[i-1]+stations[i]-stations[i-1]);
        base[count-1]=last.point.across(ew);
        for (int i=count-2;i>=0;i--) base[i]=RoadLayout.clamp(base[i],base[i+1]-(stations[i+1]-stations[i]),base[i+1]+stations[i+1]-stations[i]);
        route(road,first.y,sea,sample,base,spine,null,java.util.Collections.emptyList());
        int[] ground=heights.clone();
        profile(first.y,last.y);
        boolean earthworks=false;
        for (int i=1;i<count-1;i++) if (Math.abs(heights[i]-ground[i])>4) { earthworks=true;break; }
        if (earthworks) {
            route(road,first.y,sea,sample,base,spine,heights.clone(),java.util.Collections.emptyList());
            profile(first.y,last.y);
        }
        if (!plots.isEmpty()) for (int i=0;i<count;i++) {
            int x=ew?stations[i]:centers[i],z=ew?centers[i]:stations[i];
            if (occupation(plots,x,z,heights[i],6)==0) continue;
            route(road,first.y,sea,sample,base,spine,heights.clone(),plots);
            profile(first.y,last.y);
            break;
        }
    }

    private void route(Road road,int firstY,int sea,IntBinaryOperator sample,int[] base,int[] spine,int[] profile,List<Parcel> plots) {
        int count=stations.length;
        int[] offsets=plots.isEmpty()?OFFSETS:BYPASS_OFFSETS;
        int[][] ground=new int[count][offsets.length],parent=new int[count][offsets.length];
        int[] costs=new int[offsets.length];
        Arrays.fill(costs,Integer.MAX_VALUE);costs[0]=0;ground[0][0]=firstY;
        for (int i=1;i<count;i++) {
            int t=stations[i],advance=t-stations[i-1];
            boolean fixed=i==count-1 || (i==1 || i==count-2 || spine[i]<=sea+4) && plots.isEmpty();
            int[] next=new int[offsets.length];Arrays.fill(next,Integer.MAX_VALUE);
            for (int j=0;j<(fixed?1:offsets.length);j++) {
                int across=base[i]+offsets[j];
                if (Math.abs(across-road.index()*SPACING)>64) continue;
                int y=ground[i][j]=height(road,t,across,sample);
                if (j!=0 && y<=sea+3 && plots.isEmpty()) continue;
                int left=height(road,t,across-4,sample),right=height(road,t,across+4,sample);
                int bank=Math.max(y,Math.max(left,right))-Math.min(y,Math.min(left,right));
                int occupied=occupation(plots,road.eastWest()?t:across,road.eastWest()?across:t,profile==null?y:profile[i],6);
                int earth=7*Math.max(0,bank-2)+Math.abs(offsets[j])/5+occupied;
                for (int k=0;k<offsets.length;k++) {
                    if (costs[k]==Integer.MAX_VALUE) continue;
                    int prev=base[i-1]+offsets[k],turn=across-prev;
                    if (Math.abs(turn)>advance) continue;
                    int half=height(road,t-advance/2,(prev+across)>>1,sample);
                    int climb=Math.abs(y-ground[i-1][k]);
                    int crest=Math.abs(2*half-y-ground[i-1][k]);
                    int prior=i>1?prev-base[i-2]-offsets[parent[i-1][k]]:0;
                    int cost=costs[k]+earth+4*climb+19*Math.max(0,climb-2)+5*crest
                            +2*Math.abs(turn-prior)+Math.abs(offsets[j]-offsets[k]);
                    if (profile!=null) {
                        int fill=profile[i]-y,cut=y-profile[i];
                        cost+=20*Math.max(0,fill-2)+12*Math.max(0,cut-3);
                    }
                    if (cost<next[j]) { next[j]=cost;parent[i][j]=k; }
                }
            }
            costs=next;
        }
        for (int i=count-1,j=0;i>=0;j=parent[i][j],i--) { centers[i]=base[i]+offsets[j];heights[i]=ground[i][j]; }
    }

    private void profile(int firstY,int lastY) {
        int count=stations.length,start=stations[0],length=stations[count-1]-start;
        heights[0]=firstY;heights[count-1]=lastY;
        int[] low=heights.clone(),high=heights.clone(),budget=new int[count];
        int distance=length-6,change=Math.max((distance+3)/4,Math.abs(firstY-lastY));
        for(int i=0;i<count;i++) budget[i]=(RoadLayout.clamp(stations[i]-start-3,0,distance)*change+distance/2)/distance;
        for(int i=1;i<count;i++) bound(low,high,i,i-1,budget);
        for(int i=count-2;i>=0;i--) bound(low,high,i,i+1,budget);
        for(int i=1;i<count-1;i++) {
            int a=budget[i],b=change-a,wanted=low[i]+Math.min(3,(high[i]-low[i]+1)/2);
            heights[i]=RoadLayout.clamp(wanted,Math.max(firstY-a,lastY-b),Math.min(firstY+a,lastY+b));
        }
    }

    private static int height(Road r,int t,int across,IntBinaryOperator sample) {
        return r.eastWest()?sample.applyAsInt(t,across):sample.applyAsInt(across,t);
    }
    private static void bound(int[] low,int[] high,int i,int other,int[] budget) {
        int rise=Math.abs(budget[i]-budget[other]);
        low[i]=Math.min(low[i],low[other]+rise);high[i]=Math.max(high[i],high[other]-rise);
    }
    private int interval(int t) {
        int i=Arrays.binarySearch(stations,t);
        return RoadLayout.clamp(i>=0?i:-i-2,0,stations.length-2);
    }
    int center(Road road,int t) {
        int i=interval(t),a=stations[i],b=stations[i+1];
        // Preserve seed-drawn curves on flat ground away from the moving junction aprons.
        if (centers[i]==road.seedCenter(a) && centers[i+1]==road.seedCenter(b)) return road.seedCenter(t);
        return interpolate(centers[i],centers[i+1],t-a,b-a);
    }
    int elevation(int t) {
        int i=interval(t),a=stations[i],b=stations[i+1];
        if (i==0) a+=3;
        if (i==stations.length-2) b-=3;
        return interpolate(heights[i],heights[i+1],RoadLayout.clamp(t-a,0,b-a),b-a);
    }
    static int interpolate(int a,int b,int offset,int span) {
        return a+Math.floorDiv((b-a)*offset+span/2,span);
    }
}
