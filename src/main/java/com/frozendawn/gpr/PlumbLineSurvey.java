package com.frozendawn.gpr;

import com.frozendawn.gpr.PlumbLineScan.Kind;
import com.frozendawn.gpr.PlumbLineScan.Return;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * A printed Survey Sheet: one pulse's returns, where it was taken and when. Held, it shows those returns within the
 * pulse's radius until the ink fades a day after printing. It is a snapshot and never updates.
 */
public record PlumbLineSurvey(String dimension, BlockPos scanner, int radius, long printedAt, List<Return> returns) {
    /** One in-game day. */
    public static final long INK_TICKS = 24_000;
    /** Held within the pulse's radius plus this margin of the scanner, the sheet draws its returns. */
    public static final int VIEW_MARGIN = 16;

    public PlumbLineSurvey {
        returns = List.copyOf(returns.size() > PlumbLinePolicy.MAX_RETURNS ? returns.subList(0, PlumbLinePolicy.MAX_RETURNS) : returns);
    }

    public boolean faded(long gameTime) { return gameTime - printedAt >= INK_TICKS || gameTime < printedAt; }

    public boolean inRange(String dimension, double x, double z) {
        double dx = x - (scanner.getX() + .5), dz = z - (scanner.getZ() + .5), reach = radius + VIEW_MARGIN;
        return this.dimension.equals(dimension) && dx * dx + dz * dz <= reach * reach;
    }

    private static final Codec<Kind> KIND = Codec.STRING.xmap(s -> { try { return Kind.valueOf(s); } catch (IllegalArgumentException e) { return Kind.VOID; } }, Kind::name);
    public static final Codec<Return> RETURN_CODEC = RecordCodecBuilder.create(i -> i.group(
            KIND.fieldOf("kind").forGetter(Return::kind), Codec.string(0, 128).fieldOf("label").forGetter(Return::label),
            Codec.INT.fieldOf("min_x").forGetter(Return::minX), Codec.INT.fieldOf("min_y").forGetter(Return::minY), Codec.INT.fieldOf("min_z").forGetter(Return::minZ),
            Codec.INT.fieldOf("max_x").forGetter(Return::maxX), Codec.INT.fieldOf("max_y").forGetter(Return::maxY), Codec.INT.fieldOf("max_z").forGetter(Return::maxZ),
            Codec.BOOL.fieldOf("precise").forGetter(Return::precise)).apply(i, Return::new));
    public static final Codec<PlumbLineSurvey> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.string(0, 256).fieldOf("dimension").forGetter(PlumbLineSurvey::dimension),
            BlockPos.CODEC.fieldOf("scanner").forGetter(PlumbLineSurvey::scanner),
            Codec.intRange(1, 512).fieldOf("radius").forGetter(PlumbLineSurvey::radius),
            Codec.LONG.fieldOf("printed_at").forGetter(PlumbLineSurvey::printedAt),
            RETURN_CODEC.listOf(0, PlumbLinePolicy.MAX_RETURNS).fieldOf("returns").forGetter(PlumbLineSurvey::returns)).apply(i, PlumbLineSurvey::new));
}
