/*
 * Compile-only stub mirroring Tough As Nails API.
 * Excluded from the built jar — at runtime, TaN provides the real class.
 * Source: https://github.com/Glitchfiend/ToughAsNails (Copyright Glitchfiend Team)
 */
package toughasnails.api.temperature;

public enum TemperatureLevel {
    ICY, COLD, NEUTRAL, WARM, HOT;

    public TemperatureLevel increment(int amount) {
        int clamped = Math.min(Math.max(this.ordinal() + amount, 0), values().length - 1);
        return values()[clamped];
    }

    public TemperatureLevel decrement(int amount) {
        return increment(-amount);
    }
}
