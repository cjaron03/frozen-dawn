package com.frozendawn.world;

import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.thermal.RoomHeatMath;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Connected, loaded-only passive sources. Powers and supply temperatures are first-pass game values. */
public final class RoomHeatSources {
    private RoomHeatSources() {}
    public record Source(BlockPos pos, double power, double temperature) {
        public Source share(long count) { return new Source(pos,power/Math.max(1,count),temperature); }
        public double grant(double capacity,double current,double seconds,boolean airPresent) {
            double efficiency=airPresent?1:RoomHeatMath.VACUUM_HEATER_EFFICIENCY;
            return Math.max(0,Math.min(power*seconds*efficiency,capacity*(temperature-current)));
        }
    }
    public static Optional<Source> at(ServerLevel level,BlockPos pos) {
        if(!level.isLoaded(pos))return Optional.empty();
        double multiplier=FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.get();
        if(level.getBlockEntity(pos) instanceof GeothermalCoreBlockEntity core) {
            double temperature=GeothermalCoreBlockEntity.applySurfaceWarmthPenalty(core.getEffectiveTemp(),pos);
            // Base Core:450 units/s underground,225 at the surface; temperature upgrades increase transfer.
            // A passive Core supplies heat only below its supply temperature; it never refrigerates the room.
            double power=RoomThermalManager.BASE_HEATER_POWER*.25*temperature/GeothermalCoreBlockEntity.BASE_TEMP*multiplier;
            return power>0?Optional.of(new Source(pos.immutable(),power,temperature)):Optional.empty();
        }
        double signature=TemperatureManager.getAmbientSignatureHeat(level.getBlockState(pos));
        double power=RoomThermalManager.BASE_HEATER_POWER*.125*signature/35*multiplier;
        return power>0?Optional.of(new Source(pos.immutable(),power,20+signature)):Optional.empty();
    }
    public static List<Source> connected(ServerLevel level,Set<BlockPos> cells,Set<RoomAtmosphere.BoundaryFace> faces) {
        var positions=new TreeSet<BlockPos>(Comparator.comparingLong(BlockPos::asLong));
        positions.addAll(cells);for(var face:faces)positions.add(face.wallCell());
        var result=new ArrayList<Source>();for(var pos:positions)at(level,pos).ifPresent(result::add);
        return List.copyOf(result);
    }
}
