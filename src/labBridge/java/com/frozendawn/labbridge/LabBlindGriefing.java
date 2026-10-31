package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.google.gson.*;
import java.nio.file.Path;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/** The exact ordinary47 stone/slab wall with mobGriefing disabled; real door input only. */
public final class LabBlindGriefing {
    private LabBlindGriefing() {}
    public static boolean geometry(ServerLevel level) {return LabBlindBreach.geometry(level,false,false)&&LabBlindBreach.intact(level);}
    public static boolean fresh(ServerLevel level) {return geometry(level)&&!LabBlindDoor.state(level).get("open").getAsBoolean();}
    public static JsonObject state(ServerLevel level,ArchitectEntity actor) {
        var out=LabBlindBreach.state(level,actor);out.addProperty("mobGriefing",level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));return out;
    }
    public static boolean performed(Path directory,JsonArray decisions) {return LabBlindProtectedWall.performed(directory,decisions,true);}
}
