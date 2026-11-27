package dev.fourwinds.roads;

/** Two consecutive supports, measured along the road. Banks are supports too. */
final class BridgeBay {
    private final int left;
    private final int right;

    BridgeBay(int left, int right) {
        this.left = left;
        this.right = right;
    }
    public int left() { return left; }
    public int right() { return right; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BridgeBay)) return false;
        BridgeBay rhs = (BridgeBay)other;
        return left == rhs.left
                && right == rhs.right;
    }

    @Override public int hashCode() {
        int hash = left;
        hash = 31 * hash + right;
        return hash;
    }

    static BridgeBay at(int t,int leftBank,int rightBank,boolean whole,boolean timber,int phase) {
        int pitch=timber?7:11;
        if (whole) {
            int length=rightBank-leftBank;
            int count=Math.max(1,(length+pitch/2)/pitch);
            int left=leftBank;
            for (int i=1;i<=count;i++) {
                int right=leftBank+(length*i+count/2)/count;
                if (t<=right) return new BridgeBay(left,right);
                left=right;
            }
        }
        // Keep full-sized openings at both shores of wide channels.
        int left=t-Math.floorMod(t-phase,pitch),right=left+pitch;
        if (left<leftBank+5) left=leftBank;
        if (right>rightBank-5) right=rightBank;
        if (left>rightBank-5) left-=pitch;
        if (right<leftBank+5) right+=pitch;
        return new BridgeBay(left,right);
    }

    boolean support(int t) { return t==left || t==right; }
    boolean masonryPier(int t) { return support(t) || t==left+1; }
    boolean bevel(int t) {
        int towardCrown=Integer.compare(right-t+1,t-left);
        return soffit(t)>soffit(t+towardCrown);
    }
    int soffit(int t) {
        int width=right-left;
        if (width<=4) return 1;
        double x=RoadLayout.clamp((2.0*t-left-right-1)/(width-3),-1,1);
        double crown=Math.sqrt(Math.max(0,1-x*x));
        double rise=width<=16?Math.min(6,(width-2)*.50):Math.min(10,(width-2)*.44);
        return 1+(int)Math.round((1-crown)*rise);
    }
}
