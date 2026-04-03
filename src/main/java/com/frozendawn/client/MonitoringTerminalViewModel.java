package com.frozendawn.client;

import com.frozendawn.network.OpenMonitoringTerminalPayload;
import com.frozendawn.terminal.MonitoringTerminalPuzzle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class MonitoringTerminalViewModel {

    private static final int BOOT_DURATION = 65;

    private long nonce;
    private MonitoringTerminalPuzzle.Board board;
    private int triesLeft;
    private int state;
    private long removedMask;
    private long usedPairMask;
    private int lockoutTicksRemaining;
    private List<String> auditLines = new ArrayList<>();
    private List<String> archiveBodyLines = new ArrayList<>();
    private String archiveTitle = "";
    private int archivePage;
    private int archivePageCount;
    private boolean archivePasswordPrompt;
    private int closeTicks = -1;
    private int blinkTicks;
    private int bootTicks;
    private boolean bootSoundPlayed;
    private String terminalInput = "";
    private String archivePasswordInput = "";
    private int archiveDirectoryScroll;
    private int archiveDetailScroll;

    void applySnapshot(OpenMonitoringTerminalPayload payload) {
        long previousNonce = nonce;
        int previousArchivePage = archivePage;
        boolean previousPasswordPrompt = archivePasswordPrompt;

        nonce = payload.nonce();
        board = payload.nonce() == 0L ? null : MonitoringTerminalPuzzle.create(payload.nonce());
        triesLeft = payload.triesLeft();
        state = payload.state();
        removedMask = payload.removedMask();
        usedPairMask = payload.usedPairMask();
        lockoutTicksRemaining = payload.lockoutTicksRemaining();
        auditLines = payload.auditLog().isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(payload.auditLog().split("\n")));
        archiveTitle = payload.archiveTitle();
        archiveBodyLines = payload.archiveBody().isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(payload.archiveBody().split("\n")));
        archivePage = payload.archivePage();
        archivePageCount = payload.archivePageCount();
        archivePasswordPrompt = payload.archivePasswordPrompt();

        if (payload.archivePage() != previousArchivePage) {
            archiveDetailScroll = 0;
        }
        closeTicks = state == OpenMonitoringTerminalPayload.STATE_COMPLETE ? 30 : -1;
        if (payload.nonce() != previousNonce || state != OpenMonitoringTerminalPayload.STATE_ACTIVE) {
            terminalInput = "";
        }
        if (payload.archivePage() != previousArchivePage
                || !archivePasswordPrompt
                || archivePasswordPrompt != previousPasswordPrompt) {
            archivePasswordInput = "";
        }
    }

    TickResult tick() {
        blinkTicks++;
        boolean playBootSound = false;
        if (bootTicks < BOOT_DURATION) {
            bootTicks++;
            if (bootTicks == 8 && !bootSoundPlayed) {
                bootSoundPlayed = true;
                playBootSound = true;
            }
        }
        if (state == OpenMonitoringTerminalPayload.STATE_LOCKED_OUT && lockoutTicksRemaining > 0) {
            lockoutTicksRemaining--;
        }
        boolean closeScreen = false;
        if (closeTicks > 0) {
            closeTicks--;
            if (closeTicks == 0) {
                closeTicks = -1;
                closeScreen = true;
            }
        }
        return new TickResult(playBootSound, closeScreen);
    }

    long nonce() {
        return nonce;
    }

    MonitoringTerminalPuzzle.Board board() {
        return board;
    }

    int triesLeft() {
        return triesLeft;
    }

    int state() {
        return state;
    }

    long removedMask() {
        return removedMask;
    }

    long usedPairMask() {
        return usedPairMask;
    }

    int lockoutTicksRemaining() {
        return lockoutTicksRemaining;
    }

    List<String> auditLines() {
        return auditLines;
    }

    List<String> archiveBodyLines() {
        return archiveBodyLines;
    }

    String archiveTitle() {
        return archiveTitle;
    }

    int archivePage() {
        return archivePage;
    }

    int archivePageCount() {
        return archivePageCount;
    }

    boolean archivePasswordPrompt() {
        return archivePasswordPrompt;
    }

    int blinkTicks() {
        return blinkTicks;
    }

    int bootTicks() {
        return bootTicks;
    }

    boolean booting() {
        return bootTicks < BOOT_DURATION;
    }

    int bootDuration() {
        return BOOT_DURATION;
    }

    String terminalInput() {
        return terminalInput;
    }

    void setTerminalInput(String terminalInput) {
        this.terminalInput = terminalInput;
    }

    String archivePasswordInput() {
        return archivePasswordInput;
    }

    void setArchivePasswordInput(String archivePasswordInput) {
        this.archivePasswordInput = archivePasswordInput;
    }

    int archiveDirectoryScroll() {
        return archiveDirectoryScroll;
    }

    void setArchiveDirectoryScroll(int archiveDirectoryScroll) {
        this.archiveDirectoryScroll = archiveDirectoryScroll;
    }

    int archiveDetailScroll() {
        return archiveDetailScroll;
    }

    void setArchiveDetailScroll(int archiveDetailScroll) {
        this.archiveDetailScroll = archiveDetailScroll;
    }

    record TickResult(boolean playBootSound, boolean closeScreen) {
    }
}
