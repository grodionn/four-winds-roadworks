package dev.fourwinds.roads;

import java.util.List;
import static dev.fourwinds.roads.RoadLayout.*;
import static dev.fourwinds.roads.LegacyRoads.Material.*;

final class LegacyRoadside {
    static void place(LegacyRoads network, LegacyRoads.Land land, Road road, int minX, int minZ, List<Path> paths) {
        int along = road.eastWest() ? minX : minZ;
        int group = Math.floorDiv(along+8,2*SPACING), t = stopStation(road,group);
        if (t<along+2 || t>along+13) return;
        int side = roll(land.seed()+2137,road.index(),group,2)==0 ? -1 : 1;
        Point center = network.on(land,road,t);
        boolean roof = roll(land.seed()+2191,road.index(),group,5)<2;
        int offset = edge(road.width(),side) + 3*side;
        Point front = new Point(center.x()+(road.eastWest()?0:offset),center.z()+(road.eastWest()?offset:0));
        int floor=land.bed(front.x(),front.z());
        if (Math.abs(floor-network.height(land,road,t))>1) return;
        for (int u=-2;u<=2;u++) for (int v=0;v<=3;v++) {
            Point q=point(front,u,v,road.eastWest(),side);
            if (q.x()<minX || q.x()>=minX+16 || q.z()<minZ || q.z()>=minZ+16 || land.wet(q.x(),q.z())
                    || Math.abs(land.bed(q.x(),q.z())-floor)>1 || land.occupied(q.x(),floor,q.z())) return;
            for (Path path:paths) {
                int station=q.along(path.eastWest());
                if (path.contains(station) && Math.abs(q.across(path.eastWest())-network.on(land,path,station).across(path.eastWest()))<=path.width()/2+1) return;
            }
            for (int y=floor+1;y<=floor+(roof?5:2);y++) if (!land.natural(q.x(),y,q.z())) return;
        }
        for (int u=-2;u<=2;u++) for (int v=0;v<=3;v++) {
            Point q=point(front,u,v,road.eastWest(),side);
            int x=q.x(),z=q.z(),bed=land.bed(x,z);
            for (int y=bed+1;y<floor;y++) land.put(x,y,z,FILL,0,0);
            land.put(x,floor,z,GRAVEL,0,0);
            for (int y=floor+1;y<=floor+(roof?5:2);y++) land.put(x,y,z,AIR,0,0);
            if (v==2 && Math.abs(u)<=1) land.put(x,floor+1,z,roof?LOW_TIMBER:LOW_STONE,0,0);
            if (!roof) continue;
            if (Math.abs(u)==2 && (v==0 || v==3)) for (int y=1;y<=3;y++) land.put(x,floor+y,z,POST,0,0);
            if (v == 0 || v == 3) {
                int uphill = v == 0 ? side : -side;
                land.put(x,floor+3,z,BEAM,road.eastWest()?0:1,road.eastWest()?1:0);
                land.put(x,floor+4,z,TIMBER_STAIR,road.eastWest()?0:uphill,road.eastWest()?uphill:0);
            } else {
                land.put(x,floor+4,z,PLANK,0,0);
                land.put(x,floor+5,z,LOW_TIMBER,0,0);
            }
        }
    }
    private static Point point(Point front,int u,int v,boolean ew,int side) {
        return new Point(front.x()+(ew?u:v*side),front.z()+(ew?v*side:u));
    }
}
