package com.frozendawn.client;

import com.frozendawn.init.ModSounds;
import com.frozendawn.network.OpenTowerTerminalPayload;
import com.frozendawn.network.SubmitTowerTerminalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class TowerTerminalScreen extends Screen {

    private static final int BLACKGLASS_KEEPALIVE_INTERVAL_TICKS = 20 * 10;

    private final BlockPos consolePos;
    private final TowerTerminalViewModel viewModel = new TowerTerminalViewModel();
    private final TowerTerminalRenderer renderer = new TowerTerminalRenderer();
    private final TowerTerminalInputController inputController;
    private TowerTerminalLayout layout;
    private int blackglassKeepaliveTicks;

    public TowerTerminalScreen(OpenTowerTerminalPayload payload) {
        super(Component.literal("ORSA UPLINK TERMINAL"));
        this.consolePos = payload.pos();
        this.inputController = new TowerTerminalInputController(consolePos);
        applySnapshot(payload);
    }

    public boolean sameConsole(BlockPos pos) {
        return consolePos.equals(pos);
    }

    public void applySnapshot(OpenTowerTerminalPayload payload) {
        int previousAudioSegment = currentBlackglassSegment();
        viewModel.applySnapshot(payload);
        int currentAudioSegment = currentBlackglassSegment();
        if (previousAudioSegment != currentAudioSegment) {
            BlackglassAudioPlayer.stop();
            viewModel.stopArchiveAudio();
        }
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
        layout = TowerTerminalLayout.create(width, height, font, viewModel);
    }

    private void clampArchiveScrolls() {
        if (layout == null) {
            return;
        }
        if (viewModel.state() != OpenTowerTerminalPayload.STATE_ARCHIVE) {
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
        TowerTerminalViewModel.TickResult tickResult = viewModel.tick();
        if (tickResult.playBootSound()) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSounds.TERMINAL_BOOT_ORSA.get(), 1.0f));
        }
        if (tickResult.closeScreen() && minecraft != null) {
            minecraft.setScreen(null);
        }
        syncBlackglassAudioState();
        tickBlackglassKeepalive();
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

    @Override
    public void removed() {
        super.removed();
        BlackglassAudioPlayer.stop();
        viewModel.stopArchiveAudio();
    }

    private void syncBlackglassAudioState() {
        if (layout == null || !layout.isBlackglassArchive(viewModel)) {
            if (viewModel.archiveAudioPlaying()) {
                viewModel.stopArchiveAudio();
            }
            BlackglassAudioPlayer.stop();
            return;
        }

        int segmentIndex = currentBlackglassSegment();
        if (viewModel.archiveAudioPlaying() && !BlackglassAudioPlayer.isPlaying(segmentIndex)) {
            viewModel.stopArchiveAudio();
        }
    }

    private int currentBlackglassSegment() {
        if (!com.frozendawn.terminal.TowerArchive.isBlackglassPage(
                viewModel.archivePage(), viewModel.archivePasswordPrompt())) {
            return -1;
        }
        return com.frozendawn.terminal.TowerArchive.blackglassSegmentIndex(viewModel.archivePage());
    }

    private void tickBlackglassKeepalive() {
        if (viewModel.state() != OpenTowerTerminalPayload.STATE_ARCHIVE || currentBlackglassSegment() < 0) {
            blackglassKeepaliveTicks = 0;
            return;
        }
        blackglassKeepaliveTicks++;
        if (blackglassKeepaliveTicks < BLACKGLASS_KEEPALIVE_INTERVAL_TICKS) {
            return;
        }
        blackglassKeepaliveTicks = 0;
        PacketDistributor.sendToServer(new SubmitTowerTerminalPayload(
                consolePos,
                viewModel.nonce(),
                SubmitTowerTerminalPayload.ACTION_ARCHIVE_KEEPALIVE,
                viewModel.archivePage(),
                ""
        ));
    }

    public static void openOrUpdate(Minecraft minecraft, OpenTowerTerminalPayload payload) {
        if (minecraft.screen instanceof TowerTerminalScreen screen && screen.sameConsole(payload.pos())) {
            screen.applySnapshot(payload);
            return;
        }
        if (payload.state() == OpenTowerTerminalPayload.STATE_COMPLETE) {
            return;
        }
        minecraft.setScreen(new TowerTerminalScreen(payload));
    }
}
