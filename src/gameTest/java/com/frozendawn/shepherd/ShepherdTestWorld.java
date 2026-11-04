package com.frozendawn.shepherd;

import com.frozendawn.gametest.GameTestReporting;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * She is one per world and every test in a batch runs at once, so each Shepherd test owns a batch. A batch also
 * leaves nothing behind: a stage left at AWAKE would bring her back into the next batch's test area.
 */
final class ShepherdTestWorld {
    private ShepherdTestWorld() {}

    static void fresh(ServerLevel level) {
        GameTestReporting.installReporter(level);
        clear(level);
    }

    static void clear(ServerLevel level) {
        ShepherdSavedData.get(level.getServer()).clearForTests();
    }

    static FakePlayer player(ServerLevel level, BlockPos pos) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "shepherd_user"));
        player.setPos(pos.getCenter());
        return player;
    }
}
