package com.frozendawn.network;

import com.frozendawn.client.ApocalypseClientData;
import com.frozendawn.client.DifficultySelectionScreen;
import com.frozendawn.client.MonitoringTerminalScreen;
import com.frozendawn.client.SanityClientData;
import com.frozendawn.client.TemperatureHud;
import com.frozendawn.client.TowerTerminalScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.Minecraft;

/**
 * Client-side packet handlers. This class is only loaded on the client
 * because it is never directly referenced by server code — only called
 * through lambdas in {@link ModNetworking} that execute client-side.
 */
public final class ClientHandlers {

    private ClientHandlers() {}

    public static void handleApocalypseData(ApocalypseDataPayload payload) {
        ApocalypseClientData.update(payload);
    }

    public static void handleTemperature(TemperaturePayload payload) {
        TemperatureHud.setTemperature(payload.temperature());
    }

    public static void handleSanityStage(SanityStagePayload payload) {
        SanityClientData.setStage(payload.stage());
    }

    public static void handleOpenDifficultySelection() {
        Minecraft.getInstance().setScreen(new DifficultySelectionScreen());
    }

    public static void handleOpenTowerTerminal(OpenTowerTerminalPayload payload) {
        TowerTerminalScreen.openOrUpdate(Minecraft.getInstance(), payload);
    }

    public static void handleOpenMonitoringTerminal(OpenMonitoringTerminalPayload payload) {
        MonitoringTerminalScreen.openOrUpdate(Minecraft.getInstance(), payload);
    }

    public static void handleOpenMeteorologistJournal(OpenMeteorologistJournalPayload payload) {
        BookViewScreen.BookAccess access = BookViewScreen.BookAccess.fromItem(payload.stack());
        if (access != null) {
            Minecraft.getInstance().setScreen(new BookViewScreen(access));
        }
    }
}
