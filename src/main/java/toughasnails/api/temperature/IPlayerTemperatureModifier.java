/*
 * Compile-only stub mirroring Tough As Nails API.
 * Excluded from the built jar — at runtime, TaN provides the real class.
 */
package toughasnails.api.temperature;

import net.minecraft.world.entity.player.Player;

public interface IPlayerTemperatureModifier {
    TemperatureLevel modify(Player player, TemperatureLevel current);
}
