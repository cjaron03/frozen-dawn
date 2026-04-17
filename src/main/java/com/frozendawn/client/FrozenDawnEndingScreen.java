package com.frozendawn.client;

import com.frozendawn.network.EndingSequencePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class FrozenDawnEndingScreen extends Screen {
    private static final int BLACK_END_TICKS = 5 * 20;
    private static final int BASE_CREDITS_START_TICKS = 60 * 20;
    private static final int ENDING_COMPLETE_TICKS = 9 * 60 * 20 + 30 * 20;
    private static final int POST_CREDITS_BLACK_FADE_TICKS = 5 * 20;
    private static final int POST_CREDITS_COLONY_TICKS = 6 * 20;
    private static final int POST_CREDITS_MESSAGE_TICKS = 7 * 20;
    private static final int COMPLIANCE_TICKS = 10 * 20;
    private static final int LINE_HEIGHT = 12;
    private static final int MARS_GRID = 14;
    private static final int CREDIT_FAST_FORWARD_TICKS_PER_TICK = 55;

    private final EndingSequencePayload payload;
    private final List<CreditLine> creditLines;
    private final LogoRenderer logoRenderer = new LogoRenderer(false);
    private int ageTicks;
    private Button returnButton;
    private boolean endingMusicFadeStarted;

    public FrozenDawnEndingScreen(EndingSequencePayload payload) {
        super(Component.translatable("screen.frozendawn.ending.title"));
        this.payload = payload;
        this.creditLines = buildCreditLines(payload);
    }

    @Override
    protected void init() {
        if (ageTicks == 0) {
            EndingMusicController.start();
        }
        updateReturnButton();
    }

    @Override
    public void tick() {
        int advance = getTickAdvance();
        ageTicks = isSkippablePhase()
                ? Math.min(getPostCreditsStartTick(), ageTicks + advance)
                : ageTicks + advance;
        if (!endingMusicFadeStarted && ageTicks >= getPostCreditsStartTick()) {
            endingMusicFadeStarted = true;
            int fadeRemaining = Mth.clamp(
                    getPostCreditsStartTick() + POST_CREDITS_BLACK_FADE_TICKS - ageTicks,
                    1,
                    POST_CREDITS_BLACK_FADE_TICKS);
            EndingMusicController.fadeOutAndStop(fadeRemaining);
        }
        EndingMusicController.tick(ageTicks);
        updateReturnButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float tick = ageTicks + partialTick;
        renderSpace(graphics, tick);
        renderMars(graphics, tick);

        int creditsStart = getCreditsStartTick();
        if (ageTicks < creditsStart) {
            // Let the Mars approach breathe before the title roll begins.
        } else if (ageTicks < getPostCreditsStartTick()) {
            renderCredits(graphics, tick, creditsStart);
        } else {
            renderPostCredits(graphics);
        }

        if (isSkippablePhase()) {
            renderSkipHint(graphics);
        }
        if (canReturn()) {
            renderReturnHint(graphics);
        }
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Do nothing. Vanilla Screen.render applies the menu blur pass here, which softens the pixel-art ending.
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_Q && isSkippablePhase()) {
            return true;
        }
        if (canReturn() && (keyCode == 32 || keyCode == 257 || keyCode == 335)) {
            finishAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (canReturn() && button == 0) {
            finishAndClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return canReturn();
    }

    @Override
    public void onClose() {
        if (canReturn()) {
            finishAndClose();
        }
    }

    @Override
    public void removed() {
        EndingMusicController.stop();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private int getCreditsStartTick() {
        return BASE_CREDITS_START_TICKS;
    }

    private int getPostCreditsStartTick() {
        return ENDING_COMPLETE_TICKS - getPostCreditsDurationTicks();
    }

    private int getPostCreditsDurationTicks() {
        return POST_CREDITS_BLACK_FADE_TICKS
                + POST_CREDITS_COLONY_TICKS
                + POST_CREDITS_MESSAGE_TICKS
                + (payload.conspiracyDiscovered() ? COMPLIANCE_TICKS : 0);
    }

    private boolean isSkippablePhase() {
        return ageTicks < getPostCreditsStartTick();
    }

    private boolean canReturn() {
        return ageTicks >= ENDING_COMPLETE_TICKS;
    }

    private int getTickAdvance() {
        if (isSkippablePhase() && isQHeld()) {
            return CREDIT_FAST_FORWARD_TICKS_PER_TICK;
        }
        return 1;
    }

    private boolean isQHeld() {
        return minecraft != null
                && minecraft.getWindow() != null
                && GLFW.glfwGetKey(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_Q) == GLFW.GLFW_PRESS;
    }

    private void updateReturnButton() {
        if (!canReturn() || returnButton != null) {
            return;
        }
        int buttonWidth = Math.min(220, Math.max(150, width / 4));
        returnButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.frozendawn.ending.return"), button -> finishAndClose())
                .bounds((width - buttonWidth) / 2, height - 38, buttonWidth, 20)
                .build());
    }

    private void finishAndClose() {
        EndingMusicController.stop();
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void renderSpace(GuiGraphics graphics, float tick) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        float reveal = Mth.clamp((tick - BLACK_END_TICKS) / 100.0F, 0.0F, 1.0F);
        if (reveal <= 0.0F) {
            return;
        }

        graphics.fillGradient(0, 0, width, height, applyAlpha(0xFF02040A, reveal), applyAlpha(0xFF090D17, reveal));
        graphics.fillGradient(0, 0, width, height / 2, applyAlpha(0x22264668, reveal), 0x00000000);
        for (int i = 0; i < 170; i++) {
            float sx = hash01(i * 71 + 5);
            float sy = hash01(i * 113 + 19);
            int x = Math.round(sx * width);
            int y = Math.round(sy * height);
            int size = i % 17 == 0 ? 2 : 1;
            int alpha = Mth.clamp(Math.round((80 + hash01(i * 31) * 150) * reveal), 0, 255);
            int tint = i % 11 == 0 ? 0xD8F6FF : 0xFFFFFF;
            graphics.fill(x, y, x + size, y + size, (alpha << 24) | tint);
        }
    }

    private void renderMars(GuiGraphics graphics, float tick) {
        float reveal = Mth.clamp((tick - BLACK_END_TICKS) / (float) (BASE_CREDITS_START_TICKS - BLACK_END_TICKS), 0.0F, 1.0F);
        if (reveal <= 0.0F) {
            return;
        }

        float revealPulse = smoothStep(reveal);
        float targetFace = Mth.lerp(revealPulse, 28.0F, Math.max(48.0F, Math.min(width, height) * 0.38F));
        float halfSize = targetFace * 0.5F;
        float yaw = (tick - BASE_CREDITS_START_TICKS) * 0.012F;
        float pitch = 0.16F;
        int centerX = width / 2;
        int centerY = Math.round(height * 0.46F);

        ProjectedFace[] faces = {
                projectMarsFace(0, yaw, pitch, halfSize, centerX, centerY,
                        new MarsVertex(-1.0F, 1.0F, 1.0F), new MarsVertex(1.0F, 1.0F, 1.0F),
                        new MarsVertex(1.0F, -1.0F, 1.0F), new MarsVertex(-1.0F, -1.0F, 1.0F),
                        new MarsVertex(0.0F, 0.0F, 1.0F)),
                projectMarsFace(1, yaw, pitch, halfSize, centerX, centerY,
                        new MarsVertex(1.0F, 1.0F, -1.0F), new MarsVertex(-1.0F, 1.0F, -1.0F),
                        new MarsVertex(-1.0F, -1.0F, -1.0F), new MarsVertex(1.0F, -1.0F, -1.0F),
                        new MarsVertex(0.0F, 0.0F, -1.0F)),
                projectMarsFace(2, yaw, pitch, halfSize, centerX, centerY,
                        new MarsVertex(1.0F, 1.0F, 1.0F), new MarsVertex(1.0F, 1.0F, -1.0F),
                        new MarsVertex(1.0F, -1.0F, -1.0F), new MarsVertex(1.0F, -1.0F, 1.0F),
                        new MarsVertex(1.0F, 0.0F, 0.0F)),
                projectMarsFace(3, yaw, pitch, halfSize, centerX, centerY,
                        new MarsVertex(-1.0F, 1.0F, -1.0F), new MarsVertex(-1.0F, 1.0F, 1.0F),
                        new MarsVertex(-1.0F, -1.0F, 1.0F), new MarsVertex(-1.0F, -1.0F, -1.0F),
                        new MarsVertex(-1.0F, 0.0F, 0.0F)),
                projectMarsFace(4, yaw, pitch, halfSize, centerX, centerY,
                        new MarsVertex(-1.0F, 1.0F, -1.0F), new MarsVertex(1.0F, 1.0F, -1.0F),
                        new MarsVertex(1.0F, 1.0F, 1.0F), new MarsVertex(-1.0F, 1.0F, 1.0F),
                        new MarsVertex(0.0F, 1.0F, 0.0F)),
                projectMarsFace(5, yaw, pitch, halfSize, centerX, centerY,
                        new MarsVertex(-1.0F, -1.0F, 1.0F), new MarsVertex(1.0F, -1.0F, 1.0F),
                        new MarsVertex(1.0F, -1.0F, -1.0F), new MarsVertex(-1.0F, -1.0F, -1.0F),
                        new MarsVertex(0.0F, -1.0F, 0.0F))
        };
        sortMarsFacesBackToFront(faces);

        for (ProjectedFace face : faces) {
            renderMarsFace(graphics, face);
        }
    }

    private ProjectedFace projectMarsFace(int faceId, float yaw, float pitch, float halfSize, int centerX, int centerY,
                                          MarsVertex v0, MarsVertex v1, MarsVertex v2, MarsVertex v3, MarsVertex normal) {
        MarsPoint p0 = projectMarsVertex(v0, yaw, pitch, halfSize, centerX, centerY);
        MarsPoint p1 = projectMarsVertex(v1, yaw, pitch, halfSize, centerX, centerY);
        MarsPoint p2 = projectMarsVertex(v2, yaw, pitch, halfSize, centerX, centerY);
        MarsPoint p3 = projectMarsVertex(v3, yaw, pitch, halfSize, centerX, centerY);
        MarsVertex litNormal = rotateMarsNormal(normal, yaw, pitch);
        float light = Mth.clamp(0.64F + Math.max(0.0F,
                litNormal.x() * -0.25F + litNormal.y() * 0.76F + litNormal.z() * -0.58F) * 0.34F,
                0.48F, 1.0F);
        return new ProjectedFace(faceId, p0, p1, p2, p3, light,
                (p0.depth() + p1.depth() + p2.depth() + p3.depth()) * 0.25F);
    }

    private MarsPoint projectMarsVertex(MarsVertex vertex, float yaw, float pitch, float halfSize, int centerX, int centerY) {
        MarsVertex rotated = rotateMarsNormal(vertex, yaw, pitch);
        float perspective = 4.2F / (4.2F + rotated.z());
        return new MarsPoint(
                centerX + rotated.x() * halfSize * perspective,
                centerY - rotated.y() * halfSize * perspective,
                rotated.z());
    }

    private MarsVertex rotateMarsNormal(MarsVertex vertex, float yaw, float pitch) {
        float cosYaw = Mth.cos(yaw);
        float sinYaw = Mth.sin(yaw);
        float x = vertex.x() * cosYaw + vertex.z() * sinYaw;
        float z = vertex.z() * cosYaw - vertex.x() * sinYaw;

        float cosPitch = Mth.cos(pitch);
        float sinPitch = Mth.sin(pitch);
        float y = vertex.y() * cosPitch - z * sinPitch;
        float pitchedZ = vertex.y() * sinPitch + z * cosPitch;
        return new MarsVertex(x, y, pitchedZ);
    }

    private void sortMarsFacesBackToFront(ProjectedFace[] faces) {
        for (int i = 1; i < faces.length; i++) {
            ProjectedFace face = faces[i];
            int j = i - 1;
            while (j >= 0 && faces[j].depth() < face.depth()) {
                faces[j + 1] = faces[j];
                j--;
            }
            faces[j + 1] = face;
        }
    }

    private void renderMarsFace(GuiGraphics graphics, ProjectedFace face) {
        for (int row = 0; row < MARS_GRID; row++) {
            for (int col = 0; col < MARS_GRID; col++) {
                float u0 = col / (float) MARS_GRID;
                float u1 = (col + 1) / (float) MARS_GRID;
                float v0 = row / (float) MARS_GRID;
                float v1 = (row + 1) / (float) MARS_GRID;
                fillProjectedQuad(graphics,
                        interpolateFace(face, u0, v0),
                        interpolateFace(face, u1, v0),
                        interpolateFace(face, u1, v1),
                        interpolateFace(face, u0, v1),
                        marsFaceCellColor(face.faceId(), row, col, face.light()));
            }
        }
        drawProjectedLine(graphics, face.p0(), face.p1(), 0xAAFFD39A);
        drawProjectedLine(graphics, face.p1(), face.p2(), 0x992B0E0B);
        drawProjectedLine(graphics, face.p2(), face.p3(), 0x992B0E0B);
        drawProjectedLine(graphics, face.p3(), face.p0(), 0xAA2B0E0B);
    }

    private MarsPoint interpolateFace(ProjectedFace face, float u, float v) {
        float topX = Mth.lerp(u, face.p0().x(), face.p1().x());
        float topY = Mth.lerp(u, face.p0().y(), face.p1().y());
        float topDepth = Mth.lerp(u, face.p0().depth(), face.p1().depth());
        float bottomX = Mth.lerp(u, face.p3().x(), face.p2().x());
        float bottomY = Mth.lerp(u, face.p3().y(), face.p2().y());
        float bottomDepth = Mth.lerp(u, face.p3().depth(), face.p2().depth());
        return new MarsPoint(
                Mth.lerp(v, topX, bottomX),
                Mth.lerp(v, topY, bottomY),
                Mth.lerp(v, topDepth, bottomDepth));
    }

    private void fillProjectedQuad(GuiGraphics graphics, MarsPoint p0, MarsPoint p1, MarsPoint p2, MarsPoint p3, int color) {
        float minY = Math.min(Math.min(p0.y(), p1.y()), Math.min(p2.y(), p3.y()));
        float maxY = Math.max(Math.max(p0.y(), p1.y()), Math.max(p2.y(), p3.y()));
        int startY = Math.max(0, Mth.floor(minY));
        int endY = Math.min(height, Mth.ceil(maxY));
        for (int y = startY; y <= endY; y++) {
            float sampleY = y + 0.5F;
            float[] xs = new float[4];
            int count = 0;
            count = collectEdgeIntersection(p0, p1, sampleY, xs, count);
            count = collectEdgeIntersection(p1, p2, sampleY, xs, count);
            count = collectEdgeIntersection(p2, p3, sampleY, xs, count);
            count = collectEdgeIntersection(p3, p0, sampleY, xs, count);
            if (count < 2) {
                continue;
            }
            sortSmall(xs, count);
            int startX = Math.max(0, Mth.floor(xs[0]));
            int endX = Math.min(width, Mth.ceil(xs[count - 1]));
            if (endX > startX) {
                graphics.fill(startX, y, endX, y + 1, color);
            }
        }
    }

    private int collectEdgeIntersection(MarsPoint a, MarsPoint b, float y, float[] xs, int count) {
        float minY = Math.min(a.y(), b.y());
        float maxY = Math.max(a.y(), b.y());
        if (maxY - minY < 0.001F || y < minY || y >= maxY || count >= xs.length) {
            return count;
        }
        float t = (y - a.y()) / (b.y() - a.y());
        xs[count] = Mth.lerp(t, a.x(), b.x());
        return count + 1;
    }

    private void sortSmall(float[] values, int count) {
        for (int i = 1; i < count; i++) {
            float value = values[i];
            int j = i - 1;
            while (j >= 0 && values[j] > value) {
                values[j + 1] = values[j];
                j--;
            }
            values[j + 1] = value;
        }
    }

    private void drawProjectedLine(GuiGraphics graphics, MarsPoint from, MarsPoint to, int color) {
        int steps = Math.max(1, Mth.ceil(Math.max(Math.abs(to.x() - from.x()), Math.abs(to.y() - from.y()))));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(Mth.lerp(t, from.x(), to.x()));
            int y = Math.round(Mth.lerp(t, from.y(), to.y()));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private int marsFaceCellColor(int faceId, int row, int col, float light) {
        if (faceId == 4) {
            return shadeColor(marsTopCellColor(row, col), light);
        }
        if (faceId == 5) {
            return shadeColor(marsSideCellColor(row, col, faceId), light * 0.72F);
        }
        return shadeColor(marsSurfaceCellColor(row, col, faceId), light);
    }

    private int marsSurfaceCellColor(int row, int col, int faceId) {
        float noise = hash01(row * 97 + col * 41 + faceId * 131 + 17);
        if (row <= 1 && (col <= 3 || col >= 10) && noise > 0.18F) {
            return noise > 0.68F ? 0xFFEAF8FF : 0xFFD5EFFA;
        }
        if (row == 2 && (col == 2 || col == 11) && noise > 0.3F) {
            return 0xFFD5EFFA;
        }
        if (noise > 0.83F) {
            return 0xFFD66B3C;
        }
        if (noise < 0.16F) {
            return 0xFF641F1B;
        }
        if ((col + row) % 5 == 0) {
            return 0xFF9E3624;
        }
        return ((col + faceId) % 11) > 7 ? 0xFF7E2A20 : 0xFFB2472A;
    }

    private int marsTopCellColor(int row, int col) {
        if (row <= 2 && (col <= 4 || col >= 9)) {
            return hash01(row * 83 + col * 19) > 0.34F ? 0xFFE8F6FF : 0xFFD3EFF9;
        }
        return switch ((row + col) % 4) {
            case 0 -> 0xFFC96B3D;
            case 1 -> 0xFFB14A2D;
            case 2 -> 0xFFE08A54;
            default -> 0xFF8E3224;
        };
    }

    private int marsSideCellColor(int row, int col, int faceId) {
        return switch ((row * 2 + col + faceId) % 5) {
            case 0 -> 0xFF5C201A;
            case 1 -> 0xFF77271E;
            case 2 -> 0xFF8E3325;
            case 3 -> 0xFF6A251D;
            default -> 0xFF4B1916;
        };
    }

    private int shadeColor(int color, float light) {
        float shade = Mth.clamp(light, 0.25F, 1.08F);
        int r = Mth.clamp(Math.round(((color >> 16) & 0xFF) * shade), 0, 255);
        int g = Mth.clamp(Math.round(((color >> 8) & 0xFF) * shade), 0, 255);
        int b = Mth.clamp(Math.round((color & 0xFF) * shade), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void renderCredits(GuiGraphics graphics, float tick, int creditsStart) {
        int creditsEnd = Math.max(creditsStart + 1, getPostCreditsStartTick() - 50);
        float progress = Mth.clamp((tick - creditsStart) / (float) (creditsEnd - creditsStart), 0.0F, 1.0F);
        int totalHeight = totalCreditHeight();
        float travel = height + totalHeight + 120.0F;
        int y = Math.round(height + 62.0F - progress * travel);

        for (CreditLine line : creditLines) {
            if (y + line.height() >= -140 && y <= height + 40) {
                line.render(graphics, font, logoRenderer, width, y);
            }
            y += line.height();
        }
    }

    private void renderSkipHint(GuiGraphics graphics) {
        Component hint = Component.translatable("screen.frozendawn.ending.skip_hint");
        int x = width - font.width(hint) - 12;
        int y = height - 18;
        graphics.fill(x - 7, y - 5, width - 7, y + 13, 0x88061014);
        graphics.drawString(font, hint, x, y, 0xFFB8EFFF, false);
    }

    private void renderPostCredits(GuiGraphics graphics) {
        int local = ageTicks - getPostCreditsStartTick();
        if (local < POST_CREDITS_BLACK_FADE_TICKS) {
            int alpha = Mth.clamp(Math.round(local / (float) POST_CREDITS_BLACK_FADE_TICKS * 255.0F), 0, 255);
            graphics.fill(0, 0, width, height, alpha << 24);
            return;
        }

        graphics.fill(0, 0, width, height, 0xFF000000);
        local -= POST_CREDITS_BLACK_FADE_TICKS;

        if (local < POST_CREDITS_COLONY_TICKS) {
            float alpha = pulseAlpha(local, 28.0F, POST_CREDITS_COLONY_TICKS - 45.0F);
            drawEndingTerminalPanel(graphics,
                    Component.translatable("screen.frozendawn.ending.terminal.arrival"),
                    Component.translatable("screen.frozendawn.ending.mars_colony"),
                    null,
                    0xFFA7FFEA,
                    0xFFD9FFF6,
                    0xCC44E6A8,
                    alpha);
            return;
        }

        int messageStart = POST_CREDITS_COLONY_TICKS;
        int messageEnd = messageStart + POST_CREDITS_MESSAGE_TICKS;
        if (local < messageEnd) {
            float alpha = pulseAlpha(local - messageStart, 28.0F, POST_CREDITS_MESSAGE_TICKS - 35.0F);
            drawEndingTerminalPanel(graphics,
                    Component.translatable("screen.frozendawn.ending.terminal.signal"),
                    payload.conspiracyDiscovered()
                            ? Component.translatable("screen.frozendawn.ending.truth")
                            : Component.translatable("screen.frozendawn.ending.welcome"),
                    null,
                    payload.conspiracyDiscovered() ? 0xFFFFF0B0 : 0xFFA7FFEA,
                    payload.conspiracyDiscovered() ? 0xFFFFF5C2 : 0xFFD9FFF6,
                    payload.conspiracyDiscovered() ? 0xCCFFB84A : 0xCC44E6A8,
                    alpha);
            return;
        }

        if (payload.conspiracyDiscovered() && payload.showComplianceLine()) {
            float alpha = pulseAlpha(local - messageEnd, 28.0F, COMPLIANCE_TICKS - 45.0F);
            drawEndingTerminalPanel(graphics,
                    Component.translatable("screen.frozendawn.ending.terminal.access"),
                    Component.translatable("screen.frozendawn.ending.compliance"),
                    null,
                    0xFFFF8B8B,
                    0xFFFFC0C0,
                    0xCCFF5C5C,
                    alpha);
        }
    }

    private void renderReturnHint(GuiGraphics graphics) {
        Component hint = Component.translatable("screen.frozendawn.ending.return_hint");
        graphics.drawCenteredString(font, hint, width / 2, height - 58, 0xFFB8EFFF);
    }

    private void drawEndingTerminalPanel(GuiGraphics graphics, Component title, Component body, Component secondary,
                                         int titleColor, int bodyColor, int accentColor, float alpha) {
        if (alpha <= 0.04F) {
            return;
        }
        int panelW = Math.min(520, width - 32);
        int panelH = secondary == null ? 64 : 82;
        int panelX = (width - panelW) / 2;
        int panelY = height / 2 - panelH / 2;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, applyAlpha(0xA008120F, alpha));
        graphics.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + panelH - 2,
                applyAlpha(0x7A10231D, alpha));
        graphics.fill(panelX + 6, panelY + 6, panelX + 8, panelY + panelH - 6, applyAlpha(accentColor, alpha));
        graphics.fill(panelX + 14, panelY + 8, panelX + panelW - 10, panelY + 9, applyAlpha(0x5526F6B0, alpha));

        int scanRange = Math.max(1, panelH - 18);
        int scanY = panelY + 11 + Math.floorMod(ageTicks, scanRange);
        graphics.fill(panelX + 14, scanY, panelX + panelW - 12, scanY + 1, applyAlpha(0x3344E6A8, alpha));

        graphics.drawString(font, Component.literal("[ORSA] ").append(title),
                panelX + 16, panelY + 8, applyAlpha(titleColor, alpha), false);
        graphics.drawWordWrap(font, body, panelX + 16, panelY + 28, panelW - 32, applyAlpha(bodyColor, alpha));
        if (secondary != null) {
            graphics.drawWordWrap(font, secondary, panelX + 16, panelY + 50, panelW - 32, applyAlpha(bodyColor, alpha));
        }
    }

    private float pulseAlpha(float localTicks, float fadeInTicks, float fadeOutStartTicks) {
        float in = Mth.clamp(localTicks / fadeInTicks, 0.0F, 1.0F);
        float out = localTicks <= fadeOutStartTicks ? 1.0F : 1.0F - Mth.clamp((localTicks - fadeOutStartTicks) / 35.0F, 0.0F, 1.0F);
        return in * out;
    }

    private int totalCreditHeight() {
        int total = 0;
        for (CreditLine line : creditLines) {
            total += line.height();
        }
        return total;
    }

    private static List<CreditLine> buildCreditLines(EndingSequencePayload payload) {
        List<CreditLine> lines = new ArrayList<>();
        lines.add(CreditLine.logo(112));
        lines.add(CreditLine.blank(42));
        lines.add(CreditLine.center(Component.translatable("screen.frozendawn.ending.created_by"), 0xFFB8EFFF, 1.0F, 18));
        lines.add(CreditLine.center(Component.translatable("screen.frozendawn.ending.creator"), 0xFFFFFFFF, 1.28F, 30));
        lines.add(CreditLine.blank(36));
        lines.add(CreditLine.section(Component.translatable("screen.frozendawn.ending.section_stats")));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.days", payload.daysSurvived())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.terminals", payload.terminalsHacked())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.mobs", payload.mobsKilled())));
        lines.add(CreditLine.blank(56));
        return lines;
    }

    private static int applyAlpha(int color, float alphaScale) {
        int alpha = Mth.clamp(Math.round(((color >>> 24) & 0xFF) * alphaScale), 0, 255);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static float hash01(int seed) {
        int x = seed * 1664525 + 1013904223;
        x ^= x >>> 16;
        return (x & 0x00FFFFFF) / (float) 0x01000000;
    }

    private static float smoothStep(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record MarsVertex(float x, float y, float z) {
    }

    private record MarsPoint(float x, float y, float depth) {
    }

    private record ProjectedFace(
            int faceId,
            MarsPoint p0,
            MarsPoint p1,
            MarsPoint p2,
            MarsPoint p3,
            float light,
            float depth) {
    }

    private record CreditLine(Component text, boolean centered, boolean logo, int color, float scale, int height) {
        static CreditLine logo(int height) {
            return new CreditLine(Component.empty(), true, true, 0xFFFFFFFF, 1.0F, height);
        }

        static CreditLine blank(int height) {
            return new CreditLine(Component.empty(), true, false, 0xFFFFFFFF, 1.0F, height);
        }

        static CreditLine section(Component text) {
            return center(text.copy().withStyle(ChatFormatting.YELLOW), 0xFFFFF5C2, 1.0F, 16);
        }

        static CreditLine center(Component text, int color, float scale, int height) {
            return new CreditLine(text, true, false, color, scale, height);
        }

        static CreditLine left(Component text) {
            return new CreditLine(text, false, false, 0xFFE7F5FF, 1.0F, LINE_HEIGHT);
        }

        void render(GuiGraphics graphics, net.minecraft.client.gui.Font font, LogoRenderer logoRenderer, int screenWidth, int y) {
            if (logo) {
                logoRenderer.renderLogo(graphics, screenWidth, 1.0F, y);
                return;
            }
            if (text.getString().isEmpty()) {
                return;
            }
            graphics.pose().pushPose();
            int x = centered ? Math.round(screenWidth / 2.0F - font.width(text) * scale / 2.0F) : screenWidth / 2 - 128;
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, text, 0, 0, color, false);
            graphics.pose().popPose();
        }
    }
}
