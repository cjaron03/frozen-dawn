package com.frozendawn.block;

import com.frozendawn.init.ModParticles;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A superconducting storage cell; capacity scales with how many the box holds. Its Controller sets how it looks,
 * which follows the thermal stage, never the charge. */
public final class SuperconductingCellBlock extends HyperionPartBlock {
    public enum Look implements StringRepresentable {
        FROSTED, PLAIN, STRESSED, CRITICAL, SCARRED;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public static final EnumProperty<Look> LOOK = EnumProperty.create("look", Look.class);
    public SuperconductingCellBlock(Properties p) {
        super(p.lightLevel(s -> switch (s.getValue(LOOK)) { case STRESSED -> 3; case CRITICAL -> 7; default -> 0; }));
        registerDefaultState(stateDefinition.any().setValue(LOOK, Look.PLAIN));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LOOK); }
    /** Frosted cells spill sinking vapour off open sides; stressed and critical cells steam upward, critical more. */
    @Override public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
        var look = s.getValue(LOOK);
        if (look == Look.FROSTED) {
            if (r.nextInt(3) != 0) return;
            var d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            if (!open(l, p, d)) return;
            double along = (r.nextDouble() - 0.5) * 0.8;
            double x = p.getX() + 0.5 + d.getStepX() * 0.55 + (d.getStepX() == 0 ? along : 0), z = p.getZ() + 0.5 + d.getStepZ() * 0.55 + (d.getStepZ() == 0 ? along : 0);
            l.addParticle(ModParticles.COLD_VAPOUR.get(), x, p.getY() + 0.35 + r.nextDouble() * 0.55, z, d.getStepX() * 0.012, -0.004, d.getStepZ() * 0.012);
        } else if (look == Look.STRESSED || look == Look.CRITICAL) {
            int puffs = look == Look.CRITICAL ? 2 : r.nextInt(3) == 0 ? 1 : 0;
            for (int i = 0; i < puffs; i++) {
                var d = open(l, p, Direction.UP) ? Direction.UP : Direction.Plane.HORIZONTAL.getRandomDirection(r);
                if (!open(l, p, d)) continue;
                double x = p.getX() + 0.5 + d.getStepX() * 0.55 + (d.getStepX() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
                double y = p.getY() + 0.5 + d.getStepY() * 0.55 + (d.getStepY() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
                double z = p.getZ() + 0.5 + d.getStepZ() * 0.55 + (d.getStepZ() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
                l.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, 0, 0.03 + r.nextDouble() * 0.03, 0);
            }
        }
    }
    private static boolean open(Level l, BlockPos p, Direction d) { var n = p.relative(d); return !l.getBlockState(n).isSolidRender(l, n); }
}
