package com.frozendawn.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Chalk on the Scribe's slate: the subject's face sketched from their own skin, and a pictogram for what each line
 * is about. Neither says what was concluded or how surely; that stays in Thaeven.
 */
final class ScribeChalk {
    private static final int CHALK = 0xE6E4DC;
    private static final int[] SHADES = {0x28, 0x70, 0xB0, 0xF0};
    private static final Map<ResourceLocation, int[]> FACES = new HashMap<>();

    private ScribeChalk() { }

    /** The subject's 8x8 face (hat layer over it) in four chalk densities, drawn at the given pixel size. */
    static void portrait(GuiGraphics graphics, Optional<UUID> subject, int x, int y, int pixel) {
        if (subject.isEmpty()) return;
        int[] face = FACES.computeIfAbsent(skin(subject.get()), ScribeChalk::sketch);
        if (face == null) return;
        for (int i = 0; i < 64; i++) {
            int px = x + (i & 7) * pixel, py = y + (i >> 3) * pixel;
            graphics.fill(px, py, px + pixel, py + pixel, face[i] << 24 | CHALK);
        }
    }

    /** The subject's loaded skin while they are known to this client, else the default skin their UUID picks. */
    private static ResourceLocation skin(UUID subject) {
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(subject);
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(subject).texture();
    }

    private static int[] sketch(ResourceLocation location) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        RenderSystem.bindTexture(texture.getId());
        int width = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (width < 64 || height < 32) return null;
        int scale = width / 64;
        int[] face = new int[64];
        try (var image = new NativeImage(width, height, false)) {
            image.downloadTexture(0, false);
            for (int i = 0; i < 64; i++) {
                int u = (i & 7) * scale, v = (8 + (i >> 3)) * scale;
                int hat = image.getPixelRGBA((40 * scale) + u, v), skin = image.getPixelRGBA(8 * scale + u, v);
                int abgr = (hat >>> 24) > 0x80 ? hat : skin;
                double light = (0.2126 * (abgr & 0xFF) + 0.7152 * (abgr >> 8 & 0xFF) + 0.0722 * (abgr >> 16 & 0xFF)) / 255.0;
                face[i] = SHADES[Math.min(3, (int) (light * 4.4))];
            }
        }
        return face;
    }

    /** A small chalk pictogram for the line's subject, or none for a pattern it has no picture for. */
    static int pictogram(GuiGraphics graphics, String pattern, int x, int y) {
        List<String> rows = switch (pattern) {
            case "PLAYER_PREFERS_SWORD" -> SWORD;
            case "PLAYER_PREFERS_RANGED" -> BOW;
            case "PLAYER_USES_RECOVERY_UNDER_COVER" -> HEART;
            case "PLAYER_PURSUES_WITHDRAWING_ARCHITECT" -> PURSUIT;
            default -> exit(pattern);
        };
        if (rows == null) return 0;
        int top = y + (8 - rows.size()) / 2, width = 0;
        for (int row = 0; row < rows.size(); row++) {
            String line = rows.get(row);
            width = Math.max(width, line.length());
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != '#') continue;
                int alpha = 0xC8 + Math.floorMod((col * 31 + row * 17 + pattern.length()) * 0x9E37, 0x38);
                graphics.fill(x + col, top + row, x + col + 1, top + row + 1, alpha << 24 | CHALK);
            }
        }
        return width;
    }

    /** A doorway with an arrow the way the subject leaves: "RETREAT_BEARING_E", or the last bearing of "EXIT_AFTER_W_E_...". */
    private static List<String> exit(String pattern) {
        String bearing = pattern.startsWith("RETREAT_BEARING_") ? pattern.substring(16)
                : pattern.startsWith("EXIT_AFTER_") && pattern.length() > 13 ? pattern.substring(13, 14) : "";
        List<String> arrow = switch (bearing) {
            case "N" -> List.of("..#..", ".###.", "#.#.#", "..#..", "..#..");
            case "S" -> List.of("..#..", "..#..", "#.#.#", ".###.", "..#..");
            case "E" -> List.of("..#..", "...#.", "#####", "...#.", "..#..");
            case "W" -> List.of("..#..", ".#...", "#####", ".#...", "..#..");
            default -> null;
        };
        if (arrow == null) return null;
        return List.of("#####.......", "#...#.." + arrow.get(0), "#...#.." + arrow.get(1), "#...#.." + arrow.get(2),
                "#...#.." + arrow.get(3), "#...#.." + arrow.get(4), "#...#.......");
    }

    private static final List<String> SWORD = List.of(
            "......##", ".....###", "....###.", "#..###..", ".####...", "..##....", ".#.##...", "#.......");
    private static final List<String> BOW = List.of(
            "##.....", "#.#....", "#..#..#", "#######", "#..#..#", "#.#....", "##.....");
    private static final List<String> HEART = List.of(
            ".##.##.", "#######", "#######", ".#####.", "..###..", "...#...");
    private static final List<String> PURSUIT = List.of(
            "#...#...", ".#...#..", "..#...#.", ".#...#..", "#...#...");
}
