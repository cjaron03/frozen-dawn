package com.frozendawn.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.text2speech.Narrator;
import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.EndingSequencePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FrozenDawnEndingScreen extends Screen {
    private static final int BLACK_END_TICKS = 5 * 20;
    private static final int BASE_CREDITS_START_TICKS = 60 * 20;
    private static final int ENDING_COMPLETE_TICKS = 9 * 60 * 20 + 30 * 20;
    private static final int POST_CREDITS_BLACK_FADE_TICKS = 5 * 20;
    private static final int POST_CREDITS_COLONY_TICKS = 6 * 20;
    private static final int POST_CREDITS_MESSAGE_TICKS = 7 * 20;
    private static final int COMPLIANCE_STATIC_TICKS = 34;
    private static final int COMPLIANCE_TTS_DELAY_TICKS = 26;
    private static final int COMPLIANCE_TICKS = 16 * 20;
    private static final int LINE_HEIGHT = 12;
    private static final int MARS_TEXTURE_SUBDIVISIONS = 16;
    private static final int MARS_FALLBACK_TEXTURE_GRID = 64;
    private static final float MARS_YAW_OFFSET = 1.10F;
    private static final float MARS_PITCH_RADIANS = -0.24F;
    private static final float MARS_ROLL_RADIANS = -0.22F;
    private static final int CREDIT_FAST_FORWARD_TICKS_PER_TICK = 55;
    private static final String[] MARS_FACE_TEXTURES = {
            "mars_front",
            "mars_back",
            "mars_right",
            "mars_left",
            "mars_top",
            "mars_bottom"
    };

    private final EndingSequencePayload payload;
    private final List<CreditLine> creditLines;
    private final LogoRenderer logoRenderer = new LogoRenderer(false);
    private int ageTicks;
    private Button returnButton;
    private boolean endingMusicFadeStarted;
    private boolean complianceStaticPlayed;
    private boolean complianceNarrationPlayed;
    private MarsFaceTexture[] marsTextures;

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
        updateComplianceAudioCue();
        EndingMusicController.tick(ageTicks);
        updateReturnButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float tick = ageTicks + partialTick;
        renderSpace(graphics, tick);
        renderMars(graphics, tick);
        renderApproachViewport(graphics, tick);

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

    private int getComplianceStartTick() {
        return getPostCreditsStartTick()
                + POST_CREDITS_BLACK_FADE_TICKS
                + POST_CREDITS_COLONY_TICKS
                + POST_CREDITS_MESSAGE_TICKS;
    }

    private void updateComplianceAudioCue() {
        if (!payload.conspiracyDiscovered() || !payload.showComplianceLine()) {
            return;
        }

        int complianceTick = ageTicks - getComplianceStartTick();
        if (complianceTick < 0) {
            return;
        }

        if (!complianceStaticPlayed) {
            complianceStaticPlayed = true;
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.RADIO_STATIC_HEAVY.get(),
                        0.68F, 0.92F));
            }
        }

        if (!complianceNarrationPlayed && complianceTick >= COMPLIANCE_TTS_DELAY_TICKS) {
            complianceNarrationPlayed = true;
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(),
                        0.55F, 0.45F));
            }
            Narrator narrator = Narrator.getNarrator();
            if (narrator.active()) {
                narrator.clear();
                narrator.say(
                        "ORSA automated compliance. Civilian number thirty nine one zero two. "
                                + "Unauthorized document access detected. Landing clearance suspended. "
                                + "Remain seated. Escort en route.",
                        true);
            }
        }
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
        ensureMarsTextures();

        float revealPulse = smoothStep(reveal);
        float targetFace = Mth.lerp(revealPulse, 28.0F, Math.max(48.0F, Math.min(width, height) * 0.38F));
        float halfSize = targetFace * 0.5F;
        float yaw = MARS_YAW_OFFSET + (tick - BLACK_END_TICKS) * 0.012F;
        float marsAlpha = smoothStep(Mth.clamp((tick - BLACK_END_TICKS) / 80.0F, 0.0F, 1.0F));
        float centerX = width / 2.0F + Mth.lerp(revealPulse, width * -0.10F, width * 0.04F);
        float centerY = height * 0.46F + Mth.lerp(revealPulse, height * -0.05F, 0.0F);
        renderMarsBacklight(graphics, Math.round(centerX), Math.round(centerY), targetFace, revealPulse * marsAlpha);

        ProjectedFace[] faces = {
                projectMarsFace(0, yaw, halfSize, centerX, centerY,
                        new MarsVertex(-1.0F, 1.0F, 1.0F), new MarsVertex(1.0F, 1.0F, 1.0F),
                        new MarsVertex(1.0F, -1.0F, 1.0F), new MarsVertex(-1.0F, -1.0F, 1.0F),
                        new MarsVertex(0.0F, 0.0F, 1.0F)),
                projectMarsFace(1, yaw, halfSize, centerX, centerY,
                        new MarsVertex(1.0F, 1.0F, -1.0F), new MarsVertex(-1.0F, 1.0F, -1.0F),
                        new MarsVertex(-1.0F, -1.0F, -1.0F), new MarsVertex(1.0F, -1.0F, -1.0F),
                        new MarsVertex(0.0F, 0.0F, -1.0F)),
                projectMarsFace(2, yaw, halfSize, centerX, centerY,
                        new MarsVertex(1.0F, 1.0F, 1.0F), new MarsVertex(1.0F, 1.0F, -1.0F),
                        new MarsVertex(1.0F, -1.0F, -1.0F), new MarsVertex(1.0F, -1.0F, 1.0F),
                        new MarsVertex(1.0F, 0.0F, 0.0F)),
                projectMarsFace(3, yaw, halfSize, centerX, centerY,
                        new MarsVertex(-1.0F, 1.0F, -1.0F), new MarsVertex(-1.0F, 1.0F, 1.0F),
                        new MarsVertex(-1.0F, -1.0F, 1.0F), new MarsVertex(-1.0F, -1.0F, -1.0F),
                        new MarsVertex(-1.0F, 0.0F, 0.0F)),
                projectMarsFace(4, yaw, halfSize, centerX, centerY,
                        new MarsVertex(-1.0F, 1.0F, -1.0F), new MarsVertex(1.0F, 1.0F, -1.0F),
                        new MarsVertex(1.0F, 1.0F, 1.0F), new MarsVertex(-1.0F, 1.0F, 1.0F),
                        new MarsVertex(0.0F, 1.0F, 0.0F)),
                projectMarsFace(5, yaw, halfSize, centerX, centerY,
                        new MarsVertex(-1.0F, -1.0F, 1.0F), new MarsVertex(1.0F, -1.0F, 1.0F),
                        new MarsVertex(1.0F, -1.0F, -1.0F), new MarsVertex(-1.0F, -1.0F, -1.0F),
                        new MarsVertex(0.0F, -1.0F, 0.0F))
        };
        sortMarsFacesBackToFront(faces);

        for (ProjectedFace face : faces) {
            renderMarsFace(graphics, face, marsAlpha);
        }
    }

    private void renderMarsBacklight(GuiGraphics graphics, int centerX, int centerY, float targetFace, float reveal) {
        float fade = Mth.clamp(reveal, 0.0F, 1.0F);
        drawMarsGlowDisk(graphics, centerX, centerY, targetFace * 1.58F, Math.round(22.0F * fade), 0x9E341C);
        drawMarsGlowDisk(graphics, centerX, centerY, targetFace * 1.18F, Math.round(28.0F * fade), 0xE65C28);
        drawMarsGlowDisk(graphics, centerX, centerY, targetFace * 0.76F, Math.round(18.0F * fade), 0xFF8444);
    }

    private void drawMarsGlowDisk(GuiGraphics graphics, int centerX, int centerY, float radius, int alpha, int rgb) {
        if (alpha <= 0 || radius <= 0.0F) {
            return;
        }
        int color = (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
        int intRadius = Mth.ceil(radius);
        for (int y = -intRadius; y <= intRadius; y += 2) {
            float normalizedY = y / radius;
            if (normalizedY < -1.0F || normalizedY > 1.0F) {
                continue;
            }
            int halfWidth = Mth.floor(Math.sqrt(1.0F - normalizedY * normalizedY) * radius);
            graphics.fill(centerX - halfWidth, centerY + y, centerX + halfWidth, centerY + y + 2, color);
        }
    }

    private void renderApproachViewport(GuiGraphics graphics, float tick) {
        float alpha = getApproachViewportAlpha(tick);
        if (alpha <= 0.04F) {
            return;
        }

        int topRail = Math.max(34, height / 12);
        int bottomRail = Math.max(58, height / 6);
        int sideRail = Math.max(44, width / 10);
        int viewLeft = sideRail;
        int viewRight = width - sideRail;
        int viewTop = topRail;
        int viewBottom = height - bottomRail;

        graphics.fill(0, 0, width, topRail, applyAlpha(0xEA10141A, alpha));
        graphics.fill(0, viewBottom, width, height, applyAlpha(0xF010141A, alpha));
        graphics.fill(0, topRail, sideRail, viewBottom, applyAlpha(0xDE10141A, alpha));
        graphics.fill(width - sideRail, topRail, width, viewBottom, applyAlpha(0xDE10141A, alpha));

        graphics.fill(viewLeft - 8, viewTop - 8, viewRight + 8, viewTop + 5, applyAlpha(0xCC303844, alpha));
        graphics.fill(viewLeft - 8, viewBottom - 5, viewRight + 8, viewBottom + 10, applyAlpha(0xCC303844, alpha));
        graphics.fill(viewLeft - 10, viewTop - 4, viewLeft + 5, viewBottom + 6, applyAlpha(0xCC252C36, alpha));
        graphics.fill(viewRight - 5, viewTop - 4, viewRight + 10, viewBottom + 6, applyAlpha(0xCC252C36, alpha));

        graphics.fill(viewLeft, viewTop, viewRight, viewTop + 2, applyAlpha(0xCC4FC7DA, alpha));
        graphics.fill(viewLeft, viewBottom - 2, viewRight, viewBottom, applyAlpha(0x994FC7DA, alpha));
        graphics.fill(viewLeft, viewTop, viewLeft + 2, viewBottom, applyAlpha(0x884FC7DA, alpha));
        graphics.fill(viewRight - 2, viewTop, viewRight, viewBottom, applyAlpha(0x884FC7DA, alpha));

        int corner = Math.max(18, Math.min(width, height) / 18);
        drawCornerBracket(graphics, viewLeft + 4, viewTop + 4, corner, true, true, alpha);
        drawCornerBracket(graphics, viewRight - 4, viewTop + 4, corner, false, true, alpha);
        drawCornerBracket(graphics, viewLeft + 4, viewBottom - 4, corner, true, false, alpha);
        drawCornerBracket(graphics, viewRight - 4, viewBottom - 4, corner, false, false, alpha);

        graphics.fillGradient(viewLeft + 4, viewTop + 3, viewRight - 4, viewTop + Math.max(34, height / 11),
                applyAlpha(0x244FC7DA, alpha), 0x00000000);
        graphics.fillGradient(viewLeft + 4, viewBottom - Math.max(24, height / 14), viewRight - 4, viewBottom - 3,
                0x00000000, applyAlpha(0x18283440, alpha));
        graphics.fillGradient(viewLeft + 8, viewTop + 8, viewRight - 8, viewBottom - 8,
                applyAlpha(0x101E5968, alpha), applyAlpha(0x08070B10, alpha));

        int glareY = viewTop + Math.max(14, (viewBottom - viewTop) / 5);
        graphics.fill(viewLeft + 24, glareY, viewRight - 38, glareY + 1, applyAlpha(0x558EEAFF, alpha));
        graphics.fill(viewLeft + 44, glareY + 16, viewRight - 92, glareY + 17, applyAlpha(0x2255C6D8, alpha));

        int statusY = Math.max(6, viewTop - 25);
        graphics.fill(viewLeft + 10, statusY - 3, viewLeft + 142, statusY + 11, applyAlpha(0x6610141A, alpha));
        graphics.drawString(font, Component.literal("ORSA VIEWPORT"), viewLeft + 15, statusY,
                applyAlpha(0xFF8DEAFF, alpha), false);
    }

    private float getApproachViewportAlpha(float tick) {
        int creditsStart = getCreditsStartTick();
        float fadeIn = smoothStep(Mth.clamp((tick - BLACK_END_TICKS) / 70.0F, 0.0F, 1.0F));
        float fadeOut = 1.0F - smoothStep(Mth.clamp((tick - (creditsStart - 120.0F)) / 120.0F, 0.0F, 1.0F));
        return fadeIn * fadeOut;
    }

    private void drawCornerBracket(GuiGraphics graphics, int x, int y, int size, boolean left, boolean top,
                                   float alpha) {
        int color = applyAlpha(0xDD8DEAFF, alpha);
        int horizontalStart = left ? x : x - size;
        int horizontalEnd = left ? x + size : x;
        int verticalStart = top ? y : y - size;
        int verticalEnd = top ? y + size : y;
        graphics.fill(horizontalStart, y, horizontalEnd, y + 2, color);
        graphics.fill(x, verticalStart, x + 2, verticalEnd, color);
        graphics.fill(horizontalStart, y + (top ? 2 : -2), horizontalStart + 6, y + (top ? 4 : 0),
                applyAlpha(0xAA8DEAFF, alpha));
    }

    private ProjectedFace projectMarsFace(int faceId, float yaw, float halfSize, float centerX, float centerY,
                                          MarsVertex v0, MarsVertex v1, MarsVertex v2, MarsVertex v3, MarsVertex normal) {
        MarsPoint p0 = projectMarsVertex(v0, yaw, halfSize, centerX, centerY);
        MarsPoint p1 = projectMarsVertex(v1, yaw, halfSize, centerX, centerY);
        MarsPoint p2 = projectMarsVertex(v2, yaw, halfSize, centerX, centerY);
        MarsPoint p3 = projectMarsVertex(v3, yaw, halfSize, centerX, centerY);
        MarsVertex litNormal = rotateMarsVertex(normal, yaw);
        float light = Mth.clamp(0.64F + Math.max(0.0F,
                litNormal.x() * -0.25F + litNormal.y() * 0.76F + litNormal.z() * -0.58F) * 0.34F,
                0.48F, 1.0F);
        return new ProjectedFace(faceId, p0, p1, p2, p3, light,
                (p0.depth() + p1.depth() + p2.depth() + p3.depth()) * 0.25F);
    }

    private MarsPoint projectMarsVertex(MarsVertex vertex, float yaw, float halfSize, float centerX, float centerY) {
        MarsVertex rotated = rotateMarsVertex(vertex, yaw);
        float perspective = 4.2F / (4.2F + rotated.z());
        return new MarsPoint(
                centerX + rotated.x() * halfSize * perspective,
                centerY - rotated.y() * halfSize * perspective,
                rotated.z());
    }

    private MarsVertex rotateMarsVertex(MarsVertex vertex, float yaw) {
        float cosYaw = Mth.cos(yaw);
        float sinYaw = Mth.sin(yaw);
        float x = vertex.x() * cosYaw + vertex.z() * sinYaw;
        float z = vertex.z() * cosYaw - vertex.x() * sinYaw;

        float cosPitch = Mth.cos(MARS_PITCH_RADIANS);
        float sinPitch = Mth.sin(MARS_PITCH_RADIANS);
        float y = vertex.y() * cosPitch - z * sinPitch;
        float pitchedZ = vertex.y() * sinPitch + z * cosPitch;

        float cosRoll = Mth.cos(MARS_ROLL_RADIANS);
        float sinRoll = Mth.sin(MARS_ROLL_RADIANS);
        float rolledX = x * cosRoll - y * sinRoll;
        float rolledY = x * sinRoll + y * cosRoll;
        return new MarsVertex(rolledX, rolledY, pitchedZ);
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

    private void renderMarsFace(GuiGraphics graphics, ProjectedFace face, float alpha) {
        MarsFaceTexture texture = marsTexture(face.faceId());
        if (texture.location() != null) {
            renderTexturedMarsFace(graphics, face, texture, alpha);
        } else {
            renderFallbackMarsFace(graphics, face, texture, alpha);
        }
        drawProjectedLine(graphics, face.p0(), face.p1(), applyAlpha(0x5531514D, alpha));
        drawProjectedLine(graphics, face.p1(), face.p2(), applyAlpha(0x55170B09, alpha));
        drawProjectedLine(graphics, face.p2(), face.p3(), applyAlpha(0x55170B09, alpha));
        drawProjectedLine(graphics, face.p3(), face.p0(), applyAlpha(0x5531514D, alpha));
    }

    private void renderTexturedMarsFace(GuiGraphics graphics, ProjectedFace face, MarsFaceTexture texture, float alpha) {
        float shade = Mth.clamp(face.light(), 0.25F, 1.08F);
        graphics.flush();
        RenderSystem.setShaderTexture(0, texture.location());
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // Subdivide the textured face so the manually projected cube gets a
        // stable perspective approximation. A single screen-space quad causes
        // affine texture swim during rotation.
        for (int row = 0; row < MARS_TEXTURE_SUBDIVISIONS; row++) {
            for (int col = 0; col < MARS_TEXTURE_SUBDIVISIONS; col++) {
                float u0 = col / (float) MARS_TEXTURE_SUBDIVISIONS;
                float u1 = (col + 1) / (float) MARS_TEXTURE_SUBDIVISIONS;
                float v0 = row / (float) MARS_TEXTURE_SUBDIVISIONS;
                float v1 = (row + 1) / (float) MARS_TEXTURE_SUBDIVISIONS;
                addMarsTexturedVertex(buffer, matrix, interpolateFace(face, u0, v0), u0, v0, shade, alpha);
                addMarsTexturedVertex(buffer, matrix, interpolateFace(face, u0, v1), u0, v1, shade, alpha);
                addMarsTexturedVertex(buffer, matrix, interpolateFace(face, u1, v1), u1, v1, shade, alpha);
                addMarsTexturedVertex(buffer, matrix, interpolateFace(face, u1, v0), u1, v0, shade, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void addMarsTexturedVertex(BufferBuilder buffer, Matrix4f matrix, MarsPoint point, float u, float v, float shade,
                                       float alpha) {
        buffer.addVertex(matrix, point.x(), point.y(), 0.0F)
                .setUv(u, v)
                .setColor(shade, shade, shade, Mth.clamp(alpha, 0.0F, 1.0F));
    }

    private void renderFallbackMarsFace(GuiGraphics graphics, ProjectedFace face, MarsFaceTexture texture, float alpha) {
        for (int row = 0; row < MARS_FALLBACK_TEXTURE_GRID; row++) {
            for (int col = 0; col < MARS_FALLBACK_TEXTURE_GRID; col++) {
                float u0 = col / (float) MARS_FALLBACK_TEXTURE_GRID;
                float u1 = (col + 1) / (float) MARS_FALLBACK_TEXTURE_GRID;
                float v0 = row / (float) MARS_FALLBACK_TEXTURE_GRID;
                float v1 = (row + 1) / (float) MARS_FALLBACK_TEXTURE_GRID;
                fillProjectedQuad(graphics,
                        interpolateFace(face, u0, v0),
                        interpolateFace(face, u1, v0),
                        interpolateFace(face, u1, v1),
                        interpolateFace(face, u0, v1),
                        applyAlpha(shadeColor(texture.sample((u0 + u1) * 0.5F, (v0 + v1) * 0.5F), face.light()),
                                alpha));
            }
        }
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

    private void ensureMarsTextures() {
        if (marsTextures != null) {
            return;
        }
        marsTextures = new MarsFaceTexture[MARS_FACE_TEXTURES.length];
        for (int i = 0; i < MARS_FACE_TEXTURES.length; i++) {
            marsTextures[i] = loadMarsTexture(i, MARS_FACE_TEXTURES[i]);
        }
    }

    private MarsFaceTexture marsTexture(int faceId) {
        if (marsTextures == null || faceId < 0 || faceId >= marsTextures.length || marsTextures[faceId] == null) {
            return createFallbackMarsTexture(faceId);
        }
        return marsTextures[faceId];
    }

    private MarsFaceTexture loadMarsTexture(int faceId, String name) {
        if (minecraft == null) {
            return createFallbackMarsTexture(faceId);
        }

        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                FrozenDawn.MOD_ID, "textures/gui/ending/" + name + ".png");
        try {
            Resource resource = minecraft.getResourceManager()
                    .getResource(location)
                    .orElseThrow(() -> new IOException("Missing Mars cubemap face: " + location));
            try (InputStream stream = resource.open()) {
                BufferedImage image = ImageIO.read(stream);
                if (image == null) {
                    throw new IOException("Unreadable Mars cubemap face: " + location);
                }
                int width = image.getWidth();
                int height = image.getHeight();
                int[] pixels = new int[width * height];
                image.getRGB(0, 0, width, height, pixels, 0, width);
                return new MarsFaceTexture(location, width, height, pixels);
            }
        } catch (IOException exception) {
            FrozenDawn.LOGGER.error("Failed to load Mars cubemap face {}", location, exception);
            return createFallbackMarsTexture(faceId);
        }
    }

    private MarsFaceTexture createFallbackMarsTexture(int faceId) {
        int width = 8;
        int height = 8;
        int[] pixels = new int[width * height];
        int base = faceId == 4 ? 0xFFE8F6FF : faceId == 5 ? 0xFF6A251D : 0xFFE07134;
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = base;
        }
        return new MarsFaceTexture(null, width, height, pixels);
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
            int complianceLocal = local - messageEnd;
            if (complianceLocal < COMPLIANCE_STATIC_TICKS) {
                drawComplianceStatic(graphics, complianceLocal,
                        pulseAlpha(complianceLocal, 8.0F, COMPLIANCE_STATIC_TICKS - 10.0F));
                return;
            }

            int messageLocal = complianceLocal - COMPLIANCE_STATIC_TICKS;
            float alpha = pulseAlpha(messageLocal, 22.0F, COMPLIANCE_TICKS - COMPLIANCE_STATIC_TICKS - 45.0F);
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

    private void drawComplianceStatic(GuiGraphics graphics, int local, float alpha) {
        if (alpha <= 0.04F) {
            return;
        }

        int panelW = Math.min(520, width - 32);
        int panelH = 82;
        int panelX = (width - panelW) / 2;
        int panelY = height / 2 - panelH / 2;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, applyAlpha(0xA0180708, alpha));
        graphics.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + panelH - 2,
                applyAlpha(0x80100508, alpha));
        graphics.fill(panelX + 6, panelY + 6, panelX + 8, panelY + panelH - 6, applyAlpha(0xCCFF5C5C, alpha));

        int seed = local * 97;
        for (int i = 0; i < 44; i++) {
            float roll = hash01(seed + i * 31);
            int y = panelY + 8 + Math.round(hash01(seed + i * 47) * (panelH - 16));
            int x = panelX + 14 + Math.round(hash01(seed + i * 71) * (panelW - 44));
            int len = 8 + Math.round(hash01(seed + i * 83) * 82);
            int color = roll < 0.35F ? 0xFFFFC0C0 : roll < 0.72F ? 0xFFFF5C5C : 0xFF241014;
            graphics.fill(x, y, Math.min(panelX + panelW - 14, x + len), y + 1, applyAlpha(color, alpha));
        }

        for (int i = 0; i < 18; i++) {
            int y = panelY + 12 + i * 4;
            int color = i % 2 == 0 ? 0x2220F0FF : 0x22100000;
            graphics.fill(panelX + 12, y, panelX + panelW - 12, y + 1, applyAlpha(color, alpha));
        }

        if (local % 5 != 0) {
            graphics.drawString(font,
                    Component.literal("[ORSA] ACCESS CONTROL // SIGNAL CORRUPT"),
                    panelX + 16,
                    panelY + 8,
                    applyAlpha(0xFFFF8B8B, alpha),
                    false);
        }
        if (local % 7 < 4) {
            graphics.drawString(font,
                    Component.literal("//// DECRYPTING COMPLIANCE PACKET ////"),
                    panelX + 16,
                    panelY + 58,
                    applyAlpha(0xFFFFC0C0, alpha * 0.82F),
                    false);
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

    private record MarsFaceTexture(ResourceLocation location, int width, int height, int[] pixels) {
        int sample(float u, float v) {
            int x = Mth.clamp(Mth.floor(u * width), 0, width - 1);
            int y = Mth.clamp(Mth.floor(v * height), 0, height - 1);
            return pixels[y * width + x];
        }
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
