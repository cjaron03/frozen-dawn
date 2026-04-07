package com.frozendawn.block;

final class TerminalArchiveState {

    private int pageIndex;
    private boolean unlocked;
    private String authStatus = "";

    void resetForPuzzleSession() {
        reset();
    }

    void resetForArchiveSession() {
        reset();
    }

    int pageIndex() {
        return pageIndex;
    }

    void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    boolean unlocked() {
        return unlocked;
    }

    void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    String authStatus() {
        return authStatus;
    }

    void setAuthStatus(String authStatus) {
        this.authStatus = authStatus == null ? "" : authStatus;
    }

    void clearAuthStatus() {
        authStatus = "";
    }

    private void reset() {
        pageIndex = 0;
        unlocked = false;
        authStatus = "";
    }
}
