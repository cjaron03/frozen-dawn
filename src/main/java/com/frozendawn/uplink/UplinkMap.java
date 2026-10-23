package com.frozendawn.uplink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A relay telemetry plot, not a survey: a locked vanilla map with no terrain, a dashed search circle and the
 * console's position. Nothing is loaded or read to draw it, and its drawing never updates after printing; vanilla
 * still shows the holder's own position arrow (and an edge marker when off the plot), since locking stops only
 * terrain updates.
 */
public final class UplinkMap {
    private static final int COLOR = 0x2DD4D2;
    private UplinkMap() { }

    public static ItemStack create(ServerLevel level, BlockPos home, BlockPos circle, int radius, String nameKey, List<Component> lore) {
        int centerX = (home.getX() + circle.getX()) >> 1, centerZ = (home.getZ() + circle.getZ()) >> 1;
        int reach = Math.max(Math.abs(circle.getX() - centerX), Math.abs(circle.getZ() - centerZ)) + radius;
        reach = Math.max(reach, Math.max(Math.abs(home.getX() - centerX), Math.abs(home.getZ() - centerZ)));
        int scale = UplinkPolicy.mapScale(reach);
        var tag = new CompoundTag();
        tag.putString("dimension", level.dimension().location().toString());
        tag.putInt("xCenter", centerX); tag.putInt("zCenter", centerZ); tag.putByte("scale", (byte) scale);
        tag.putBoolean("trackingPosition", true); tag.putBoolean("unlimitedTracking", true); tag.putBoolean("locked", true);
        tag.putByteArray("colors", ring(centerX, centerZ, scale, circle, radius));
        tag.put("banners", new ListTag()); // vanilla load parses this unconditionally and warns when it is absent
        var id = level.getFreeMapId();
        level.setMapData(id, MapItemSavedData.load(tag, level.registryAccess()));
        var stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        var marks = new LinkedHashMap<String, MapDecorations.Entry>();
        marks.put("frozendawn_uplink_console", new MapDecorations.Entry(MapDecorationTypes.TARGET_POINT, home.getX() + .5, home.getZ() + .5, 180));
        stack.set(DataComponents.MAP_DECORATIONS, new MapDecorations(marks));
        stack.set(DataComponents.MAP_COLOR, new MapItemColor(COLOR));
        stack.set(DataComponents.ITEM_NAME, Component.translatable(nameKey));
        var lines = new ArrayList<Component>();
        for (var line : lore) lines.add(line.copy().withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    /** Dashed one-pixel circle; pixels outside the 128x128 plot are dropped. */
    static byte[] ring(int centerX, int centerZ, int scale, BlockPos circle, int radius) {
        byte[] colors = new byte[128 * 128];
        byte ink = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.HIGH);
        double perPixel = 1 << scale, cx = 64 + (circle.getX() - centerX) / perPixel, cz = 64 + (circle.getZ() - centerZ) / perPixel;
        double r = Math.max(2, radius / perPixel);
        int steps = (int) Math.max(48, Math.ceil(2 * Math.PI * r * 2));
        for (int i = 0; i < steps; i++) {
            if ((i * 12 / steps) % 2 == 1 && (i * 24 / steps) % 2 == 1) continue; // short gaps make it read as a search area
            double a = 2 * Math.PI * i / steps;
            int px = (int) Math.floor(cx + Math.cos(a) * r), pz = (int) Math.floor(cz + Math.sin(a) * r);
            if (px >= 0 && px < 128 && pz >= 0 && pz < 128) colors[px + pz * 128] = ink;
        }
        return colors;
    }
}
