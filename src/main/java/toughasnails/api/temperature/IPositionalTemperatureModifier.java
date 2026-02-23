/*
 * Compile-only stub mirroring Tough As Nails API.
 * Excluded from the built jar — at runtime, TaN provides the real class.
 */
package toughasnails.api.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface IPositionalTemperatureModifier {
    TemperatureLevel modify(Level level, BlockPos pos, TemperatureLevel current);
}
