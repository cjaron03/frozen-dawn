package com.frozendawn.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Real players a test puts into the level. A player left in {@link net.minecraft.server.level.ServerLevel#players()}
 * stays there for every later batch on that server: vanilla then despawns any hostile mob more than 128 blocks from
 * it at once, and controllers that react to nearby players see it. A test that removes its players only on its
 * success path leaks them whenever it fails, and the failure then spreads to unrelated tests.
 */
public final class GameTestPlayers {
    private GameTestPlayers() { }

    /** Adds the player to the level and removes it when the test passes or fails, however it ends. */
    public static <P extends ServerPlayer> P join(GameTestHelper helper, P player) {
        var level = helper.getLevel();
        level.addNewPlayer(player);
        helper.testInfo.addListener(new GameTestListener() {
            private void leave() { if (!player.isRemoved()) level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED); }
            public void testStructureLoaded(GameTestInfo info) { }
            public void testPassed(GameTestInfo info, GameTestRunner runner) { leave(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { leave(); }
            public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) { }
        });
        return player;
    }
}
