package com.frozendawn.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.frozendawn.config.FrozenDawnClientConfig;
import com.frozendawn.lore.ThaevenRecordId;
import com.frozendawn.network.ThaevenLoreViewedPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Two-page raw/reconstruction archive with non-blocking ink revelation. */
public final class ThaevenLoreScreen extends Screen {
    private static final int REVEAL_TICKS = 130;
    private final boolean rawOnly;
    private final Map<ThaevenRecordId, Integer> revealAge =
            new EnumMap<>(ThaevenRecordId.class);
    private final Map<ThaevenRecordId, Integer> reportedRevision =
            new EnumMap<>(ThaevenRecordId.class);
    private ThaevenRecordId record;
    private ThaevenRecordDefinition definition;
    private int page;
    private Button previousRecord;
    private Button nextRecord;

    public ThaevenLoreScreen(int focusRecord, boolean rawOnly) {
        super(Component.translatable("screen.frozendawn.thaeven_archive"));
        this.rawOnly = rawOnly;
        this.record = chooseInitialRecord(focusRecord);
        this.definition = ThaevenRecordDefinition.load(record);
    }

    @Override
    protected void init() {
        previousRecord = addRenderableWidget(Button.builder(
                Component.literal("<"), button -> changeRecord(-1))
                .bounds(width / 2 - 176, 4, 28, 20).build());
        previousRecord.setTooltip(Tooltip.create(Component.translatable(
                "screen.frozendawn.thaeven_archive.previous_record")));
        nextRecord = addRenderableWidget(Button.builder(
                Component.literal(">"), button -> changeRecord(1))
                .bounds(width / 2 + 148, 4, 28, 20).build());
        nextRecord.setTooltip(Tooltip.create(Component.translatable(
                "screen.frozendawn.thaeven_archive.next_record")));
        int y = height - 28;
        Button previousPage = addRenderableWidget(Button.builder(
                Component.literal("<"), button -> changePage(-1))
                .bounds(12, y, 28, 20).build());
        previousPage.setTooltip(Tooltip.create(Component.translatable(
                "screen.frozendawn.thaeven_archive.previous_page")));
        Button nextPage = addRenderableWidget(Button.builder(
                Component.literal(">"), button -> changePage(1))
                .bounds(width - 40, y, 28, 20).build());
        nextPage.setTooltip(Tooltip.create(Component.translatable(
                "screen.frozendawn.thaeven_archive.next_page")));
        updateRecordButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (rawOnly || !ThaevenLoreClientState.has(record)) {
            return;
        }
        int targetRevision = ThaevenLoreClientState.currentRevision(record);
        if (ThaevenLoreClientState.seenRevision(record) >= targetRevision) {
            return;
        }
        int age = revealAge.merge(record, 1, Integer::sum);
        if (FrozenDawnClientConfig.REDUCED_THAEVEN_INK_ANIMATION.get()
                || age >= REVEAL_TICKS) {
            revealAge.put(record, REVEAL_TICKS);
            if (reportedRevision.getOrDefault(record, -1) != targetRevision) {
                reportedRevision.put(record, targetRevision);
                PacketDistributor.sendToServer(new ThaevenLoreViewedPayload(
                        record.ordinal(), targetRevision));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                       float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int margin = Math.max(12, width / 18);
        int top = 24;
        int bottom = height - 38;
        int panelWidth = width - margin * 2;
        List<DisplayPage> pages = displayPages();
        DisplayPage displayPage = pages.get(Math.floorMod(page, pages.size()));
        Component heading = Component.translatable(rawOnly
                ? "screen.frozendawn.thaeven_archive.raw"
                : "screen.frozendawn.thaeven_archive.reconstruction");
        renderPage(graphics, margin, top, panelWidth, bottom - top,
                displayedLines(displayPage, panelWidth), !rawOnly, heading);
        graphics.drawCenteredString(font,
                Component.literal(definition.title()).withStyle(
                        ChatFormatting.DARK_GRAY), width / 2, 9, 0xFFE7E1D2);
        graphics.drawCenteredString(font,
                Component.translatable(
                        "screen.frozendawn.thaeven_archive.page",
                        Math.floorMod(page, pages.size()) + 1, pages.size()),
                width / 2,
                height - 22, 0xFF90897B);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        // Vanilla's deferred menu blur softens everything drawn by this screen.
        // A flat dim keeps the archive and Minecraft font pixel-sharp.
        graphics.fill(0, 0, width, height, 0x78000000);
    }

    private void renderPage(GuiGraphics graphics, int x, int y, int w, int h,
                            List<net.minecraft.util.FormattedCharSequence> lines,
                            boolean translated,
                            Component heading) {
        graphics.fill(x, y, x + w, y + h, 0xFFF0E9D7);
        graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFDBD0B7);
        graphics.drawString(font, heading, x + 12, y + 10, 0xFF625947, false);
        boolean morphing = translated
                && inkState() != ThaevenInkState.RESOLVED
                && inkState() != ThaevenInkState.UNRESOLVED;
        float progress = revealProgress();
        int age = revealAge.getOrDefault(record, 0);
        for (int i = 0; i < lines.size(); i++) {
            int drawX = x + 12;
            int drawY = y + 27 + i * 10;
            if (!morphing) {
                graphics.drawString(font, lines.get(i), drawX, drawY,
                        translated ? 0xFF28231D : 0xFF4B5260, false);
                continue;
            }
            float unrest = (float) (1.0D - Mth.smoothstep(progress));
            float slowWarp = Mth.sin(age * 0.105F + i * 1.37F);
            int warpX = Math.round((slowWarp * 3.0F
                    + Mth.sin(age * 0.31F + i * 1.73F) * 1.5F) * unrest);
            int warpY = Math.round(Mth.sin(age * 0.16F + i * 0.81F)
                    * 1.25F * unrest);
            int ghostAlpha = Math.round(62.0F * unrest);
            int coldGhost = ghostAlpha << 24 | 0x354850;
            int warmGhost = ghostAlpha << 24 | 0x6A4935;
            graphics.drawString(font, lines.get(i), drawX + warpX - 1,
                    drawY + warpY, coldGhost, false);
            graphics.drawString(font, lines.get(i), drawX - warpX + 1,
                    drawY - warpY, warmGhost, false);
            graphics.drawString(font, lines.get(i), drawX + warpX / 3,
                    drawY, 0xFF28231D, false);
        }
    }

    private String translatedText(int translatedPage) {
        if (!ThaevenLoreClientState.has(record)) {
            return Component.translatable(
                    "screen.frozendawn.thaeven_archive.undiscovered").getString();
        }
        int revision = ThaevenLoreClientState.currentRevision(record);
        return isRewetting()
                ? definition.translatedPageMorphing(translatedPage, revision,
                revealProgress())
                : definition.translatedPage(translatedPage, revision);
    }

    private boolean isRewetting() {
        ThaevenInkState state = inkState();
        return state == ThaevenInkState.REWETTING
                || state == ThaevenInkState.BLEEDING;
    }

    private ThaevenInkState inkState() {
        if (rawOnly || !ThaevenLoreClientState.has(record)) {
            return ThaevenInkState.UNRESOLVED;
        }
        if (ThaevenLoreClientState.seenRevision(record)
                >= ThaevenLoreClientState.currentRevision(record)
                || FrozenDawnClientConfig.REDUCED_THAEVEN_INK_ANIMATION.get()
                || revealAge.getOrDefault(record, 0) >= REVEAL_TICKS) {
            return ThaevenInkState.RESOLVED;
        }
        return revealAge.getOrDefault(record, 0) < 9
                ? ThaevenInkState.REWETTING : ThaevenInkState.BLEEDING;
    }

    private float revealProgress() {
        return Mth.clamp(revealAge.getOrDefault(record, 0)
                / (float) REVEAL_TICKS, 0.0F, 1.0F);
    }

    private void changePage(int delta) {
        page = Math.floorMod(page + delta, pageCount());
    }

    private int pageCount() {
        return displayPages().size();
    }

    private List<DisplayPage> displayPages() {
        int margin = Math.max(12, width / 18);
        int contentWidth = Math.max(1, width - margin * 2 - 24);
        int contentHeight = Math.max(1, height - 38 - 24);
        int maxLines = Math.max(1, (contentHeight - 38) / 10);
        int logicalPages = rawOnly ? definition.rawPages().size()
                : definition.translatedPages().size();
        List<DisplayPage> result = new ArrayList<>();
        for (int logicalPage = 0; logicalPage < logicalPages; logicalPage++) {
            String finalText = rawOnly
                    ? definition.rawPages().get(logicalPage)
                    : definition.translatedPage(logicalPage,
                    ThaevenLoreClientState.currentRevision(record));
            int lineCount = Math.max(1, font.split(
                    Component.literal(finalText), contentWidth).size());
            for (int firstLine = 0; firstLine < lineCount;
                 firstLine += maxLines) {
                result.add(new DisplayPage(logicalPage, firstLine, maxLines));
            }
        }
        if (result.isEmpty()) {
            result.add(new DisplayPage(0, 0, maxLines));
        }
        return result;
    }

    private List<net.minecraft.util.FormattedCharSequence> displayedLines(
            DisplayPage displayPage, int panelWidth) {
        String text = rawOnly
                ? definition.rawPages().get(Math.floorMod(
                displayPage.logicalPage(), definition.rawPages().size()))
                : translatedText(displayPage.logicalPage());
        List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(
                Component.literal(text), Math.max(1, panelWidth - 24));
        int from = Math.min(displayPage.firstLine(), wrapped.size());
        int to = Math.min(wrapped.size(), from + displayPage.maxLines());
        return wrapped.subList(from, to);
    }

    private void changeRecord(int delta) {
        if (rawOnly) {
            return;
        }
        ThaevenRecordId[] values = ThaevenRecordId.values();
        for (int attempt = 1; attempt <= values.length; attempt++) {
            ThaevenRecordId candidate = values[Math.floorMod(
                    record.ordinal() + delta * attempt, values.length)];
            if (ThaevenLoreClientState.has(candidate)) {
                record = candidate;
                definition = ThaevenRecordDefinition.load(record);
                page = 0;
                updateRecordButtons();
                return;
            }
        }
    }

    private void updateRecordButtons() {
        if (previousRecord != null) {
            previousRecord.visible = !rawOnly;
            nextRecord.visible = !rawOnly;
        }
    }

    private ThaevenRecordId chooseInitialRecord(int focus) {
        ThaevenRecordId[] values = ThaevenRecordId.values();
        if (focus >= 0 && focus < values.length) {
            return values[focus];
        }
        for (ThaevenRecordId candidate : values) {
            if (ThaevenLoreClientState.has(candidate)) {
                return candidate;
            }
        }
        return ThaevenRecordId.VEL_AN;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            changePage(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            changePage(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record DisplayPage(int logicalPage, int firstLine, int maxLines) {
    }
}
