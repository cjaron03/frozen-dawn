package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.PlasmaSight;
import com.frozendawn.block.SfrControllerBlock;
import com.frozendawn.block.SfrControllerBlockEntity;
import com.frozendawn.block.SfrMultiblock;
import com.frozendawn.power.FusionPhysics.State;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Looking through the Maintenance Hatch porthole into a burning SFR-1 leaves faint white-blue specks on the retina,
 * drawn by the radiation system's eye flashes. Visual only: the fast-neutron dose is not modelled yet. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PlasmaGlimpse {
    public static final double REACH = 24;
    private static final Set<SfrControllerBlockEntity> BURNING = Collections.newSetFromMap(new WeakHashMap<>());
    private PlasmaGlimpse() {}

    /** Called from each client Controller's ticker with its synced state. */
    public static void track(SfrControllerBlockEntity controller) {
        var state = controller.sound().state();
        if (state == State.BURNING || state == State.RAMP_DOWN) BURNING.add(controller); else BURNING.remove(controller);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); var player = mc.player;
        if (player == null || mc.isPaused()) return;
        BURNING.removeIf(c -> c.isRemoved() || c.getLevel() != player.level());
        RadiationClient.glimpse(!BURNING.isEmpty()
                && PlasmaSight.seesPlasma(player.level(), player.getEyePosition(), player.getViewVector(1), REACH, PlasmaGlimpse::burningChannel));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { BURNING.clear(); }
    private static boolean burningChannel(BlockPos pos) {
        for (var c : BURNING) {
            var center = SfrMultiblock.center(c.getBlockPos(), c.getBlockState().getValue(SfrControllerBlock.FACING));
            if (SfrMultiblock.channel(pos.getX() - center.getX(), pos.getY() - center.getY(), pos.getZ() - center.getZ())) return true;
        }
        return false;
    }
}
