/*
 * Compile-only stub mirroring Tough As Nails API.
 * Excluded from the built jar — at runtime, TaN provides the real class.
 * Only the registration methods we call are included.
 */
package toughasnails.api.temperature;

public class TemperatureHelper {
    public static void registerPositionalTemperatureModifier(IPositionalTemperatureModifier modifier) {}
    public static void registerPlayerTemperatureModifier(IPlayerTemperatureModifier modifier) {}
    public static void registerProximityBlockModifier(IProximityBlockModifier modifier) {}
}
