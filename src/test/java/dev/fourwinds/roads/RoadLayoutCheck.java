package dev.fourwinds.roads;

import static dev.fourwinds.roads.RoadLayout.*;

public class RoadLayoutCheck {
    public static void main(String[] args) {
        checkTerrainRoutes();
        checkVillageBypass();
        checkHillsideJunction();
        int lastBank=70;
        for (int d=1;d<=6;d++) {
            int bank=bankHeight(d,75,70,true);
            assert bank>=lastBank && bank<=lastBank+1 && bank<=75;
            assert bankHeight(d,75,70,false)==75;
            assert bankHeight(d,70,70,true)==70;
            lastBank=bank;
        }
        checkPiers();
        checkBridgeBays();
        checkBridgeSeats();
        for (int width:new int[]{3,4,5}) {
            for (boolean between:new boolean[]{false,true}) {
                int left=edge(width,-1),right=edge(width,1);
                assert right+2-(left-2)+1==width+4;
                assert vaultClearance(0,between)>=7 && vaultClearance(1,between)>=8;
                for (int off=left-2;off<=right+2;off++) {
                    int h=vaultClearance(depth(width,off),between);
                    assert h==vaultClearance(depth(width,left+right-off),between) && h>=5;
                    assert Math.abs(h-vaultClearance(depth(width,off+1),between))<=1;
                }
            }
        }
        for (int start=-20;start<21;start++) for (int length=0;length<12;length++) {
            int from=start,until=start+length;
            for (int i=from-1;i<=until;i++)
                assert sustainedCover(i,t->t>=from && t<until)==(i>=from && i<until && length>=4);
        }
        for (long seed : new long[]{-91, 0, 97, Long.MAX_VALUE}) {
            for (int i = -5; i <= 5; i++) {
                for (boolean eastWest : new boolean[]{true, false}) {
                    Road road = new Road(seed, eastWest, i);
                    for (int group=-64;group<=64;group++) {
                        int t=stopStation(road,group),phase=Math.floorMod(t,SPACING);
                        assert Math.floorDiv(t,2*SPACING)==group && Math.min(phase,SPACING-phase)>=55;
                        assert Math.floorMod(t,16)==7 && t-stopStation(road,group-1)>=112;
                    }
                    assert road.width() == (Math.floorMod(i,3) == 0 ? 5 : 4);
                    assert edge(road.width(),1) - edge(road.width(),-1) + 1 == road.width();
                    for (int start=-SPACING*2; start<=SPACING; start+=SPACING) {
                        int bend=0;
                        for (int t=start; t<start+SPACING; t++) bend=Math.max(bend,Math.abs(road.center(t)-i*SPACING));
                        assert bend >= 16 && bend <= MAX_BEND;
                    }
                    for (int t = -600; t <= 600; t++) {
                        assert Math.abs(road.center(t) - road.center(t - 1)) <= 1;
                        if (t % SPACING == 0) assert road.center(t) == i * SPACING;
                        Point door = road.point(t, Math.floorMod(t, 161) - 80);
                        Spur spur = toRoad(seed, door, !eastWest, door, false);
                        assert spur.width() == 3;
                        assert Math.abs(wear(seed,road,t)-wear(seed,road,t-1))<=3;
                        assert Math.abs(wear(seed,spur,t)-wear(seed,spur,t-1))<=5;
                        assert spur.point(spur.end(), 0).equals(door);
                        assert spur.sign().distanceTo(spur.point(spur.signTurn(),2))==1;
                        assert spur.point(spur.signTurn(),2).distanceTo(spur.point(spur.signTurn(),edge(spur.width(),1)))==1;
                        Point turn = spur.point(spur.turn(), 0);
                        assert nearest(seed, eastWest, turn).center(turn.along(eastWest)) == turn.across(eastWest);
                        assert turn.distanceTo(door) <= 101;
                        assert spur.contains(spur.turn()) && spur.contains(spur.end());
                        for (int s = Math.min(spur.turn(), spur.end()); s < Math.max(spur.turn(), spur.end()); s++) {
                            assert Math.abs(spur.center(s) - spur.center(s + 1)) <= 1;
                        }
                    }
                }
            }
        }
    }
    private static void checkBridgeSeats() {
        Road diagonal=new Road(42,true,0,t->Math.floorDiv(t+1,2));
        var skewed=BridgeAlignment.fit(diagonal,-5,5,83,36,-48,48,(t,c)->{
            if (Math.abs(c-diagonal.center(t))>3) return 108*2;
            return Math.abs(t)<=4?62*2+1:Math.min(84,62+2*(Math.abs(t)-4))*2;
        });
        assert skewed!=null && skewed.center(skewed.left())<skewed.center(skewed.right());
        for (int t=0;t<=20;t++) {
            int rise=BridgeAlignment.availableRise(t);
            assert t>2 || rise==0;
            assert BridgeAlignment.availableRise(t+1)-rise<=1;
            assert 2*rise<=t;
        }
        for (boolean ew:new boolean[]{false,true}) for (int sign:new int[]{-1,1}) {
            for (int c:new int[]{-894,0,15}) {
                assert BridgeAlignment.deckDepth(sign*2,c,c,c)==0;
                assert BridgeAlignment.deckDepth(3*sign,c-sign,c,c+sign)==0;
                for (int o=-2;o<=2;o++) assert BridgeAlignment.deckDepth(o,c-sign,c,c+sign)>=1;
            }
            Road path=new Road(17,ew,0,t->sign*Math.clamp(t/3,-8,8));
            // Five dry rows on either bank are still far below the road. The outer corner seats last.
            var terrain=(java.util.function.IntBinaryOperator)(t,c)->{
                if (Math.abs(t)<=4) return 62*2+1;
                return Math.min(84,63+2*(Math.abs(t)-4)-Math.floorMod(c,3))*2;
            };
            var deck=BridgeAlignment.fit(path,-5,5,83,36,-48,48,terrain);
            assert deck!=null && deck.left()<-10 && deck.right()>10;
            for (int foot:new int[]{deck.left(),deck.right()}) for (int o=-2;o<=2;o++)
                assert (terrain.applyAsInt(foot,deck.center(foot)+o)>>1)>=80;
            for (int side:new int[]{-1,1}) {
                int end=side<0?deck.left():deck.right(),bearing=deck.center(end),last=bearing;
                for (int d=1;d<=24;d++) {
                    int c=BridgeAlignment.approach(path.center(end+side*d),bearing,d);
                    assert Math.abs(c-last)<=1 && (d>2 || c==bearing);
                    last=c;
                }
            }
            assert BridgeAlignment.fit(path,-5,5,83,36,-48,48,(t,c)->t>0?125:terrain.applyAsInt(t,c))==null;
            assert BridgeAlignment.fit(path,-5,5,83,36,-48,48,(t,c)->t>0?Integer.MIN_VALUE:terrain.applyAsInt(t,c))==null;
        }
    }

    private static void checkBridgeBays() {
        for (int left=-36;left<=5;left++) for (int length=4;length<=98;length++) {
            int right=left+length;
            for (int phase=0;phase<11;phase++) {
                int lastSupport=left;
                for (int t=left+1;t<right;t++) {
                    var bay=BridgeBay.at(t,Math.max(left,t-25),Math.min(right,t+25),length<=25,length<=16,phase);
                    assert bay.left()>=left && bay.right()<=right && bay.left()<=t && t<=bay.right();
                    assert bay.soffit(t)>=1;
                    if (!bay.masonryPier(t)) {
                        int inward=Integer.compare(bay.right()-t+1,t-bay.left());
                        int outerDepth=2*bay.soffit(t),innerDepth=outerDepth-(bay.bevel(t)?1:0);
                        assert innerDepth>=2*bay.soffit(t+inward);
                    }
                    if (bay.support(t)) {
                        assert length<=25 || Math.min(t-left,right-t)>=5;
                        assert t-lastSupport<=16;
                        lastSupport=t;
                    } else assert bay.left()==lastSupport;
                }
                assert right-lastSupport<=16;
            }
        }
    }
    private static void checkVillageBypass() {
        var corner=new RoadSection.Parcel(-8,-8,8,8,60,80,false);
        var junction=RoadSection.survey(0,0,62,(x,z)->65,java.util.List.of(corner));
        assert !corner.intersects(junction.point().x()-3,junction.point().z()-3,junction.point().x()+3,junction.point().z()+3);
        for (boolean ew:new boolean[]{false,true}) {
            Road road=new Road(38473917L,ew,0);
            int from=48,to=88;
            var town=ew?new RoadSection.Parcel(from,-18,to,18,60,80,false):new RoadSection.Parcel(-18,from,18,to,60,80,false);
            var first=new RoadSection.Junction(road.point(0,0),65);
            var last=new RoadSection.Junction(road.point(SPACING,0),65);
            var section=new RoadSection(road,first,last,62,(x,z)->65,java.util.List.of(town));
            for (int t=1;t<SPACING;t++) {
                int c=section.center(road,t);
                assert Math.abs(c-section.center(road,t-1))<=1;
                if (t>=from && t<=to) assert Math.abs(c)>=21;
            }
        }
    }

    private static void checkTerrainRoutes() {
        for (boolean eastWest : new boolean[]{true,false}) {
            Road road=new Road(98147L,eastWest,-2);
            for (int start : new int[]{-2*SPACING,0,SPACING}) {
                RoadSection plain=new RoadSection(road,start,62,75,75,(x,z)->75);
                var ridge=(java.util.function.IntBinaryOperator)(x,z)->{
                    int t=eastWest?x:z, across=eastWest?z:x;
                    int along=Math.max(0,30-Math.abs(t-start-88));
                    return 75+Math.min(along,Math.max(0,18-Math.abs(across-road.seedCenter(t))));
                };
                RoadSection hill=new RoadSection(road,start,62,75,75,ridge);
                RoadSection coast=new RoadSection(road,start,62,64,64,(x,z)->64);
                int untouched=0,rerouted=0,prev=hill.center(road,start),last=75;
                for (int t=start;t<start+SPACING;t++) {
                    int natural=road.seedCenter(t),chosen=hill.center(road,t),deck=hill.elevation(t);
                    assert plain.center(road,t)==natural && plain.elevation(t)==75;
                    assert coast.center(road,t)==natural && coast.elevation(t)==64;
                    assert Math.abs(chosen-prev)<=1 && Math.abs(deck-last)<=1;
                    assert Math.abs(chosen-natural)<=RoadSection.SHIFT+1;
                    if (t-start<8 || start+SPACING-t<8) assert chosen==natural && deck==75;
                    untouched+=ridge.applyAsInt(eastWest?t:natural,eastWest?natural:t)-75;
                    rerouted+=ridge.applyAsInt(eastWest?t:chosen,eastWest?chosen:t)-75;
                    prev=chosen;last=deck;
                }
                assert rerouted*2<untouched : "road should skirt the ridge";
                RoadSection gorge=new RoadSection(road,start,62,86,86,(x,z)->{
                    int t=(eastWest?x:z)-start;
                    if (t<24 || t>SPACING-24) return 86;
                    return t>=72 && t<=104?64:145;
                });
                assert gorge.elevation(start+88)<=67 : "do not carry the high bank across a river";
                for (int t=start+1;t<start+SPACING;t++)
                    assert Math.abs(gorge.elevation(t)-gorge.elevation(t-1))<=1;
            }
        }
    }
    private static void checkHillsideJunction() {
        // A flat summit has four drops. The lower bench has room to enter and leave in both axes.
        java.util.function.IntBinaryOperator hill=(x,z)->82+Math.max(0,23-Math.max(0,Math.max(Math.abs(x),Math.abs(z))-4)*2);
        var shared=RoadSection.survey(0,0,62,hill);
        assert shared.y()==82 && shared.point().distanceTo(new Point(0,0))<=RoadSection.JUNCTION_SHIFT;
        for (boolean ew:new boolean[]{false,true}) {
            Road road=new Road(7319041,ew,0);
            int cross=shared.point().along(ew);
            var left=new RoadSection(road,new RoadSection.Junction(road.point(-SPACING,0),82),shared,62,hill);
            var right=new RoadSection(road,shared,new RoadSection.Junction(road.point(SPACING,0),82),62,hill);
            assert left.center(road,cross)==shared.point().across(ew) && right.center(road,cross)==left.center(road,cross);
            for (int t=cross-3;t<=cross+3;t++) assert (t<cross?left:right).elevation(t)==shared.y();
            for (int t=-SPACING;t<SPACING-12;t++) {
                int before=(t<cross?left:right).elevation(t),after=(t+12<cross?left:right).elevation(t+12);
                assert Math.abs(after-before)<=4;
            }
        }
    }

    private static void checkPiers() {
        java.util.function.IntPredicate sea=d->d>=0, land=d->d<0;
        java.util.function.IntFunction<PierLayout.Outlet> ocean=d->d>=20?PierLayout.Outlet.OPEN_SEA:PierLayout.Outlet.COAST;
        java.util.function.IntUnaryOperator bed=d->d<0?65:54;
        assert PierLayout.suitableShore(62,64,sea,land,bed,ocean);
        assert PierLayout.suitableShore(62,68,sea,land,bed,ocean);
        assert !PierLayout.suitableShore(62,69,sea,land,bed,ocean); // cliff
        assert !PierLayout.suitableShore(62,64,sea,land,bed,d->PierLayout.Outlet.COAST); // inland lake
        assert !PierLayout.suitableShore(62,64,sea,land,bed,d->d==32?PierLayout.Outlet.PACK_ICE:ocean.apply(d)); // ice across the outlet
        assert !PierLayout.suitableShore(62,64,d->false,land,bed,ocean); // freshwater inlet
        assert !PierLayout.suitableShore(62,64,sea,land,d->d==28?62:bed.applyAsInt(d),ocean); // opposite bank
        assert !PierLayout.suitableShore(62,64,d->sea.test(d) && d!=4,land,bed,ocean); // disconnected water
        assert !PierLayout.suitableShore(62,64,d->true,d->false,d->54,ocean); // offshore
        assert !PierLayout.suitableShore(62,64,sea,d->false,d->54,ocean); // no shore behind the road
        assert !PierLayout.suitableShore(62,64,d->sea.test(d) || d==-6,land,bed,ocean); // narrow spit
        assert !PierLayout.suitableShore(62,64,sea,land,d->d==-2?60:d==-8?61:d==-16?62:bed.applyAsInt(d),ocean); // exposed tidal boulder
        assert PierLayout.suitableShore(62,64,sea,land,d->d==-2?60:bed.applyAsInt(d),ocean); // supported land behind a shallow bay
        // Two approaches compete for one cove on the server seed, regardless of the order chunks open.
        assert PierLayout.hasHarbourPriority(new Road(20261121L,false,9),1790,-1);
        assert !PierLayout.hasHarbourPriority(new Road(20261121L,true,10),1612,-1);
        for (int x=-16;x<16;x++) for (int z=-16;z<16;z++) {
            for (boolean eastWest:new boolean[]{true,false}) for (int sign:new int[]{-1,1}) {
                for (int side:new int[]{-1,1}) for (int rise=-2;rise<=4;rise++) {
                    var pier=new PierLayout(new Point(x,z),eastWest,sign,side,62,64+rise);
                    assert pier.floor(0)==64+rise && pier.floor(6)==64;
                    for (int s=0;s<=PierLayout.LENGTH;s++) {
                        assert Math.abs(pier.floor(s+1)-pier.floor(s))<=1;
                        assert pier.platform(s,0);
                        for (int u:new int[]{PierLayout.MIN_SIDE,PierLayout.MAX_SIDE}) {
                            var p=pier.point(s,u);
                            assert Math.abs((p.x()>>4)-(x>>4))<=1 && Math.abs((p.z()>>4)-(z>>4))<=1;
                        }
                    }
                    assert pier.point(0,0).equals(pier.shore());
                }
            }
        }
    }
}
