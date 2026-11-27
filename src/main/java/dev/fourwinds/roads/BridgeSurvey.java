package dev.fourwinds.roads;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import static dev.fourwinds.roads.RoadLayout.*;

/** Reads the pre-decoration riverbed. Every tile of a crossing sees the same two banks. */
final class BridgeSurvey {
    private static final int DRY=Integer.MIN_VALUE, BANK_LIMIT=24, APPROACH=48, LANDING=10, MAX_RIVER_WIDTH=112;
    private static final int FIT_LIMIT=48, TURN=24;
    record Span(int y,int center,boolean timber,BridgeBay bay,RoadMaterials palette) {}
    private record Crossing(BridgeAlignment deck,int y,boolean timber,boolean singleArch,RoadMaterials palette) {
        Span row(int t) {
            var bay=singleArch?new BridgeBay(deck.left(),deck.right()):BridgeBay.at(t,deck.left(),deck.right(),true,timber,0);
            return new Span(y,deck.center(t),timber,bay,palette);
        }
    }
    private final RoadTerrain terrain;
    private final Map<Path,Channel> channels=new HashMap<>();

    BridgeSurvey(RoadTerrain terrain) { this.terrain=terrain; }
    Channel along(Path path) { return channels.computeIfAbsent(path,Channel::new); }

    final class Channel {
        private final Path path;
        private final Map<Integer,Integer> water=new HashMap<>(), shelves=new HashMap<>(), surfaces=new HashMap<>();
        private final Map<Integer,Span> spans=new HashMap<>();
        private final Map<Integer,Crossing> crossings=new HashMap<>();
        private final Map<Integer,BridgeBay> viaducts=new HashMap<>();
        private final Map<Integer,Boolean> gaps=new HashMap<>();
        private final Map<Point,Integer> soundings=new HashMap<>();
        private final Map<Integer,Integer> centers=new HashMap<>();
        private final Map<Integer,Boolean> landings=new HashMap<>();
        Channel(Path path) { this.path=path; }

        private int water(int t) {
            return water.computeIfAbsent(t,key->{
                if (!path.contains(key)) return DRY;
                Point p=path.point(key,0);
                int survey=ShoreTerrain.surface(terrain.level,p);
                if (survey==DRY || (survey&1)==0) return DRY;
                int y=survey>>1;
                return terrain.ocean(new BlockPos(p.x(),y,p.z()))?DRY:y;
            });
        }

        private int shelf(int t) {
            return shelves.computeIfAbsent(t,key->{
                int y=water(key);
                if (y!=DRY) return y;
                if (!path.contains(key)) return DRY;
                // Carry the same span across low gravel bars between arms of a braided stream.
                int l=key-1,r=key+1;
                while (key-l<=3 && water(l)==DRY) l--;
                while (r-key<=3 && water(r)==DRY) r++;
                if (r-l>4 || water(l)==DRY || water(l)!=water(r)) return DRY;
                int level=water(l);
                for (int i=l+1;i<r;i++) {
                    int ground=ShoreTerrain.surface(terrain.level,path.point(i,0));
                    if (ground==DRY || (ground>>1)>level+2) return DRY;
                }
                return level;
            });
        }

        private boolean channel(int t) {
            return shelf(t)!=DRY && (shelf(t-1)!=DRY && shelf(t+1)!=DRY || shelf(t-1)!=DRY && shelf(t-2)!=DRY
                    || shelf(t+1)!=DRY && shelf(t+2)!=DRY);
        }

        Span span(int t) {
            if (spans.containsKey(t)) return spans.get(t);
            Span result=null;
            if (channel(t)) {
                var fit=crossing(t);
                result=fit!=null?fit.row(t):measure(t);
            } else if (path.contains(t)) {
                // Follow the same drawing past the waterline, up to the actual rock seat.
                for (int side:new int[]{-1,1}) {
                    for (int d=1;d<=BridgeAlignment.BANK_SEARCH+1;d++) {
                        int wet=t+side*d;
                        if (!channel(wet)) continue;
                        var fit=crossing(wet);
                        if (fit!=null && fit.deck.distance(t)==0) result=fit.row(t);
                        break;
                    }
                    if (result!=null) break;
                }
            }
            spans.put(t,result);
            return result;
        }

        private int original(int t,int across) {
            Point p=path.eastWest()?new Point(t,across):new Point(across,t);
            return soundings.computeIfAbsent(p,key->ShoreTerrain.surface(terrain.level,key));
        }

        private Crossing crossing(int wet) {
            if (crossings.containsKey(wet)) return crossings.get(wet);
            int left=wet-1,right=wet+1;
            while (wet-left<=FIT_LIMIT && shelf(left)!=DRY) left--;
            while (right-wet<=FIT_LIMIT && shelf(right)!=DRY) right++;
            Crossing result=null;
            if (right-left<=FIT_LIMIT && bank(wet,-1)!=DRY && bank(wet,1)!=DRY) result=fit(left,right,shelf(wet));
            for (int t=left+1;t<right;t++) crossings.put(t,result);
            return result;
        }

        private Crossing fit(int left,int right,int waterY) {
            int junction;
            if (path instanceof Road road) {
                int number=Math.floorDiv((left+right)/2+SPACING/2,SPACING);
                junction=terrain.grade.junction(road,number).point().along(road.eastWest());
            } else junction=((Spur)path).turn();
            int bankGrade=(terrain.grade.along(path,left)+terrain.grade.along(path,right)+1)/2;
            boolean timber=path.width()<5 && right-left<=16 && bankGrade<=waterY+5
                    && (junction<left-LANDING || junction>right+LANDING);
            int y=Math.max(bankGrade,waterY+(timber?2:4));
            int leftTie=dryTie(left,-1),rightTie=dryTie(right,1);
            var deck=BridgeAlignment.fit(path,left,right,y,junction,leftTie,rightTie,this::original);
            if (deck==null) return null;
            if (timber && deck.right()-deck.left()>20) {
                timber=false;
                y=Math.max(bankGrade,waterY+4);
                deck=BridgeAlignment.fit(path,left,right,y,junction,leftTie,rightTie,this::original);
                if (deck==null) return null;
            }
            for (int t=deck.left();t<=deck.right();t++) {
                int c=deck.center(t),surface=original(t,c);
                if ((surface&1)!=0 && terrain.ocean(new BlockPos(path.eastWest()?t:c,surface>>1,path.eastWest()?c:t))) return null;
            }
            int end=terrain.grade.along(path,left)>=terrain.grade.along(path,right)?deck.left():deck.right();
            Point seat=path.point(end,deck.center(end)-path.center(end));
            String stone=terrain.materials.usesLanterns()?"andesite":ShoreTerrain.quarry(terrain.level,seat);
            boolean singleArch=!timber && y-waterY>=8 && deck.right()-deck.left()<=30;
            return new Crossing(deck,y,timber,singleArch,terrain.materials.construction(stone));
        }

        // Two nearby rivers share a dry point on the original road so their approaches always meet.
        private int dryTie(int bank,int side) {
            for (int d=1;d<=2*TURN;d++) if (channel(bank+side*d)) return bank+side*(d/2);
            return bank+side*(TURN+BridgeAlignment.BANK_SEARCH);
        }

        Span junctionSpan(int t) {
            for (int d=0;d<=3;d++) {
                Span s=span(t-d);
                if (s==null && d!=0) s=span(t+d);
                if (s!=null) return s;
            }
            return null;
        }

        Span built(int t) {
            Span s=span(t);
            if (s!=null) return s;
            Span bank=channel(t-1) && crossing(t-1)==null?span(t-1):null;
            if (bank==null && channel(t+1) && crossing(t+1)==null) bank=span(t+1);
            return bank==null?null:new Span(bank.y,center(t),bank.timber,new BridgeBay(t,t+1),bank.palette);
        }

        boolean reachesLand(int t) {
            if (!channel(t) && span(t)!=null) return true;
            return landings.computeIfAbsent(t,k->{
                int left=bank(k,-1);
                if (left==DRY) return false;
                int right=bank(k,1);
                return right!=DRY && right-left<=MAX_RIVER_WIDTH;
            });
        }

        private int bank(int t,int side) {
            // A river biome can run well out into a bay. Its boundary in the water is no abutment.
            int dry=0;
            for (int d=0;d<=MAX_RIVER_WIDTH+2;d++) {
                int i=t+side*d;
                if (!path.contains(i)) return DRY;
                Point p=path.point(i,0);
                int original=ShoreTerrain.surface(terrain.level,p);
                if (original==DRY || (original&1)!=0 && water(i)==DRY) return DRY;
                dry=d>0 && (original&1)==0 && shelf(i)==DRY?dry+1:0;
                if (dry==2) return i-side;
            }
            return DRY;
        }

        boolean surveyedWet(int t) {
            int surface=ShoreTerrain.surface(terrain.level,path.point(t,0));
            return surface!=DRY && (surface&1)!=0;
        }

        private Span measure(int t) {
            int l=t-1,r=t+1;
            while (t-l<=BANK_LIMIT && shelf(l)!=DRY) l--;
            while (r-t<=BANK_LIMIT && shelf(r)!=DRY) r++;
            boolean whole=r-l<=BANK_LIMIT+1;
            boolean junction=!whole || path instanceof Spur spur && spur.turn()>=l-LANDING && spur.turn()<=r+LANDING;
            if (whole && path instanceof Road road) {
                int number=Math.floorDiv(t+SPACING/2,SPACING);
                if (Math.abs(t-number*SPACING)<=RoadSection.JUNCTION_SHIFT+BANK_LIMIT+LANDING) {
                    int cross=terrain.grade.junction(road,number).point().along(road.eastWest());
                    junction|=cross>=l-LANDING && cross<=r+LANDING;
                }
            }
            int waterY=shelf(t),y=waterY+5,center=path.center(t);
            int left=whole?terrain.grade.along(path,l):waterY+5;
            int right=whole?terrain.grade.along(path,r):waterY+5;
            int design=(left+right+1)/2;
            // A highway above a gorge crosses at the bank grade. Only low, short crossings use timber piles.
            boolean timber=whole && !junction && path.width()<5 && r-l<=16 && design<=waterY+5;
            if (whole) y=Math.max(waterY+(timber?2:4),design);
            String stone="andesite";
            if (whole && !junction) {
                int lc=path.center(l),rc=path.center(r);
                center=RoadSection.interpolate(lc,rc,t-l,r-l);
                int lowBank=Math.min(ShoreTerrain.surface(terrain.level,path.point(l,0))>>1,
                        ShoreTerrain.surface(terrain.level,path.point(r,0))>>1);
                if (!timber && y-lowBank<5 && !terrain.materials.usesLanterns())
                    stone=ShoreTerrain.quarry(terrain.level,path.point(left>=right?l:r,0));
            }
            int route=path instanceof Road road?road.index():((Spur)path).across();
            int phase=roll(terrain.level.getSeed(),route,path.eastWest()?19:23,11);
            return new Span(y,center,timber,BridgeBay.at(t,l,r,whole,timber,phase),terrain.materials.construction(stone));
        }

        int grade(int t) { return surfaces.computeIfAbsent(t,this::approach); }

        int center(int t) { return centers.computeIfAbsent(t,this::alignment); }

        int deckDepth(int t,int offset) {
            return BridgeAlignment.deckDepth(offset,center(t-1),center(t),center(t+1));
        }

        boolean landingTrack(int t,int offset) {
            if (Math.abs(offset)!=2 || deckDepth(t,offset)<1) return false;
            for (int d=1;d<=2;d++) for (int side:new int[]{-1,1}) {
                int row=t+side*d;
                if (built(row)!=null || viaduct(row)!=null) return true;
            }
            return false;
        }

        int constrainLanding(int t,int wanted) {
            int low=Integer.MIN_VALUE,high=Integer.MAX_VALUE,left=0,leftDistance=0;
            for (int side:new int[]{-1,1}) for (int d=1;d<=24;d++) {
                var deck=span(t+side*d);
                if (deck==null) continue;
                int rise=BridgeAlignment.availableRise(d);
                low=Math.max(low,deck.y-rise);
                high=Math.min(high,deck.y+rise);
                if (side<0) { left=deck.y;leftDistance=d; }
                else if (low>high) return RoadSection.interpolate(left,deck.y,leftDistance,leftDistance+d);
                break;
            }
            return RoadLayout.clamp(wanted,low,high);
        }

        Point point(int t,int offset) {
            int c=center(t)+offset;
            return path.eastWest()?new Point(t,c):new Point(c,t);
        }

        private int alignment(int t) {
            Span here=span(t);
            if (here!=null) return here.center;
            int wanted=path.center(t),lo=Integer.MIN_VALUE,hi=Integer.MAX_VALUE;
            int left=0,right=0,leftDistance=0,rightDistance=0;
            for (int side:new int[]{-1,1}) {
                for (int d=1;d<TURN;d++) {
                    Span s=span(t+side*d);
                    if (s==null) continue;
                    if (side<0) {left=s.center;leftDistance=d;}
                    else {right=s.center;rightDistance=d;}
                    int projected=BridgeAlignment.approach(wanted,s.center,d);
                    if (projected>wanted) lo=Math.max(lo,projected);
                    if (projected<wanted) hi=Math.min(hi,projected);
                    break;
                }
            }
            return lo<=hi?RoadLayout.clamp(wanted,lo,hi)
                    :RoadSection.interpolate(left,right,leftDistance,leftDistance+rightDistance);
        }

        private int approach(int t) {
            Span here=span(t);
            if (here!=null) return here.y;
            int left=1,right=1;
            while (left<APPROACH && span(t-left)==null) left++;
            while (right<APPROACH && span(t+right)==null) right++;
            if (left==APPROACH && right==APPROACH) return terrain.grade.along(path,t);
            if (right==APPROACH) return ramp(t,-left);
            if (left==APPROACH) return ramp(t,right);
            int a=(APPROACH-left)*right, b=(APPROACH-right)*left;
            return RoadSection.interpolate(ramp(t,-left),ramp(t,right),b,a+b);
        }

        private int ramp(int t,int delta) {
            int bank=t+delta;
            int side=-Integer.signum(delta),d=Math.abs(delta);
            int far=terrain.grade.along(path,bank+side*APPROACH);
            return RoadSection.interpolate(span(bank).y,far,d-1,APPROACH-1);
        }

        private boolean gap(int t) {
            return gaps.computeIfAbsent(t,k->{
                if (!path.contains(k) || built(k)!=null) return false;
                int c=center(k),g=original(k,c);
                if (g==DRY || (g&1)!=0) return false;
                int y=RoadFeature.surface(terrain,path,point(k,0));
                return y-(g>>1)>=5;
            });
        }

        BridgeBay viaduct(int t) {
            if (viaducts.containsKey(t)) return viaducts.get(t);
            BridgeBay bay=null;
            if (gap(t) && RoadLayout.sustainedCover(t,this::gap)) {
                int l=t,r=t;
                while (t-l<=BANK_LIMIT && gap(l-1)) l--;
                while (r-t<=BANK_LIMIT && gap(r+1)) r++;
                bay=BridgeBay.at(t,l,r,r-l<=BANK_LIMIT,false,0);
            }
            viaducts.put(t,bay);
            return bay;
        }
    }
}
