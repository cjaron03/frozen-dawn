package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class MonitoringStationCalendar {

    private static final ResourceLocation CALENDAR_ART_ID =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "map_art/monitoring_station_calendar.png");

    private static volatile byte[] cachedCalendarColors;

    private MonitoringStationCalendar() {
    }

    public static ItemStack create(ServerLevel level, BlockPos center) {
        MapItemSavedData mapData = MapItemSavedData.createFresh(
                center.getX(),
                center.getZ(),
                (byte) 0,
                false,
                false,
                level.dimension()
        ).locked();
        System.arraycopy(getCalendarColors(level), 0, mapData.colors, 0, mapData.colors.length);
        mapData.setDirty();

        MapId mapId = level.getFreeMapId();
        level.setMapData(mapId, mapData);

        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, mapId);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Station Calendar"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("The X marks stop on September 19th.")
        )));
        return stack;
    }

    private static byte[] getCalendarColors(ServerLevel level) {
        byte[] cached = cachedCalendarColors;
        if (cached != null) {
            return cached;
        }

        synchronized (MonitoringStationCalendar.class) {
            if (cachedCalendarColors != null) {
                return cachedCalendarColors;
            }

            try {
                Resource resource = level.getServer().getResourceManager()
                        .getResource(CALENDAR_ART_ID)
                        .orElseThrow(() -> new IOException("Missing calendar art: " + CALENDAR_ART_ID));
                try (InputStream stream = resource.open()) {
                    BufferedImage image = ImageIO.read(stream);
                    if (image == null) {
                        throw new IOException("Unreadable calendar art: " + CALENDAR_ART_ID);
                    }

                    cachedCalendarColors = convertImageToMapColors(image);
                    return cachedCalendarColors;
                }
            } catch (IOException exception) {
                FrozenDawn.LOGGER.error("Failed to load monitoring station calendar art {}", CALENDAR_ART_ID, exception);
                cachedCalendarColors = createFallbackColors();
                return cachedCalendarColors;
            }
        }
    }

    private static byte[] convertImageToMapColors(BufferedImage image) {
        if (image.getWidth() != 128 || image.getHeight() != 128) {
            throw new IllegalArgumentException(
                    "Calendar art must be 128x128 pixels, got " + image.getWidth() + "x" + image.getHeight()
            );
        }

        int[] palette = buildPalette();
        byte[] colors = new byte[128 * 128];
        byte paper = MapColor.QUARTZ.getPackedId(MapColor.Brightness.NORMAL);

        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha < 16) {
                    colors[x + y * 128] = paper;
                    continue;
                }

                int red = argb >> 16 & 0xFF;
                int green = argb >> 8 & 0xFF;
                int blue = argb & 0xFF;
                colors[x + y * 128] = nearestPackedColor(red, green, blue, palette);
            }
        }

        return colors;
    }

    private static int[] buildPalette() {
        int[] palette = new int[256];
        for (int packed = 0; packed < palette.length; packed++) {
            palette[packed] = MapColor.getColorFromPackedId(packed) & 0x00FFFFFF;
        }
        return palette;
    }

    private static byte nearestPackedColor(int red, int green, int blue, int[] palette) {
        int bestPacked = MapColor.QUARTZ.getPackedId(MapColor.Brightness.NORMAL) & 0xFF;
        int bestDistance = Integer.MAX_VALUE;

        for (int packed = 4; packed < palette.length; packed++) {
            int color = palette[packed];
            int pr = color >> 16 & 0xFF;
            int pg = color >> 8 & 0xFF;
            int pb = color & 0xFF;

            int dr = red - pr;
            int dg = green - pg;
            int db = blue - pb;
            int distance = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPacked = packed;
            }
        }

        return (byte) bestPacked;
    }

    private static byte[] createFallbackColors() {
        byte[] colors = new byte[128 * 128];
        byte paper = MapColor.QUARTZ.getPackedId(MapColor.Brightness.NORMAL);
        for (int i = 0; i < colors.length; i++) {
            colors[i] = paper;
        }
        return colors;
    }
}
