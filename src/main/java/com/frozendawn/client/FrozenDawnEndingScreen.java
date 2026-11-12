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
import net.minecraft.sounds.SoundSource;
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
    private static final int MARS_START_TICKS = EndingOrbit.MARS_START;
    private static final float EARTH_RADIUS_PER_HEIGHT = 280.0F / 240.0F;
    private static final float EARTH_CENTER_BELOW_PER_HEIGHT = 90.0F / 240.0F;
    private static final float EARTH_ROLL_RADIANS = (float) Math.toRadians(-8.0);
    private static final float EARTH_LIMB_GLOW_PIXELS = 3.0F;
    private static final int BASE_CREDITS_START_TICKS = 60 * 20;
    private static final int COMPLIANCE_GLITCH_TICKS = EndingGlitch.GLITCH_TICKS;
    private static final int COMPLIANCE_TTS_DELAY_TICKS = COMPLIANCE_GLITCH_TICKS + 10;
    private static final int LINE_HEIGHT = 12;
    private static final int MARS_TEXTURE_SUBDIVISIONS = 16;
    private static final int MARS_FALLBACK_TEXTURE_GRID = 64;
    private static final float MARS_YAW_OFFSET = 1.10F;
    private static final float MARS_PITCH_RADIANS = -0.24F;
    private static final float MARS_ROLL_RADIANS = -0.22F;
    private static final int CREDIT_FAST_FORWARD_TICKS_PER_TICK = 55;
    private static final SoundSource[] ENDING_MUTED_SOURCES = {
            SoundSource.RECORDS,
            SoundSource.WEATHER,
            SoundSource.BLOCKS,
            SoundSource.HOSTILE,
            SoundSource.NEUTRAL,
            SoundSource.PLAYERS,
            SoundSource.AMBIENT,
            SoundSource.VOICE
    };
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
    private EarthGlobeCells earthCells;
    private EarthMap earthMap;

    public FrozenDawnEndingScreen(EndingSequencePayload payload) {
        super(Component.translatable("screen.frozendawn.ending.title"));
        this.payload = payload;
        this.creditLines = buildCreditLines(payload);
    }

    @Override
    protected void init() {
        if (ageTicks == 0) {
            EndingMusicController.start();
            suppressWorldAudio();
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
            int fadeTicks = epilogue().getFirst().ticks();
            int fadeRemaining = Mth.clamp(getPostCreditsStartTick() + fadeTicks - ageTicks, 1, fadeTicks);
            EndingMusicController.fadeOutAndStop(fadeRemaining);
        }
        updateComplianceAudioCue();
        EndingMusicController.tick(ageTicks);
        suppressWorldAudio();
        updateReturnButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float tick = ageTicks + partialTick;
        renderSpace(graphics, tick);
        renderEarthOrbit(graphics, tick);
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
        // Credits music uses a runtime stream and volume ramp; Minecraft only
        // advances streaming/tickable sound channels while the game is unpaused.
        return false;
    }

    private int getCreditsStartTick() {
        return BASE_CREDITS_START_TICKS;
    }

    private List<EndingEpilogue.Beat> epilogue() {
        return EndingEpilogue.beats(payload.conspiracyDiscovered());
    }

    private int getEndingCompleteTicks() {
        return EndingEpilogue.endingCompleteTicks(payload.conspiracyDiscovered());
    }

    private int getPostCreditsStartTick() {
        return getEndingCompleteTicks() - getPostCreditsDurationTicks();
    }

    private int getPostCreditsDurationTicks() {
        return EndingEpilogue.length(payload.conspiracyDiscovered());
    }

    private int getComplianceStartTick() {
        return getPostCreditsStartTick()
                + EndingEpilogue.startOf(payload.conspiracyDiscovered(), EndingEpilogue.Kind.COMPLIANCE);
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
        return ageTicks >= getEndingCompleteTicks();
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

    private void suppressWorldAudio() {
        if (minecraft == null) {
            return;
        }
        for (SoundSource source : ENDING_MUTED_SOURCES) {
            minecraft.getSoundManager().stop(null, source);
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
        float reveal = Mth.clamp((tick - MARS_START_TICKS) / (float) (BASE_CREDITS_START_TICKS - MARS_START_TICKS), 0.0F, 1.0F);
        if (reveal <= 0.0F) {
            return;
        }
        ensureMarsTextures();

        float revealPulse = smoothStep(reveal);
        MarsPlacement placement = marsPlacement(tick);
        float targetFace = placement.face();
        float halfSize = targetFace * 0.5F;
        float yaw = MARS_YAW_OFFSET + (tick - MARS_START_TICKS) * 0.012F;
        float marsAlpha = smoothStep(Mth.clamp((tick - MARS_START_TICKS) / 80.0F, 0.0F, 1.0F));
        float centerX = placement.centerX();
        float centerY = placement.centerY();
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

    /** The low orbit prelude: the frozen Earth under the window, life support, and the countdown to the burn. */
    private void renderEarthOrbit(GuiGraphics graphics, float tick) {
        float alpha = EndingOrbit.earthAlpha(tick);
        if (alpha <= 0.04F) {
            return;
        }
        renderEarthGlobe(graphics, tick, alpha);
        drawViewportChrome(graphics, alpha);
        renderLifeSupport(graphics, tick, alpha);
    }

    /**
     * A blocky globe whose limb arcs across the window, its sub-point below the frame: each cell samples the frozen
     * Earth map at the point under it, darkened toward the limb, with a thin airglow outside. Same-colored cells in a
     * row are drawn as one fill.
     */
    private void renderEarthGlobe(GuiGraphics graphics, float tick, float alpha) {
        EarthGlobeCells cells = earthGlobeCells();
        EarthMap map = earthMap();
        double subLongitude = EndingOrbit.longitude(tick);
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24;
        int block = cells.block();
        for (int row = 0; row < cells.rows(); row++) {
            int runStart = 0;
            int runColor = 0;
            for (int col = 0; col <= cells.cols(); col++) {
                int color = 0;
                if (col < cells.cols()) {
                    int index = row * cells.cols() + col;
                    float shade = cells.shade()[index];
                    if (shade > 0.0F) {
                        int rgb = map.sample(cells.latitude()[index], cells.longitude()[index] + subLongitude);
                        color = a | scaleRgb(rgb, shade);
                    } else if (cells.glow()[index] > 0.0F) {
                        color = applyAlpha(Mth.clamp(Math.round(cells.glow()[index] * 0x99), 0, 255) << 24 | 0x44E6A8,
                                alpha);
                    }
                }
                if (col == cells.cols() || color != runColor) {
                    if (runColor != 0) {
                        graphics.fill(runStart * block, row * block, col * block, (row + 1) * block, runColor);
                    }
                    runStart = col;
                    runColor = color;
                }
            }
        }
    }

    private static int scaleRgb(int rgb, float shade) {
        int r = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) * shade), 0, 255);
        int g = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) * shade), 0, 255);
        int b = Mth.clamp(Math.round((rgb & 0xFF) * shade), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private EarthGlobeCells earthGlobeCells() {
        int block = Math.max(2, Mth.ceil(width / 214.0F));
        if (earthCells != null && earthCells.width() == width && earthCells.height() == height
                && earthCells.block() == block) {
            return earthCells;
        }
        int cols = Mth.ceil(width / (float) block);
        int rows = Mth.ceil(height / (float) block);
        float radius = height * EARTH_RADIUS_PER_HEIGHT;
        float centerX = width / 2.0F;
        float centerY = height + height * EARTH_CENTER_BELOW_PER_HEIGHT;
        float cos = Mth.cos(EARTH_ROLL_RADIANS);
        float sin = Mth.sin(EARTH_ROLL_RADIANS);
        double[] latitude = new double[cols * rows];
        double[] longitude = new double[cols * rows];
        float[] shade = new float[cols * rows];
        float[] glow = new float[cols * rows];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                float sx = (col * block + block / 2.0F - centerX) / radius;
                float sy = (centerY - row * block - block / 2.0F) / radius;
                float x = sx * cos - sy * sin;
                float y = sx * sin + sy * cos;
                double[] point = EndingOrbit.surface(x, y, EndingOrbit.SUB_POINT_LATITUDE, 0.0);
                if (point == null) {
                    float beyond = (Mth.sqrt(x * x + y * y) - 1.0F) * radius / EARTH_LIMB_GLOW_PIXELS;
                    glow[index] = beyond < 1.0F ? 1.0F - beyond : 0.0F;
                    continue;
                }
                latitude[index] = point[0];
                longitude[index] = point[1];
                float z = Mth.sqrt(Math.max(0.0F, 1.0F - x * x - y * y));
                shade[index] = 0.85F * (0.55F + 0.45F * (float) Math.pow(z, 0.6));
            }
        }
        earthCells = new EarthGlobeCells(width, height, block, cols, rows, latitude, longitude, shade, glow);
        return earthCells;
    }

    private EarthMap earthMap() {
        if (earthMap != null) {
            return earthMap;
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                FrozenDawn.MOD_ID, "textures/gui/ending/earth_surface.png");
        if (minecraft != null) {
            try {
                Resource resource = minecraft.getResourceManager().getResource(location)
                        .orElseThrow(() -> new IOException("Missing Earth map: " + location));
                try (InputStream stream = resource.open()) {
                    BufferedImage image = ImageIO.read(stream);
                    if (image == null) {
                        throw new IOException("Unreadable Earth map: " + location);
                    }
                    int[] pixels = new int[image.getWidth() * image.getHeight()];
                    image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
                    earthMap = new EarthMap(image.getWidth(), image.getHeight(), pixels);
                    return earthMap;
                }
            } catch (IOException exception) {
                FrozenDawn.LOGGER.error("Failed to load the Earth map {}", location, exception);
            }
        }
        earthMap = new EarthMap(1, 1, new int[] {0xFF7A8EA4});
        return earthMap;
    }

    private void renderLifeSupport(GuiGraphics graphics, float tick, float alpha) {
        int panelW = 150;
        int panelH = 78;
        int panelX = 18;
        int panelY = height - panelH - 18;
        if (panelY < 34 || panelX + panelW > width / 2) {
            return;
        }
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, applyAlpha(0x7006121A, alpha));
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, applyAlpha(0xCC4FC7DA, alpha));
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, applyAlpha(0x334FC7DA, alpha));
        graphics.drawString(font, Component.translatable(EndingOrbit.TITLE), panelX + 8, panelY + 6,
                applyAlpha(0xFF8DEAFF, alpha), false);
        graphics.fill(panelX + panelW - 12, panelY + 8, panelX + panelW - 9, panelY + 11, applyAlpha(0xFF44E6A8, alpha));

        int valueRight = panelX + panelW - 8;
        drawReadout(graphics, panelX + 8, valueRight, panelY + 19, EndingOrbit.O2_LABEL,
                Component.translatable(EndingOrbit.O2_VALUE), alpha);
        drawReadout(graphics, panelX + 8, valueRight, panelY + 29, EndingOrbit.CABIN_LABEL,
                Component.translatable(EndingOrbit.CABIN_VALUE), alpha);
        drawReadout(graphics, panelX + 8, valueRight, panelY + 39, EndingOrbit.CO2_LABEL,
                Component.translatable(EndingOrbit.CO2_VALUE), alpha);
        drawReadout(graphics, panelX + 8, valueRight, panelY + 49, EndingOrbit.HULL_LABEL,
                Component.translatable(EndingOrbit.HULL_VALUE), alpha);
        for (int x = panelX + 8; x < panelX + panelW - 8; x += 3) {
            graphics.fill(x, panelY + 61, x + 1, panelY + 62, applyAlpha(0x5555C6D8, alpha));
        }
        drawReadout(graphics, panelX + 8, valueRight, panelY + 66, EndingOrbit.BURN_LABEL,
                Component.translatable(EndingOrbit.BURN_VALUE, EndingOrbit.burnClock(tick)), 0xFFFFD27A, alpha);
    }

    private MarsPlacement marsPlacement(float tick) {
        float reveal = Mth.clamp((tick - MARS_START_TICKS) / (float) (BASE_CREDITS_START_TICKS - MARS_START_TICKS), 0.0F, 1.0F);
        float revealPulse = smoothStep(reveal);
        return new MarsPlacement(
                width / 2.0F + Mth.lerp(revealPulse, width * -0.10F, width * 0.04F),
                height * 0.46F + Mth.lerp(revealPulse, height * -0.05F, 0.0F),
                Mth.lerp(revealPulse, 28.0F, Math.max(48.0F, Math.min(width, height) * 0.38F)));
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

        drawViewportChrome(graphics, alpha);

        MarsPlacement mars = marsPlacement(tick);
        int half = Math.round(mars.face() * 0.62F);
        int marsX = Math.round(mars.centerX());
        int marsY = Math.round(mars.centerY());
        drawCornerTicks(graphics, marsX - half, marsY - half, marsX + half, marsY + half, 5,
                applyAlpha(0xCCE06D3C, alpha));
        graphics.drawString(font, Component.translatable(EndingCockpit.MARS), marsX - half, marsY + half + 4,
                applyAlpha(0xFFE06D3C, alpha), false);

        renderApproachVectorTelemetry(graphics, tick, alpha);
    }

    /** The glass cockpit around any view: edge-to-edge, darkened at the rim, one thin frame, label and clock. */
    private void drawViewportChrome(GuiGraphics graphics, float alpha) {
        // Glass cockpit: the view runs edge to edge, darkened only at its rim, inside one thin frame.
        int edge = 0x050A10;
        graphics.fillGradient(0, 0, width, 26, applyAlpha(0xC0000000 | edge, alpha), edge);
        graphics.fillGradient(0, height - 30, width, height, edge, applyAlpha(0xD0000000 | edge, alpha));
        for (int i = 0; i < 22; i++) {
            int column = applyAlpha(0xA0000000 | edge, alpha * (1.0F - i / 22.0F));
            graphics.fill(i, 0, i + 1, height, column);
            graphics.fill(width - 1 - i, 0, width - i, height, column);
        }

        int inset = 10;
        int left = inset;
        int top = inset;
        int right = width - inset - 1;
        int bottom = height - inset - 1;
        int frame = applyAlpha(0x444FC7DA, alpha);
        graphics.fill(left, top, right + 1, top + 1, frame);
        graphics.fill(left, bottom, right + 1, bottom + 1, frame);
        graphics.fill(left, top, left + 1, bottom + 1, frame);
        graphics.fill(right, top, right + 1, bottom + 1, frame);
        drawCornerTicks(graphics, left, top, right, bottom, 6, applyAlpha(0xDD8DEAFF, alpha));

        graphics.fill(18, 18, 21, 21, applyAlpha(0xFF44E6A8, alpha));
        graphics.drawString(font, Component.translatable(EndingCockpit.VIEWPORT), 25, 16,
                applyAlpha(0xFF8DEAFF, alpha), false);
        String clock = EndingCockpit.missionClock(payload.playTimeTicks() + (long) ageTicks);
        graphics.drawString(font, clock, width - 18 - font.width(clock), 16, applyAlpha(0xAA8DEAFF, alpha), false);
    }

    private float getApproachViewportAlpha(float tick) {
        int creditsStart = getCreditsStartTick();
        float fadeIn = smoothStep(Mth.clamp((tick - MARS_START_TICKS) / 70.0F, 0.0F, 1.0F));
        float fadeOut = 1.0F - smoothStep(Mth.clamp((tick - (creditsStart - 120.0F)) / 120.0F, 0.0F, 1.0F));
        return fadeIn * fadeOut;
    }

    /** Small L-shaped ticks at the four corners of a box, each arm this many pixels long. */
    private void drawCornerTicks(GuiGraphics graphics, int left, int top, int right, int bottom, int arm, int color) {
        graphics.fill(left, top, left + arm + 1, top + 1, color);
        graphics.fill(left, top, left + 1, top + arm + 1, color);
        graphics.fill(right - arm, top, right + 1, top + 1, color);
        graphics.fill(right, top, right + 1, top + arm + 1, color);
        graphics.fill(left, bottom, left + arm + 1, bottom + 1, color);
        graphics.fill(left, bottom - arm, left + 1, bottom + 1, color);
        graphics.fill(right - arm, bottom, right + 1, bottom + 1, color);
        graphics.fill(right, bottom - arm, right + 1, bottom + 1, color);
    }

    private void renderApproachVectorTelemetry(GuiGraphics graphics, float tick, float alpha) {
        int panelW = 150;
        int panelH = 78;
        int panelX = 18;
        int panelY = height - panelH - 18;
        if (panelY < 34 || panelX + panelW > width / 2) {
            return;
        }

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, applyAlpha(0x7006121A, alpha));
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, applyAlpha(0xCC4FC7DA, alpha));
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, applyAlpha(0x334FC7DA, alpha));
        graphics.drawString(font, Component.translatable("screen.frozendawn.ending.vector.title"),
                panelX + 8, panelY + 6, applyAlpha(0xFF8DEAFF, alpha), false);
        graphics.fill(panelX + panelW - 12, panelY + 8, panelX + panelW - 9, panelY + 11, applyAlpha(0xFF44E6A8, alpha));

        float approach = (tick - MARS_START_TICKS) / (float) (getCreditsStartTick() - MARS_START_TICKS);
        String range = EndingCockpit.groupThousands(EndingCockpit.rangeKm(approach));
        int valueRight = panelX + panelW - 8;
        drawReadout(graphics, panelX + 8, valueRight, panelY + 19, EndingCockpit.GRAVITY_LABEL,
                Component.translatable(EndingCockpit.GRAVITY_VALUE), alpha);
        drawReadout(graphics, panelX + 8, valueRight, panelY + 29, EndingCockpit.CORRIDOR_LABEL,
                Component.translatable(EndingCockpit.CORRIDOR_VALUE), alpha);
        drawReadout(graphics, panelX + 8, valueRight, panelY + 39, EndingCockpit.RANGE_LABEL,
                Component.translatable(EndingCockpit.RANGE_VALUE, range), alpha);

        int graphX = panelX + 8;
        int graphW = panelW - 17;
        int graphY = panelY + 56;
        for (int x = graphX; x < graphX + graphW; x += 3) {
            graphics.fill(x, graphY, x + 1, graphY + 1, applyAlpha(0x5555C6D8, alpha));
        }
        int previousX = graphX;
        int previousY = trajectoryY(graphY, 0.0F);
        for (int i = 1; i <= 40; i++) {
            float t = i / 40.0F;
            int x = graphX + Math.round(t * graphW);
            int y = trajectoryY(graphY, t);
            drawHudLine(graphics, previousX, previousY, x, y, applyAlpha(0xDD8DEAFF, alpha));
            previousX = x;
            previousY = y;
        }
        float craft = Mth.clamp(0.22F + Mth.sin(tick * 0.025F) * 0.015F, 0.0F, 1.0F);
        int craftX = graphX + Math.round(craft * graphW);
        int craftY = trajectoryY(graphY, craft);
        graphics.fill(craftX - 1, craftY - 1, craftX + 2, craftY + 2, applyAlpha(0xFFE7F5FF, alpha));
        graphics.fill(previousX - 2, previousY - 2, previousX + 3, previousY + 3, applyAlpha(0xFFE06D3C, alpha));

        drawReadout(graphics, panelX + 8, valueRight, panelY + 66, EndingCockpit.CAPTURE_LABEL,
                Component.translatable(EndingCockpit.CAPTURE_VALUE), alpha);
    }

    private static int trajectoryY(int baseY, float t) {
        return baseY + 4 - Math.round(Mth.sin(t * Mth.PI * 0.55F) * 7.0F);
    }

    private void drawReadout(GuiGraphics graphics, int left, int right, int y, String labelKey, Component value,
                             float alpha) {
        drawReadout(graphics, left, right, y, labelKey, value, 0xFFD9FFF6, alpha);
    }

    private void drawReadout(GuiGraphics graphics, int left, int right, int y, String labelKey, Component value,
                             int valueColor, float alpha) {
        graphics.drawString(font, Component.translatable(labelKey), left, y, applyAlpha(0xFF6FA3AC, alpha), false);
        graphics.drawString(font, value, right - font.width(value), y, applyAlpha(valueColor, alpha), false);
    }

    private void drawHudLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int steps = Math.max(1, Math.max(dx, dy));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(Mth.lerp(t, x0, x1));
            int y = Math.round(Mth.lerp(t, y0, y1));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
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
        float y = height + 62.0F - progress * travel;

        for (CreditLine line : creditLines) {
            if (y + line.height() >= -140 && y <= height + 40) {
                line.render(graphics, font, logoRenderer, width, y);
            }
            y += line.height();
        }
    }

    private void renderSkipHint(GuiGraphics graphics) {
        Component hint = Component.translatable("screen.frozendawn.ending.skip_hint");
        int x = width - font.width(hint) - 18;
        int y = height - 22;
        graphics.fill(x - 5, y - 4, width - 14, y + 11, 0x66061014);
        graphics.drawString(font, hint, x, y, 0xCCB8EFFF, false);
    }

    private void renderPostCredits(GuiGraphics graphics) {
        int local = ageTicks - getPostCreditsStartTick();
        EndingEpilogue.Beat beat = null;
        for (EndingEpilogue.Beat candidate : epilogue()) {
            if (local < candidate.ticks()) {
                beat = candidate;
                break;
            }
            local -= candidate.ticks();
        }
        if (beat == null) {
            // Past the timeline: the last beat's own rule decides what stays on screen.
            beat = epilogue().getLast();
            local = beat.ticks();
        }

        if (beat.kind() == EndingEpilogue.Kind.FADE) {
            int alpha = Mth.clamp(Math.round(local / (float) beat.ticks() * 255.0F), 0, 255);
            graphics.fill(0, 0, width, height, alpha << 24);
            return;
        }
        graphics.fill(0, 0, width, height, 0xFF000000);

        switch (beat.kind()) {
            case COLONY -> drawEndingTerminalPanel(graphics,
                    Component.translatable("screen.frozendawn.ending.terminal.arrival"),
                    Component.translatable(beat.key()),
                    beat.titleColor(),
                    beat.bodyColor(),
                    beat.accentColor(),
                    pulseAlpha(local, 28.0F, beat.ticks() - 45.0F),
                    -1);
            case SIGNAL -> drawEndingTerminalPanel(graphics,
                    Component.translatable("screen.frozendawn.ending.terminal.signal"),
                    Component.translatable(beat.key()),
                    beat.titleColor(),
                    beat.bodyColor(),
                    beat.accentColor(),
                    EndingEpilogue.signalHolds(payload.conspiracyDiscovered())
                            ? Mth.clamp(local / 28.0F, 0.0F, 1.0F)
                            : pulseAlpha(local, 28.0F, beat.ticks() - 35.0F),
                    local);
            case COMPLIANCE -> renderCompliance(graphics, beat, local);
            case END_CARD -> {
                // Alone on black: no panel, header or frame, and it stays as the last frame.
                int alpha = Math.round(EndingEpilogue.cardAlpha(local) * 255.0F);
                if (alpha > 10) {
                    graphics.drawCenteredString(font, Component.translatable(beat.key()), width / 2, height / 2 - 4,
                            (alpha << 24) | (beat.bodyColor() & 0xFFFFFF));
                }
            }
            default -> {
                // HOLD is plain black, with nothing drawn.
            }
        }
    }

    private void renderCompliance(GuiGraphics graphics, EndingEpilogue.Beat beat, int local) {
        if (!payload.showComplianceLine()) {
            return;
        }
        if (local < COMPLIANCE_GLITCH_TICKS) {
            renderGlitchedWelcome(graphics, local);
            return;
        }
        int messageLocal = local - COMPLIANCE_GLITCH_TICKS;
        drawEndingTerminalPanel(graphics,
                Component.translatable("screen.frozendawn.ending.terminal.access"),
                Component.translatable(beat.key()),
                beat.titleColor(),
                beat.bodyColor(),
                beat.accentColor(),
                pulseAlpha(messageLocal, 22.0F, beat.ticks() - COMPLIANCE_GLITCH_TICKS - 45.0F),
                -1);
    }

    /**
     * Ending B's cut: the held welcome tears into sideways slices, its letters corrupt, it bleeds to red behind a red
     * ghost and scan lines, and it blinks out just before the access-control line.
     */
    private void renderGlitchedWelcome(GuiGraphics graphics, int local) {
        if (EndingGlitch.blankFrame(local)) {
            return;
        }
        float progress = EndingGlitch.progress(local);
        EndingEpilogue.Beat signal = epilogue().stream()
                .filter(beat -> beat.kind() == EndingEpilogue.Kind.SIGNAL).findFirst().orElseThrow();
        String welcome = Component.translatable(signal.key()).getString();
        String corrupted = EndingGlitch.corrupt(welcome, progress, local);
        Component title = EndingGlitch.titleCorrupted(local, progress)
                ? Component.translatable("screen.frozendawn.ending.terminal.corrupt")
                : Component.translatable("screen.frozendawn.ending.terminal.signal");
        int titleColor = EndingGlitch.mix(signal.titleColor(), 0xFFFF8B8B, progress);
        int bodyColor = EndingGlitch.mix(signal.bodyColor(), 0xFFFFC0C0, progress);
        int accentColor = EndingGlitch.mix(signal.accentColor(), 0xCCFF5C5C, progress);

        int top = height / 2 - 40;
        for (int slice = 0; top + slice * EndingGlitch.SLICE_HEIGHT < height / 2 + 40; slice++) {
            int y = top + slice * EndingGlitch.SLICE_HEIGHT;
            graphics.enableScissor(0, y, width, y + EndingGlitch.SLICE_HEIGHT);
            graphics.pose().pushPose();
            graphics.pose().translate(EndingGlitch.sliceOffset(slice, local, progress), 0.0F, 0.0F);
            drawTerminalPanel(graphics, title, corrupted, welcome, titleColor, bodyColor, accentColor, 1.0F, -1);
            graphics.pose().popPose();
            graphics.disableScissor();
        }
        if (progress > 0.2F) {
            graphics.pose().pushPose();
            graphics.pose().translate(2.0F, 0.0F, 0.0F);
            drawTerminalPanel(graphics, title, corrupted, welcome, 0xFFFF5C5C, 0xFFFF5C5C, 0xCCFF5C5C,
                    0.35F * progress, -1);
            graphics.pose().popPose();
        }
        for (int line = 0; line < Math.round(progress * 10.0F); line++) {
            int y = Math.round(EndingGlitch.scanLine(local, line) * height);
            graphics.fill(0, y, width, y + 1, line % 2 == 0 ? 0x66FFC0C0 : 0x88FF5C5C);
        }
    }

    private void renderReturnHint(GuiGraphics graphics) {
        Component hint = Component.translatable("screen.frozendawn.ending.return_hint");
        graphics.drawCenteredString(font, hint, width / 2, height - 58, 0xFFB8EFFF);
    }

    /**
     * A glass terminal panel sized to its text. With typingTicks at 0 or more, the body types in behind a block
     * cursor, which blinks once the line is complete; at -1 the body shows whole.
     */
    private void drawEndingTerminalPanel(GuiGraphics graphics, Component title, Component body,
                                         int titleColor, int bodyColor, int accentColor, float alpha,
                                         int typingTicks) {
        String text = body.getString();
        drawTerminalPanel(graphics, title, text, text, titleColor, bodyColor, accentColor, alpha, typingTicks);
    }

    /** The panel sized to sizingText, so a corrupted line keeps its clean line's frame. */
    private void drawTerminalPanel(GuiGraphics graphics, Component title, String text, String sizingText,
                                   int titleColor, int bodyColor, int accentColor, float alpha, int typingTicks) {
        if (alpha <= 0.04F) {
            return;
        }
        int panelW = Math.min(width - 32, Math.max(200, font.width(sizingText) + 40));
        List<String> lines = font.getSplitter().splitLines(text, panelW - 32, net.minecraft.network.chat.Style.EMPTY)
                .stream().map(net.minecraft.network.chat.FormattedText::getString).toList();
        int panelH = 36 + lines.size() * 9;
        int panelX = (width - panelW) / 2;
        int panelY = height / 2 - panelH / 2;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, applyAlpha(0x9008120F, alpha));
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, applyAlpha(accentColor, alpha));
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, applyAlpha(accentColor, alpha * 0.3F));
        graphics.fill(panelX + 16, panelY + 9, panelX + 19, panelY + 12, applyAlpha(accentColor, alpha));
        graphics.drawString(font, Component.literal("[ORSA] ").append(title),
                panelX + 23, panelY + 8, applyAlpha(titleColor, alpha * 0.8F), false);

        int typed = typingTicks < 0 ? text.length() : EndingCockpit.typedLength(typingTicks, text.length());
        int lineY = panelY + 24;
        int cursorX = panelX + 16;
        int cursorY = lineY;
        int from = 0;
        for (String line : lines) {
            int start = Math.max(from, text.indexOf(line, from));
            from = start + line.length();
            int visible = Mth.clamp(typed - start, 0, line.length());
            if (visible > 0) {
                String shown = line.substring(0, visible);
                graphics.drawString(font, shown, panelX + 16, lineY, applyAlpha(bodyColor, alpha), false);
                cursorX = panelX + 16 + font.width(shown);
                cursorY = lineY;
            }
            lineY += 9;
        }
        boolean typing = typed < text.length();
        if (typingTicks >= 0 && (typing || (typingTicks / 10) % 2 == 0)) {
            graphics.fill(cursorX + 1, cursorY, cursorX + 6, cursorY + 8, applyAlpha(bodyColor, alpha * 0.85F));
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
        lines.add(CreditLine.section(Component.translatable("screen.frozendawn.ending.section_survival")));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.days", payload.daysSurvived())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.lowest_temp",
                formatTemperature(payload.lowestTemperatureSurvived()))));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.phases",
                formatPhaseMask(payload.phasesWitnessedMask()))));
        lines.add(CreditLine.blank(18));

        lines.add(CreditLine.section(Component.translatable("screen.frozendawn.ending.section_world")));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.blocks_frozen",
                formatCount(payload.blocksFrozen()))));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.structures",
                formatCount(payload.structuresDiscovered()))));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.documents",
                formatCount(payload.orsaDocumentsRead()))));
        lines.add(CreditLine.blank(18));

        lines.add(CreditLine.section(Component.translatable("screen.frozendawn.ending.section_combat")));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.mobs", payload.mobsKilled())));
        addMobLine(lines, "frostbitten", payload.frostbittenKilled());
        addMobLine(lines, "frostmite", payload.frostmiteKilled());
        addMobLine(lines, "returned", payload.returnedKilled());
        addMobLine(lines, "mimic", payload.mimicKilled());
        addMobLine(lines, "hollow", payload.hollowKilled());
        addMobLine(lines, "other", payload.otherMobsKilled());
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.architect_defeats",
                payload.architectDefeats())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.architect_base_breaches",
                payload.architectBreaches())));
        lines.add(CreditLine.blank(18));

        lines.add(CreditLine.section(Component.translatable("screen.frozendawn.ending.section_lore")));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.terminals", payload.terminalsHacked())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.documents",
                formatCount(payload.orsaDocumentsRead()))));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.conspiracy",
                payload.conspiracyDiscovered()
                        ? Component.translatable("screen.frozendawn.ending.yes")
                        : Component.translatable("screen.frozendawn.ending.no"))));
        lines.add(CreditLine.blank(18));

        lines.add(CreditLine.section(Component.translatable("screen.frozendawn.ending.section_journey")));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.heaters_lit",
                payload.heatersLit())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.fuel_burned",
                formatFuelBurned(payload.fuelBurnedTicks()))));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.nights_underground",
                payload.nightsUnderground())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.closest_call",
                formatHealth(payload.lowestHealth()))));
        lines.add(CreditLine.blank(18));

        lines.add(CreditLine.section(Component.translatable("screen.frozendawn.ending.section_architect")));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.architect_observed",
                payload.architectObserved())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.architect_retreats",
                payload.architectRetreatedToHeal())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.architect_wall_breaches",
                payload.architectWallBreaches())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.architect_killed",
                payload.architectKilled()
                        ? Component.translatable("screen.frozendawn.ending.yes")
                        : Component.translatable("screen.frozendawn.ending.no"))));
        lines.add(CreditLine.blank(18));

        lines.add(CreditLine.section(Component.translatable("screen.frozendawn.ending.section_endgame")));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.rocket_components",
                payload.rocketComponentsCrafted())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.fuel_cells",
                payload.fuelCellsProcessed())));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.satellite_to_launch",
                formatOptionalDays(payload.daysBetweenSatelliteAndLaunch()))));
        lines.add(CreditLine.blank(18));

        lines.add(CreditLine.section(Component.translatable("screen.frozendawn.ending.section_quiet")));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.last_temp",
                formatTemperature(payload.lastTemperatureBeforeLaunch()))));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.final_phase",
                formatPhase(payload.finalPhaseAtLaunch()))));
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.time_played",
                formatTimePlayed(payload.playTimeTicks()))));
        lines.add(CreditLine.blank(34));
        lines.add(CreditLine.center(Component.translatable("screen.frozendawn.ending.dedication")
                .withStyle(ChatFormatting.ITALIC), 0xFFDCEBFF, 0.95F, 28));
        lines.add(CreditLine.blank(56));
        return lines;
    }

    private static void addMobLine(List<CreditLine> lines, String key, int count) {
        lines.add(CreditLine.left(Component.translatable("screen.frozendawn.ending.stat.mob_" + key,
                Math.max(0, count))));
    }

    private static String formatCount(long value) {
        return String.format(java.util.Locale.US, "%,d", Math.max(0L, value));
    }

    private static String formatTemperature(float value) {
        return String.format(java.util.Locale.US, "%.1fC", value);
    }

    private static String formatHealth(float value) {
        return String.format(java.util.Locale.US, "%.1f HP", Math.max(0.0F, value));
    }

    private static String formatFuelBurned(int fuelTicks) {
        float minutes = Math.max(0, fuelTicks) / 1200.0F;
        return String.format(java.util.Locale.US, "%.1f heater-min", minutes);
    }

    private static String formatOptionalDays(int days) {
        return days < 0 ? "Unknown" : String.format(java.util.Locale.US, "%,d days", days);
    }

    private static String formatTimePlayed(int playTimeTicks) {
        float hours = Math.max(0, playTimeTicks) / 20.0F / 3600.0F;
        return String.format(java.util.Locale.US, "%.1f hours", hours);
    }

    private static String formatPhase(int phase) {
        return switch (phase) {
            case 0 -> "Phase 0";
            case 1 -> "Phase 1";
            case 2 -> "Phase 2";
            case 3 -> "Phase 3";
            case 4 -> "Phase 4";
            case 5 -> "Phase 5";
            case 6 -> "Phase 6";
            default -> "Phase " + phase;
        };
    }

    private static String formatPhaseMask(int mask) {
        List<String> phases = new ArrayList<>();
        for (int phase = 0; phase <= 6; phase++) {
            if ((mask & (1 << phase)) != 0) {
                phases.add(String.valueOf(phase));
            }
        }
        return phases.isEmpty() ? "None recorded" : String.join(", ", phases);
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

    private record MarsPlacement(float centerX, float centerY, float face) {
    }

    /** The globe's cells for one screen size: what each shows, minus the turn, and the airglow past the limb. */
    private record EarthGlobeCells(int width, int height, int block, int cols, int rows, double[] latitude,
                                   double[] longitude, float[] shade, float[] glow) {
    }

    private record EarthMap(int width, int height, int[] pixels) {
        int sample(double latitude, double longitude) {
            int x = Mth.floor(Mth.positiveModulo(longitude + 180.0, 360.0) / 360.0 * width);
            int y = Mth.floor(Mth.clamp(90.0 - latitude, 0.0, 179.999) / 180.0 * height);
            return pixels[Mth.clamp(y, 0, height - 1) * width + Mth.clamp(x, 0, width - 1)] & 0xFFFFFF;
        }
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

        void render(GuiGraphics graphics, net.minecraft.client.gui.Font font, LogoRenderer logoRenderer, int screenWidth, float y) {
            if (logo) {
                logoRenderer.renderLogo(graphics, screenWidth, 1.0F, Math.round(y));
                return;
            }
            if (text.getString().isEmpty()) {
                return;
            }
            graphics.pose().pushPose();
            float x = centered ? screenWidth / 2.0F - font.width(text) * scale / 2.0F : screenWidth / 2.0F - 128.0F;
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, text, 0, 0, color, false);
            graphics.pose().popPose();
        }
    }
}
