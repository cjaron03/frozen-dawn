package com.frozendawn.power;

import com.frozendawn.power.HyperionPhysics.Stage;

/** What every part of the silo shows for its synced state: the window bars, pipes, lamps, rings and vent, following
 * the silo design's state table. Pure: the client only draws what this says. */
public final class HyperionGauge {
    public enum Colour { OFF, BLUE, AMBER, RED, DEAD }
    public enum Rings { FLOATING, RESTING }
    public enum Motion { CALM, WOBBLE, SHUDDER }
    public enum Pipes { FROSTED, DRIPPING, BARE }
    public enum Flow { NONE, IN, OUT }
    public enum Lamps { OFF, BLUE, AMBER, RED }
    public enum Vent { NONE, BREATH, WISPS, HEAVY_WISPS }
    /** ticksSinceQuench is Long.MAX_VALUE when it never quenched. */
    public record Inputs(Stage stage, boolean formed, long stored, long capacity, long flowIn, long flowOut, long rateWatts, int height,
                         boolean scarred, long ticksSinceQuench) {}
    /** bars and lit count blocks of window; breathing, flickering and collapseShown are -1 when unused; spin is turns per minute. */
    public record State(int bars, double lit, Colour colour, int breathing, int flickering, boolean ember, int collapseShown, Rings rings,
                        double spin, Motion motion, Pipes pipes, Flow flow, Lamps lamps, Vent vent) {}
    /** Ticks each bar takes to go out, top to bottom, after a quench. */
    public static final int COLLAPSE_TICKS_PER_BAR = 3;
    public static final double IDLE_SPIN = 0.5, FLOW_SPIN = 3.5;
    private HyperionGauge() {}

    public static State of(Inputs in) {
        if (!in.formed()) return new State(0, 0, Colour.OFF, -1, -1, false, -1, Rings.RESTING, 0, Motion.CALM, Pipes.BARE, Flow.NONE, Lamps.OFF, Vent.NONE);
        int bars = in.height();
        double lit = in.capacity() <= 0 ? 0 : bars * (double) in.stored() / in.capacity();
        Stage stage = in.stage();
        boolean full = in.stored() >= in.capacity();
        int breathing = in.flowIn() > in.flowOut() && stage == Stage.COLD && !full ? (int) Math.floor(lit) : -1;
        int flickering = in.flowOut() > in.flowIn() && in.stored() > 0 ? (int) Math.ceil(lit) - 1 : -1;
        Colour colour; Pipes pipes; Lamps lamps; Vent vent; Motion motion = Motion.CALM; boolean ember = false;
        switch (stage) {
            case COLD -> { colour = Colour.BLUE; pipes = Pipes.FROSTED; lamps = Lamps.BLUE; vent = in.stored() > 0 ? Vent.BREATH : Vent.NONE; }
            case CHIRP, WARNING -> { colour = Colour.AMBER; pipes = Pipes.DRIPPING; lamps = Lamps.AMBER; vent = Vent.WISPS; motion = Motion.WOBBLE; }
            case FINAL -> { colour = Colour.RED; pipes = Pipes.DRIPPING; lamps = Lamps.RED; vent = Vent.HEAVY_WISPS; motion = Motion.SHUDDER; }
            default -> { colour = in.scarred() ? Colour.DEAD : Colour.OFF; ember = in.scarred(); pipes = Pipes.BARE; lamps = Lamps.OFF; vent = Vent.NONE; }
        }
        int collapseShown = -1;
        if (in.ticksSinceQuench() >= 0 && in.ticksSinceQuench() < (long) COLLAPSE_TICKS_PER_BAR * bars) {
            collapseShown = bars - (int) (in.ticksSinceQuench() / COLLAPSE_TICKS_PER_BAR); colour = Colour.RED; ember = false;
        }
        Rings rings = in.stored() > 0 && stage != Stage.WARM ? Rings.FLOATING : Rings.RESTING;
        long flow = Math.max(in.flowIn(), in.flowOut());
        double spin = IDLE_SPIN + FLOW_SPIN * (in.rateWatts() <= 0 ? 0 : Math.min(1, flow / (double) in.rateWatts()));
        Flow direction = in.flowIn() > in.flowOut() ? Flow.IN : in.flowOut() > in.flowIn() ? Flow.OUT : Flow.NONE;
        return new State(bars, lit, colour, breathing, flickering, ember, collapseShown, rings, spin, motion, pipes, direction, lamps, vent);
    }
}
