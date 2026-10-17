package com.frozendawn.labbridge;

import com.google.gson.JsonObject;

/** Actual published integrated-server time, paired with the raw client time and independent player ticks. */
public final class LabClientClock {
    private volatile long gameTime;
    public void publish(long gameTime) { this.gameTime = gameTime; }
    public void sample(JsonObject row, long rawClientWorldTime) {
        row.addProperty("gameTime", gameTime);
        row.addProperty("clientWorldGameTime", rawClientWorldTime);
        row.addProperty("clockSource", "INTEGRATED_SERVER_POST_TICK");
    }
}
