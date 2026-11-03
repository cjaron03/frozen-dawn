package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lab only (-Dfrozendawn.labCommands): a suspected home and a cleared cooldown, so a §9.4a Pyro can be called to a
 * prepared base by eye. Production never registers the commands that reach this.
 */
public final class PyroLab {
    private static final UUID WITNESS = new UUID(0x5C81BEL, 3);

    private PyroLab() { }

    /** One covered shelter sample where the command runs: Maeve now suspects the player lives here, with no heat known. */
    public static String seedHome(ServerPlayer player, BlockPos at) {
        var data = DirectorRuntime.current(player.server).data;
        if (data.store() == null || data.pyro() == null) return "Maeve is not awake.";
        String dimension = player.level().dimension().location().toString();
        var world = data.store().observeContact(player.getUUID(), WITNESS, dimension, player.server.overworld().getGameTime());
        world.shelter(dimension, at, player.server.overworld().getGameTime());
        data.setDirty();
        return "Suspected home: " + world.center(dimension).toShortString() + ".";
    }

    /** The active Pyro, if loaded, vanishes now instead of finishing its watch. Its looks so far stay uploaded. */
    public static String vanish(MinecraftServer server) {
        var data = DirectorRuntime.current(server).data;
        if (data.pyro() == null) return "Maeve is not awake.";
        var claim = data.pyro().active;
        if (claim == null) return "No Pyro is out.";
        for (var level : server.getAllLevels()) {
            if (level.getEntity(claim.pyro()) instanceof ArchitectEntity pyro)
                return pyro.vanishPyroNow() ? "The Pyro vanishes." : "The Pyro is already vanishing.";
        }
        return "The Pyro is not loaded.";
    }

    /** Clears the cooldown only; an active Pyro is left as it is. */
    public static String clearCooldown(MinecraftServer server) {
        var data = DirectorRuntime.current(server).data;
        if (data.pyro() == null) return "Maeve is not awake.";
        if (data.pyro().active != null) return "A Pyro is still out.";
        data.pyro().lastEnded = -1;
        data.setDirty();
        return "Pyro cooldown cleared.";
    }
}
