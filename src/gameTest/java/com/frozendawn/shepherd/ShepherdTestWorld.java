package com.frozendawn.shepherd;

import com.frozendawn.gametest.GameTestPlayers;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.init.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
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
        ShepherdTalk.clearForTests();
        ShepherdSavedData.get(level.getServer()).clearForTests();
        // A body left in a test area that has stopped ticking would never get to remove itself.
        for (var body : level.getEntities(ModEntities.CS1_UNIT.get(), e -> true)) body.discard();
    }

    /** A test can start before its chunk ticks entities, and she counts ticks. */
    static void whenTicking(GameTestHelper h, Runnable test) {
        h.startSequence().thenWaitUntil(() -> {
            var level = h.getLevel();
            var pos = h.absolutePos(new BlockPos(10, 4, 10));
            h.assertTrue(level.isPositionEntityTicking(pos) && level.areEntitiesLoaded(ChunkPos.asLong(pos)), "Waiting for her chunk to tick");
        }).thenExecute(test);
    }

    static FakePlayer player(ServerLevel level, BlockPos pos) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "shepherd_user"));
        player.setPos(pos.getCenter());
        return player;
    }

    /** In the level for real, so she can find them. The test framework takes them out again when the test ends. */
    static ServerPlayer onlinePlayer(GameTestHelper h, Vec3 at) {
        var level = h.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), "shepherd_follower");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new FakePlayer(level, profile).connection;
        player.setPos(at);
        player.setGameMode(GameType.CREATIVE);
        GameTestPlayers.join(h, player);
        return player;
    }
}
