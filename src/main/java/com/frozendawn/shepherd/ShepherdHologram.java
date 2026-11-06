package com.frozendawn.shepherd;

import java.util.ArrayList;
import java.util.List;

/**
 * What her hologram does at each age, as `hologram-mockup.gif` and `hologram-arrive-mockup.gif` draw it. It all follows from the age, so the
 * client needs nothing from the server but which figure. Rows count up from the figure's feet, half size, 16 to the block.
 */
public final class ShepherdHologram {
    /** After the beam: how long the figure takes to form. */
    public static final int FORM_TICKS = 40;
    /** When it ends: the figure falls away, then the beam draws back, as `hologram-leave-mockup.gif` draws it. */
    public static final int LEAVE_FADE_TICKS = 24, LEAVE_BEAM_TICKS = 9, LEAVE_TICKS = LEAVE_FADE_TICKS + LEAVE_BEAM_TICKS;
    /** How far through leaving it is, while it isn't. */
    public static final int STAYING = -1;
    public static final int ROWS = 16, MAX_SHIFT = 4;
    /** One frame of the mockup. */
    private static final int FRAME_TICKS = 3, EGG_TEAR_TICKS = 6;
    private static final int CHIN_FRAMES = 24, CHIN_FROM = 10, CHIN_TO = 19, BAND_FRAMES = 20;
    private static final int BARS = 3, FLECKS = 14, FLECK_ROWS = 19, BAR_ROWS = 18;
    private static final int HEAD_BOTTOM = 15, CHEST_TOP = 11, LEG_TOP = 5;
    private static final int SKIN_HEAD_ROW = 8, SKIN_LIMB_ROW = 20, SKIN_LEG_COLUMNS = 16, SKIN_LAYER = 16;
    private static final int EYE_TOP = 10, EYE_BOTTOM = 11;
    private static final List<Voxel> HOODED = hood(), SMALL_HER = smallLantern();

    public enum Shade { HOLO, HOLO_DARK, BODY, GLOW }

    /** A streak of static across the figure, from {@code left} in texels from its middle. */
    public record Bar(int y, int left, int length) {}

    public record Fleck(int x, int y, int z, boolean bright) {}

    public record Voxel(int x, int y, int z, Shade shade) {}

    private ShepherdHologram() {}

    private static int hash(int a, int b, int c) {
        return (a * 73856093 ^ b * 19349663 ^ c * 83492791) & 0xFFFF;
    }

    private static int frame(int age) {
        return age / FRAME_TICKS;
    }

    public static int tearWidth(int age) {
        return 1 + hash(frame(age), 2, 3) % 3;
    }

    /** The lowest row a tear slides, kept so all of it is on the figure. */
    public static int tearRow(int age) {
        return hash(frame(age) / 2, 1, 7) % (ROWS - tearWidth(age) + 1);
    }

    public static int tearShift(int age) {
        return hash(frame(age) / 2, 4, 9) % (MAX_SHIFT * 2 + 1) - MAX_SHIFT;
    }

    /** The lower of two rows that drop out. */
    public static int gapRow(int age) {
        return hash(frame(age) / 3, 8, 8) % 12;
    }

    /** Chin in hand: it stops to think. */
    public static boolean chin(int age) {
        int frame = frame(age) % CHIN_FRAMES;
        return frame >= CHIN_FROM && frame < CHIN_TO;
    }

    public static List<Bar> bars(int age) {
        List<Bar> bars = new ArrayList<>();
        int frame = frame(age);
        for (int i = 0; i < BARS; i++) bars.add(new Bar(hash(frame, i, 5) % BAR_ROWS, -6 + hash(i, frame / 2, 2) % 6, 4 + hash(frame, i, 6) % 8));
        return bars;
    }

    public static List<Fleck> flecks(int age) {
        List<Fleck> flecks = new ArrayList<>();
        for (int i = 0; i < FLECKS; i++) {
            int h = hash(frame(age), i, 11);
            flecks.add(new Fleck(h % 15 - 7, h / 16 % FLECK_ROWS, h / 256 % 5 - 2, h % 3 != 0));
        }
        return flecks;
    }

    /** The bright row climbing the figure, which is off it for a moment between climbs. */
    public static int band(int age) {
        return frame(age) % BAND_FRAMES;
    }

    /** Every third row is dim, so it reads as light and not a statue. */
    public static boolean dimRow(int row) {
        return row % 3 == 2;
    }

    /** How far along the beam has reached. */
    public static float beam(int age) {
        return Math.min(1, (age + 1) / (float) ShepherdProjection.BEAM_TICKS);
    }

    /** Whether this texel of its skin has arrived: a few at a time in the beam, the eyes last. */
    public static boolean formed(int age, int x, int y) {
        float formed = Math.max(0, (age - ShepherdProjection.BEAM_TICKS + 1) / (float) FORM_TICKS);
        return formed >= 1 || !eye(x, y) && hash(x, y, 0) % 100 < formed * 100;
    }

    /** How much of the figure has gone. */
    public static float faded(int leaving) {
        return leaving == STAYING ? 0 : Math.min(1, (leaving + 1) / (float) LEAVE_FADE_TICKS);
    }

    /** Whether this texel of its skin has left: the eyes first, then a few at a time. */
    public static boolean gone(int leaving, int x, int y) {
        return leaving != STAYING && (eye(x, y) || hash(x, y, 0) % 100 < faded(leaving) * 100);
    }

    /** How much of the beam is still out, which is all of it until the figure has gone. */
    public static float beamLeft(int leaving) {
        return 1 - Math.clamp((leaving - LEAVE_FADE_TICKS + 1) / (float) LEAVE_BEAM_TICKS, 0F, 1F);
    }

    /** The two eyes on a head's front, in skin texels. */
    public static boolean eye(int x, int y) {
        return y >= EYE_TOP && y <= EYE_BOTTOM && (x == 9 || x == 10 || x == 13 || x == 14);
    }

    /** Which of the figure's rows a texel of a humanoid skin lands on. */
    public static int row(int x, int y) {
        int skin = y < SKIN_LAYER * 2 ? y : SKIN_LAYER + y % SKIN_LAYER;
        if (skin < SKIN_LAYER) return HEAD_BOTTOM - Math.clamp((skin - SKIN_HEAD_ROW) / 2, 0, 3);
        int down = Math.clamp((skin - SKIN_LIMB_ROW) / 2, 0, 5);
        return (x < SKIN_LEG_COLUMNS ? LEG_TOP : CHEST_TOP) - down;
    }

    /** At each end of the egg's moment both shapes show, row by row. */
    public static boolean eggTearing(int eggAge) {
        return eggAge < EGG_TEAR_TICKS || eggAge >= ShepherdProjection.EGG_TICKS - EGG_TEAR_TICKS;
    }

    /** One row in three of the other shape is missing while it tears. */
    public static boolean eggRowTorn(int eggAge, int row) {
        return eggTearing(eggAge) && (row + frame(eggAge)) % 3 == 1;
    }

    /** The egg's small figure on one knee, with its drawn file number. */
    public static List<Voxel> hooded() {
        return HOODED;
    }

    /** The small her, beside the small you. */
    public static List<Voxel> smallHer() {
        return SMALL_HER;
    }

    private static List<Voxel> smallLantern() {
        List<Voxel> cells = new ArrayList<>();
        for (int x = 1; x <= 8; x++) {
            for (int y = 1; y <= 7; y++) cells.add(new Voxel(x, y, 0, x == 1 || x == 8 || y == 1 || y == 7 ? Shade.BODY : Shade.HOLO_DARK));
        }
        for (int[] texel : new int[][] {{3, 5}, {6, 5}, {4, 3}, {5, 3}}) cells.add(new Voxel(texel[0], texel[1], 1, Shade.GLOW));
        return List.copyOf(cells);
    }

    private static List<Voxel> hood() {
        List<Voxel> cells = new ArrayList<>();
        part(cells, -2, 0, -5, 0, 2, 1, Shade.HOLO);
        part(cells, -2, 2, -1, 0, 6, 1, Shade.HOLO);
        part(cells, 0, 4, -1, 2, 6, 4, Shade.HOLO);
        part(cells, 0, 0, 2, 2, 4, 4, Shade.HOLO);
        part(cells, -3, 3, -2, 3, 6, 2, Shade.BODY);
        part(cells, -3, 6, -2, 3, 12, 2, Shade.BODY);
        part(cells, -3, 8, 2, -1, 10, 5, Shade.BODY);
        part(cells, 1, 8, 2, 3, 10, 5, Shade.BODY);
        part(cells, -3, 12, -3, 3, 16, 2, Shade.BODY);
        part(cells, -2, 16, -2, 2, 17, 1, Shade.BODY);
        part(cells, -2, 12, 2, 2, 15, 3, Shade.HOLO_DARK);
        part(cells, -1, 8, 5, 1, 10, 7, Shade.GLOW);
        String[] number = {"###", "#.#", "###", "#..", "###"};
        for (int left : new int[] {-11, -7}) {
            for (int y = 0; y < number.length; y++) {
                for (int x = 0; x < 3; x++) if (number[y].charAt(x) == '#') cells.add(new Voxel(left + x, 11 + y, 0, Shade.GLOW));
            }
        }
        return List.copyOf(cells);
    }

    private static void part(List<Voxel> cells, int x0, int y0, int z0, int x1, int y1, int z1, Shade shade) {
        for (int x = x0; x < x1; x++) for (int y = y0; y < y1; y++) for (int z = z0; z < z1; z++) cells.add(new Voxel(x, y, z, shade));
    }
}
