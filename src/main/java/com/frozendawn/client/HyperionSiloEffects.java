package com.frozendawn.client;

import com.frozendawn.block.CryocoolerBlock;
import com.frozendawn.block.HyperionControllerBlock;
import com.frozendawn.block.HyperionControllerBlockEntity;
import com.frozendawn.block.HyperionFacingPartBlock;
import com.frozendawn.block.HyperionMultiblock;
import com.frozendawn.init.ModParticles;
import com.frozendawn.power.HyperionGauge;
import com.frozendawn.power.HyperionSiloLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;

/** The silo's client particles, once a tick from the Controller's client ticker: cold vapour sinking off frosted pipes,
 * drips from thawing ones, an idle cold breath from the vent, and smoke wisps from it while hot. The quench steam
 * itself stays in HyperionVentJets. */
public final class HyperionSiloEffects {
    /** Ticks between breaths, give or take BREATH_SPREAD, seeded per Controller. */
    private static final int BREATH_TICKS = 80, BREATH_SPREAD = 20;
    private HyperionSiloEffects() {}

    /** The gauge for a Controller's synced snapshot at this game time; null unless formed. */
    public static HyperionGauge.State gauge(HyperionControllerBlockEntity be, long now) {
        var s = be.snapshot();
        if (s.problem() != HyperionMultiblock.Problem.NONE || s.height() <= 0) return null;
        long since = s.lastQuenchTick() == Long.MIN_VALUE ? Long.MAX_VALUE : Math.max(0, now - s.lastQuenchTick());
        return HyperionGauge.of(new HyperionGauge.Inputs(s.stage(), true, s.stored(), s.capacity(), s.flowIn(), s.flowOut(), s.rateWatts(),
                s.height(), s.scarred(), since));
    }

    public static void tick(HyperionControllerBlockEntity be) {
        var level = be.getLevel();
        if (level == null) return;
        long now = level.getGameTime();
        var g = gauge(be, now);
        var front = be.getBlockState().getValue(HyperionControllerBlock.FACING);
        if (g == null || !front.getAxis().isHorizontal()) return;
        var r = level.random;
        for (var cooler : be.snapshot().coolers()) {
            var state = level.getBlockState(cooler);
            if (!(state.getBlock() instanceof CryocoolerBlock)) continue;
            var d = state.getValue(HyperionFacingPartBlock.FACING);
            if (!d.getAxis().isHorizontal()) continue;
            double out = 0.5 + (HyperionSiloLayout.PIPE_OUT_PX - HyperionSiloLayout.PIPE_PX / 2.0) / 16;
            double x = cooler.getX() + 0.5 + d.getStepX() * out, y = cooler.getY() + HyperionSiloLayout.PIPE_Y, z = cooler.getZ() + 0.5 + d.getStepZ() * out;
            if (g.pipes() == HyperionGauge.Pipes.FROSTED && r.nextInt(4) == 0)
                level.addParticle(ModParticles.COLD_VAPOUR.get(), x + (r.nextDouble() - 0.5) * 0.3, y + r.nextDouble() * 0.2, z + (r.nextDouble() - 0.5) * 0.3,
                        d.getStepX() * 0.01, -0.006, d.getStepZ() * 0.01);
            if (g.pipes() == HyperionGauge.Pipes.DRIPPING && r.nextInt(10) == 0)
                level.addParticle(ParticleTypes.FALLING_WATER, x + (r.nextDouble() - 0.5) * 0.3, y - 0.1, z + (r.nextDouble() - 0.5) * 0.3, 0, 0, 0);
        }
        var pos = be.getBlockPos(); int height = be.snapshot().height();
        double vx = pos.getX() + 0.5 - front.getStepX(), vy = pos.getY() + height + 0.05, vz = pos.getZ() + 0.5 - front.getStepZ();
        switch (g.vent()) {
            case BREATH -> {
                long seed = pos.asLong() * 0x9E3779B97F4A7C15L;
                int every = BREATH_TICKS - BREATH_SPREAD + (int) Math.floorMod(seed >>> 17, 2 * BREATH_SPREAD + 1);
                if (Math.floorMod(now, every) == 0) for (int i = 0; i < 3; i++)
                    level.addParticle(ModParticles.COLD_VAPOUR.get(), vx + (r.nextDouble() - 0.5) * 0.3, vy, vz + (r.nextDouble() - 0.5) * 0.3,
                            (r.nextDouble() - 0.5) * 0.01, 0.04, (r.nextDouble() - 0.5) * 0.01);
            }
            case WISPS -> { if (r.nextInt(4) == 0) wisp(level, vx, vy, vz, r.nextDouble(), r.nextDouble()); }
            case HEAVY_WISPS -> wisp(level, vx, vy, vz, r.nextDouble(), r.nextDouble());
            default -> {}
        }
    }
    private static void wisp(Level level, double x, double y, double z, double a, double b) {
        level.addParticle(ParticleTypes.WHITE_SMOKE, x + (a - 0.5) * 0.5, y, z + (b - 0.5) * 0.5, 0, 0.03 + 0.03 * a, 0);
    }
    /** Air blocks under a cooler's elbow before the first solid one, looking no deeper than the pipe may run; -1 for none. */
    public static int solidBelow(Level level, BlockPos cooler, Direction out) {
        var elbow = cooler.relative(out);
        for (int i = 1; i <= HyperionSiloLayout.MAX_PIPE_AIR + 1; i++) {
            var p = elbow.below(i);
            if (!level.isLoaded(p)) return -1;
            if (level.getBlockState(p).isSolidRender(level, p)) return i - 1;
        }
        return -1;
    }
}
