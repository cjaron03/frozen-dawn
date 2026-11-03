package com.frozendawn.cyclone;
import java.util.Optional;
public final class CycloneTransitions {
    public enum Action { NONE, PLAN, START, COLLAPSE, END }
    private CycloneTransitions() {}
    public static Action decide(int phase,boolean erased,boolean enabled,boolean planned,boolean due,
            Optional<CycloneStorm> active,long time,boolean eligible,boolean masterFight) {
        // Terminal conditions must precede the phase-6 return; a collapsed storm must actually end.
        if(active.isPresent()) {
            var s=active.get();if(erased || phase<5 || time>=s.endGameTime())return Action.END;
            if((phase>=6 || !enabled) && !s.collapsing())return Action.COLLAPSE;
            return Action.NONE;
        }
        if(erased || phase!=5 || !enabled)return Action.NONE;
        if(!planned)return Action.PLAN;
        return due && eligible && !masterFight?Action.START:Action.NONE;
    }
    public static CycloneSeverity effectiveSeverity(CycloneSeverity s,boolean cat) {
        return s==CycloneSeverity.CATASTROPHIC&&!cat?CycloneSeverity.SEVERE:s;
    }
}
