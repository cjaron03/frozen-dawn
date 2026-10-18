package com.frozendawn.gpr;

import java.util.List;
import java.util.Locale;

/**
 * What the Plumb Line calls each ore: a short type (deepslate and frozen variants share one), its target group, the
 * colour it draws in, and a value tier used to rank returns. Shared by the scan, the overlay and the screen's legend.
 */
public final class PlumbLineOres {
    private PlumbLineOres() { }

    public enum Group { METALS, ENERGY, MINERALS }

    /** Chosen on the scanner before a pulse; only matching ore is reported. */
    public enum Target {
        ALL, METALS, ENERGY, MINERALS;
        public boolean matches(Group group) { return this == ALL || name().equals(group.name()); }
        public Target next() { return values()[(ordinal() + 1) % values().length]; }
        public static Target byId(int id) { var all = values(); return id >= 0 && id < all.length ? all[id] : ALL; }
        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Tier 3 ores are reported even as a single hit; higher tiers rank first. */
    public record Ore(String key, Group group, int color, int tier, boolean known) { }

    public static final List<Ore> KNOWN = List.of(
            new Ore("iron", Group.METALS, 0xD9B48F, 1, true),
            new Ore("copper", Group.METALS, 0xE8743B, 1, true),
            new Ore("gold", Group.METALS, 0xFFC935, 2, true),
            new Ore("galena", Group.METALS, 0x8EA8D6, 2, true),
            new Ore("coal", Group.ENERGY, 0x9AA3AB, 0, true),
            new Ore("uranium", Group.ENERGY, 0xB6FF3C, 3, true),
            new Ore("redstone", Group.MINERALS, 0xF0453A, 1, true),
            new Ore("lapis", Group.MINERALS, 0x4D6BFF, 1, true),
            new Ore("sulfur", Group.MINERALS, 0xE6E27A, 1, true),
            new Ore("diamond", Group.MINERALS, 0xA6E6FF, 3, true),
            new Ore("emerald", Group.MINERALS, 0x31C46C, 3, true),
            new Ore("ancient_debris", Group.MINERALS, 0xA0705A, 3, true));
    private static final int UNKNOWN_COLOR = 0xC8C8C8;

    /** The ore a block id belongs to: {@code minecraft:deepslate_iron_ore} and {@code frozendawn:frozen_coal_ore} map to iron and coal. */
    public static Ore of(String blockId) {
        String key = type(blockId);
        for (var ore : KNOWN) if (ore.key.equals(key)) return ore;
        return new Ore(key, Group.MINERALS, UNKNOWN_COLOR, 0, false);
    }

    static String type(String blockId) {
        String path = blockId.substring(blockId.indexOf(':') + 1);
        for (var prefix : new String[] {"deepslate_", "frozen_", "nether_"}) if (path.startsWith(prefix)) path = path.substring(prefix.length());
        return path.endsWith("_ore") ? path.substring(0, path.length() - 4) : path;
    }
}
