package com.frozendawn.oxygen;
/** No concentration is inferred from production rates. Pressure/breathability remain external authorities. */
public final class OxygenStatus {
    private OxygenStatus() {}
    public enum Mode { WAITING, SUPPLIED, LOW, STRAINED, TRAPPED_AIR, NO_AIR, NO_SEAL, OPEN_AIR, NO_OXYGEN }
    public static Mode room(boolean complete,boolean air,double supply,double demand) {
        if(!air)return Mode.NO_AIR;if(!complete)return Mode.WAITING;if(supply<=0)return Mode.TRAPPED_AIR;
        if(demand> supply+1e-9)return Mode.STRAINED;return demand>=supply*.8?Mode.LOW:Mode.SUPPLIED;
    }
    public static int color(Mode mode,long tick) {
        int bright=switch(mode){case NO_AIR,NO_SEAL,NO_OXYGEN->0xFF5555;case LOW,STRAINED,TRAPPED_AIR->0xFFCF4A;case SUPPLIED,OPEN_AIR->0x75EEA6;default->0xAAB7C1;};
        boolean blink=mode==Mode.NO_OXYGEN||mode==Mode.NO_AIR||mode==Mode.NO_SEAL||mode==Mode.LOW||mode==Mode.STRAINED||mode==Mode.TRAPPED_AIR;
        return blink&&Math.floorMod(tick,20)>=12?0x596169:bright;
    }
}
