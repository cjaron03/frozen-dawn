package com.frozendawn.client;

import com.frozendawn.network.OpenTowerTerminalPayload;
import com.frozendawn.network.SubmitTowerTerminalPayload;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

final class TowerTerminalInputController {

    private final BlockPos consolePos;

    TowerTerminalInputController(BlockPos consolePos) {
        this.consolePos = consolePos;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button,
                         TowerTerminalLayout layout, TowerTerminalViewModel viewModel) {
        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE) {
            int segmentIndex = layout.blackglassSegmentAt(mouseX, mouseY, viewModel);
            if (segmentIndex >= 0) {
                sendArchiveOpenAction(viewModel, com.frozendawn.terminal.TowerArchive.COMMAND_PAGE + segmentIndex);
                return true;
            }
            int audioButton = layout.archiveAudioButtonAt(mouseX, mouseY, viewModel);
            if (audioButton >= 0) {
                if (audioButton == 0) {
                    int audioSegmentIndex = com.frozendawn.terminal.TowerArchive.blackglassSegmentIndex(viewModel.archivePage());
                    BlackglassAudioPlayer.playSegment(audioSegmentIndex);
                    viewModel.startArchiveAudio();
                } else {
                    BlackglassAudioPlayer.stop();
                    viewModel.stopArchiveAudio();
                }
                return true;
            }
            int entryIndex = layout.archiveEntryAt(mouseX, mouseY, viewModel.archiveDirectoryScroll(), viewModel.archivePageCount());
            if (entryIndex >= 0) {
                sendArchiveOpenAction(viewModel, entryIndex);
                return true;
            }
            return false;
        }
        if (viewModel.state() != OpenTowerTerminalPayload.STATE_ACTIVE || viewModel.board() == null) {
            return false;
        }

        TowerTerminalLayout.TokenHitbox hitbox = layout.hoveredToken(mouseX, mouseY);
        if (hitbox == null) {
            return false;
        }

        PacketDistributor.sendToServer(new SubmitTowerTerminalPayload(
                consolePos,
                viewModel.nonce(),
                hitbox.word() ? SubmitTowerTerminalPayload.ACTION_TYPED_GUESS : SubmitTowerTerminalPayload.ACTION_USE_PAIR,
                hitbox.index(),
                ""
        ));
        viewModel.setTerminalInput("");
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                          TowerTerminalLayout layout, TowerTerminalViewModel viewModel) {
        if (viewModel.state() != OpenTowerTerminalPayload.STATE_ARCHIVE) {
            return false;
        }
        int delta = (int) Math.round(-scrollY * Math.max(10, layout.auditLineHeight * 2));
        if (delta == 0) {
            delta = scrollY > 0 ? -Math.max(10, layout.auditLineHeight * 2) : Math.max(10, layout.auditLineHeight * 2);
        }
        if (layout.isInsideDetailPane(mouseX, mouseY) && layout.detailScrollMax() > 0) {
            viewModel.setArchiveDetailScroll(layout.clampDetailScroll(viewModel.archiveDetailScroll() + delta));
            return true;
        }
        if (layout.isInsideDirectoryPane(mouseX, mouseY) && layout.directoryScrollMax(viewModel.archivePageCount()) > 0) {
            viewModel.setArchiveDirectoryScroll(
                    layout.clampDirectoryScroll(viewModel.archiveDirectoryScroll() + delta, viewModel.archivePageCount()));
            return true;
        }
        return false;
    }

    boolean charTyped(char codePoint, int modifiers,
                      TowerTerminalLayout layout, TowerTerminalViewModel viewModel) {
        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ACTIVE) {
            if (Character.isLetter(codePoint) && viewModel.board() != null
                    && viewModel.terminalInput().length() < viewModel.board().wordLength()) {
                viewModel.setTerminalInput(viewModel.terminalInput() + Character.toUpperCase(codePoint));
            }
            return true;
        }
        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE) {
            if (viewModel.archivePasswordPrompt()) {
                if (Character.isLetterOrDigit(codePoint) && viewModel.archivePasswordInput().length() < 24) {
                    viewModel.setArchivePasswordInput(viewModel.archivePasswordInput() + Character.toUpperCase(codePoint));
                    return true;
                }
                return true;
            }
            if (codePoint == '[') {
                sendArchiveAction(viewModel, SubmitTowerTerminalPayload.ACTION_ARCHIVE_PREVIOUS);
                return true;
            }
            if (codePoint == ']') {
                sendArchiveAction(viewModel, SubmitTowerTerminalPayload.ACTION_ARCHIVE_NEXT);
                return true;
            }
        }
        return false;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers,
                       TowerTerminalLayout layout, TowerTerminalViewModel viewModel) {
        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ACTIVE) {
            if (keyCode == 259) {
                if (!viewModel.terminalInput().isEmpty()) {
                    viewModel.setTerminalInput(viewModel.terminalInput().substring(0, viewModel.terminalInput().length() - 1));
                }
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (!viewModel.terminalInput().isBlank()) {
                    PacketDistributor.sendToServer(new SubmitTowerTerminalPayload(
                            consolePos,
                            viewModel.nonce(),
                            SubmitTowerTerminalPayload.ACTION_TYPED_GUESS,
                            -1,
                            viewModel.terminalInput()
                    ));
                    viewModel.setTerminalInput("");
                }
                return true;
            }
        }
        if (viewModel.state() == OpenTowerTerminalPayload.STATE_ARCHIVE) {
            if (viewModel.archivePasswordPrompt()) {
                if (keyCode == 259) {
                    if (!viewModel.archivePasswordInput().isEmpty()) {
                        viewModel.setArchivePasswordInput(
                                viewModel.archivePasswordInput().substring(0, viewModel.archivePasswordInput().length() - 1));
                    }
                    return true;
                }
                if (keyCode == 257 || keyCode == 335) {
                    sendArchiveAuthAction(viewModel);
                    return true;
                }
                return true;
            }
            if (keyCode == 263 || keyCode == 65) {
                sendArchiveAction(viewModel, SubmitTowerTerminalPayload.ACTION_ARCHIVE_PREVIOUS);
                return true;
            }
            if (keyCode == 262 || keyCode == 68) {
                sendArchiveAction(viewModel, SubmitTowerTerminalPayload.ACTION_ARCHIVE_NEXT);
                return true;
            }
        }
        return false;
    }

    private void sendArchiveAction(TowerTerminalViewModel viewModel, int actionType) {
        PacketDistributor.sendToServer(new SubmitTowerTerminalPayload(
                consolePos,
                viewModel.nonce(),
                actionType,
                0,
                ""
        ));
    }

    private void sendArchiveOpenAction(TowerTerminalViewModel viewModel, int pageIndex) {
        PacketDistributor.sendToServer(new SubmitTowerTerminalPayload(
                consolePos,
                viewModel.nonce(),
                SubmitTowerTerminalPayload.ACTION_ARCHIVE_OPEN_PAGE,
                pageIndex,
                ""
        ));
    }

    private void sendArchiveAuthAction(TowerTerminalViewModel viewModel) {
        PacketDistributor.sendToServer(new SubmitTowerTerminalPayload(
                consolePos,
                viewModel.nonce(),
                SubmitTowerTerminalPayload.ACTION_ARCHIVE_AUTH,
                viewModel.archivePage(),
                viewModel.archivePasswordInput()
        ));
        viewModel.setArchivePasswordInput("");
    }
}
