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
        int offset = edge(road.width(),side) + 2*side;
        Point front = new Point(center.x()+(road.eastWest()?0:offset),center.z()+(road.eastWest()?offset:0));
        int floor=land.bed(front.x(),front.z());
        int half=WaysidePlan.half(roof), back=WaysidePlan.back(roof);
        if (floor<2 || floor+6>=255 || Math.abs(floor-network.height(land,road,t))>1) return;
        for (int u=-half;u<=half;u++) for (int v=0;v<=back;v++) {
            Point q=point(front,u,v,road.eastWest(),side);
            int x=q.x(),z=q.z(),bed=land.bed(x,z);
            if (x<minX || x>=minX+16 || z<minZ || z>=minZ+16 || land.wet(x,z) || Math.abs(bed-floor)>1
                    || land.occupied(x,floor,z) || !land.firm(x,bed,z) || !land.firm(x,bed-1,z)) return;
            for (Path path:paths) {
                int station=q.along(path.eastWest());
                if (path.contains(station) && depth(path.width(),q.across(path.eastWest())-network.on(land,path,station).across(path.eastWest()))>=0) return;
            }
            for (int y=bed+1;y<=floor+WaysidePlan.headroom(roof,v);y++) if (!land.openForStop(x,y,z)) return;
        }
        for (int u=-half;u<=half;u++) for (int v=0;v<=back;v++) {
            if (!WaysidePlan.floor(roof,u,v)) continue;
            Point q=point(front,u,v,road.eastWest(),side);
            for (int h=1;h<=WaysidePlan.headroom(roof,v);h++) land.put(q.x(),floor+h,q.z(),AIR,0,0);
            boolean base=WaysidePlan.footing(roof,u,v) || !roof && v==1;
            land.put(q.x(),floor,q.z(),base?COBBLE:PATH,0,0);
        }
        WaysidePlan.draw(roof,(u,v,h,piece)->{
            Point q=point(front,u,v,road.eastWest(),side);
            int dir=piece==WaysidePlan.Piece.ROOF_OUT?-side:side;
            boolean alongRoad=piece==WaysidePlan.Piece.TIE;
            int dx=road.eastWest()==alongRoad?dir:0,dz=road.eastWest()!=alongRoad?dir:0;
            LegacyRoads.Material block;
            switch (piece) {
                case BASE: case STEP: block=COBBLE;break;
                case LOW_STEP: block=LOW_STONE;break;
                case POST: block=SLIM_POST;break;
                case TIE: case CROSS_TIE: block=BEAM;break;
                case SEAT: case ROOF_IN: case ROOF_OUT: block=TIMBER_STAIR;break;
                case RIDGE: block=LOW_TIMBER;break;
                default: throw new AssertionError(piece);
            }
            land.put(q.x(),floor+h,q.z(),block,dx,dz);
        });
    }
    private static Point point(Point front,int u,int v,boolean ew,int side) {
        return new Point(front.x()+(ew?u:v*side),front.z()+(ew?v*side:u));
    }
}
