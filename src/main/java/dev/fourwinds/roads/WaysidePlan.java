package dev.fourwinds.roads;

/** Coordinates run along the road (u), away from it (v), and up from the native ground (h). */
final class WaysidePlan {
    enum Piece { BASE, STEP, LOW_STEP, POST, TIE, CROSS_TIE, SEAT, ROOF_IN, ROOF_OUT, RIDGE }

    interface Builder { void place(int u,int v,int h,Piece piece); }

    static int half(boolean shelter) { return shelter?3:1; }
    static int back(boolean shelter) { return shelter?5:2; }
    static int headroom(boolean shelter,int v) {
        if (!shelter || v==0) return 3;
        return 5-Math.abs(v-3);
    }
    static boolean footing(boolean shelter,int u,int v) {
        return shelter && Math.abs(u)==2 && (v==2 || v==4);
    }
    static boolean floor(boolean shelter,int u,int v) {
        if (!shelter) return v==1 || u==0;
        return footing(true,u,v) || Math.abs(u)<=1 && (v>=2 && v<=4 || u==0 && v<2);
    }

    static void draw(boolean shelter,Builder b) {
        if (!shelter) {
            b.place(-1,1,1,Piece.LOW_STEP);
            b.place(0,1,1,Piece.STEP);
            b.place(1,1,1,Piece.STEP);
            b.place(1,1,2,Piece.LOW_STEP);
            return;
        }
        for (int u=-3;u<=3;u++) {
            for (int v=1;v<=5;v++)
                b.place(u,v,headroom(true,v),v==3?Piece.RIDGE:v<3?Piece.ROOF_IN:Piece.ROOF_OUT);
            if (Math.abs(u)>2) continue;
            for (int v=2;v<=4;v+=2) {
                if (Math.abs(u)==2) {
                    b.place(u,v,1,Piece.BASE);
                    b.place(u,v,2,Piece.POST);
                }
                b.place(u,v,3,Piece.TIE);
            }
            if (Math.abs(u)==2) {
                b.place(u,3,3,Piece.CROSS_TIE);
                b.place(u,3,4,Piece.POST);
            } else b.place(u,4,1,Piece.SEAT);
        }
    }
}
