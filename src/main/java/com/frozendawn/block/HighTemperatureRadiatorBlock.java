package com.frozendawn.block;

import com.frozendawn.power.PowerNetworks;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** SFR-1 heat rejection. Links, sinks, glows and burns like a reactor radiator, but only an SFR-1 sinks through it. */
public final class HighTemperatureRadiatorBlock extends ReactorRadiatorBlock {
    public HighTemperatureRadiatorBlock(Properties p) { super(p); }

    /** Every linked High-Temperature Radiator, buried or not, sorted and without duplicates. */
    public static List<BlockPos> linked(ServerLevel l, List<BlockPos> coolantPorts) {
        var linked = new TreeSet<BlockPos>();
        for (var port : coolantPorts) linked.addAll(PowerNetworks.coolantRadiators(l, port));
        return linked.stream().filter(p -> l.getBlockState(p).getBlock() instanceof HighTemperatureRadiatorBlock).toList();
    }
    /** Linked High-Temperature Radiators with an open outdoor face. */
    public static List<BlockPos> sinks(ServerLevel l, List<BlockPos> coolantPorts) {
        return linked(l, coolantPorts).stream().filter(p -> sink(l, p).isPresent()).toList();
    }
}
