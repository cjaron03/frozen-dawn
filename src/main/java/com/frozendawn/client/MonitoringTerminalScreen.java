package com.frozendawn.client;

import com.frozendawn.init.ModSounds;
import com.frozendawn.network.OpenMonitoringTerminalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class MonitoringTerminalScreen extends Screen {

    private final BlockPos consolePos;
    private final MonitoringTerminalViewModel viewModel = new MonitoringTerminalViewModel();
    private final MonitoringTerminalRenderer renderer = new MonitoringTerminalRenderer();
    private final MonitoringTerminalInputController inputController;
    private MonitoringTerminalLayout layout;

    public MonitoringTerminalScreen(OpenMonitoringTerminalPayload payload) {
        super(Component.literal("ORSA WEATHER INGEST TERMINAL"));
        this.consolePos = payload.pos();
        this.inputController = new MonitoringTerminalInputController(consolePos);
        applySnapshot(payload);
    }

    public boolean sameConsole(BlockPos pos) {
        return consolePos.equals(pos);
    }

    public void applySnapshot(OpenMonitoringTerminalPayload payload) {
        viewModel.applySnapshot(payload);
        if (minecraft != null && minecraft.screen == this) {
            refreshLayout();
            clampArchiveScrolls();
        }
    }

    @Override
    protected void init() {
        super.init();
        refreshLayout();
        clampArchiveScrolls();
    }

    private void refreshLayout() {
        layout = MonitoringTerminalLayout.create(width, height, font, viewModel);
    }

    private void clampArchiveScrolls() {
        if (layout == null) {
            return;
        }
        if (viewModel.state() != OpenMonitoringTerminalPayload.STATE_ARCHIVE) {
            viewModel.setArchiveDirectoryScroll(0);
            viewModel.setArchiveDetailScroll(0);
            return;
        }
        int directoryScroll = layout.clampDirectoryScroll(viewModel.archiveDirectoryScroll(), viewModel.archivePageCount());
        directoryScroll = layout.ensureSelectedArchiveEntryVisible(viewModel.archivePage(), directoryScroll);
        directoryScroll = layout.clampDirectoryScroll(directoryScroll, viewModel.archivePageCount());
        viewModel.setArchiveDirectoryScroll(directoryScroll);
        viewModel.setArchiveDetailScroll(layout.clampDetailScroll(viewModel.archiveDetailScroll()));
    }

    @Override
    public void tick() {
        super.tick();
        MonitoringTerminalViewModel.TickResult tickResult = viewModel.tick();
        if (tickResult.playBootSound()) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSounds.TERMINAL_BOOT_ORSA.get(), 1.0f));
        }
        if (tickResult.closeScreen() && minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (layout == null) {
            refreshLayout();
            clampArchiveScrolls();
        }
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderer.render(graphics, mouseX, mouseY, width, height, font, title, layout, viewModel);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (layout != null && inputController.mouseClicked(mouseX, mouseY, button, layout, viewModel)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout != null && inputController.mouseScrolled(mouseX, mouseY, scrollX, scrollY, layout, viewModel)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (layout != null && inputController.charTyped(codePoint, modifiers, layout, viewModel)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (layout != null && inputController.keyPressed(keyCode, scanCode, modifiers, layout, viewModel)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void openOrUpdate(Minecraft minecraft, OpenMonitoringTerminalPayload payload) {
        if (minecraft.screen instanceof MonitoringTerminalScreen screen && screen.sameConsole(payload.pos())) {
            screen.applySnapshot(payload);
            return;
        }
        minecraft.setScreen(new MonitoringTerminalScreen(payload));
    }
}
