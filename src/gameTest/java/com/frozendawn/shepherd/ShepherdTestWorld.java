package com.frozendawn.shepherd;

import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.gametest.GameTestPlayers;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.init.ModEntities;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.world.BlastPitPlacement;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * She is one per world and every test in a batch runs at once, so each Shepherd test owns a batch. A batch also
 * leaves nothing behind: a stage left at AWAKE would bring her back into the next batch's test area.
 */
final class ShepherdTestWorld {
    private static final long NOON = 6000;

    private ShepherdTestWorld() {}

    static void fresh(ServerLevel level) {
        GameTestReporting.installReporter(level);
        clear(level);
        // A clear night or a warm room is a deed she would score in a test about something else.
        level.setDayTime(NOON);
    }

    static void clear(ServerLevel level) {
        ShepherdTalk.clearForTests();
        ShepherdBoot.clearForTests();
        ShepherdPrompt.clearForTests();
        ShepherdReactions.clearForTests();
        ShepherdSavedData.get(level.getServer()).clearForTests();
        MillerPattern.reset(level.getServer());
        // A body left in a test area that has stopped ticking would never get to remove itself.
        for (var body : level.getEntities(ModEntities.CS1_UNIT.get(), e -> true)) body.discard();
    }

    /** The world's pit, as it stood before a test moved it. */
    private static BlockPos pitTarget, pitBuilt;
    private static boolean pitPlaced;

    static void keepThePit(ServerLevel level) {
        var state = OrsaStructureState.get(level.getServer());
        pitTarget = state.getBlastPitTargetPos();
        pitBuilt = state.getBlastPitPos();
        pitPlaced = state.isBlastPitPlaced();
    }

    static void restoreThePit(ServerLevel level) {
        var state = OrsaStructureState.get(level.getServer());
        state.setBlastPitTargetPos(pitTarget);
        state.setBlastPitPos(pitBuilt);
        state.setBlastPitPlaced(pitPlaced);
    }

    /** A pit standing here, as far as anyone can tell. Marked built, so the real placement leaves the test area alone. */
    static void pitAt(ServerLevel level, BlockPos pos) {
        var state = OrsaStructureState.get(level.getServer());
        state.setBlastPitTargetPos(pos);
        state.setBlastPitPos(pos);
        state.setBlastPitPlaced(true);
    }

    static BlockPos pit(ServerLevel level) {
        return BlastPitPlacement.ensureBlastPitResolved(level);
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

    /** A right-click on a dock with what is in the main hand. */
    static void use(GameTestHelper h, BlockPos dock, ServerPlayer player) {
        var level = h.getLevel();
        var hit = new BlockHitResult(dock.getCenter(), Direction.UP, dock, false);
        level.getBlockState(dock).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    }

    /** A right-click on a dock with nothing in hand. */
    static void touch(GameTestHelper h, BlockPos dock, ServerPlayer player) {
        h.getLevel().getBlockState(dock).useWithoutItem(h.getLevel(), player, new BlockHitResult(dock.getCenter(), Direction.UP, dock, false));
    }

    /** Seats what is in hand and answers the choice with Boot. */
    static void boot(GameTestHelper h, BlockPos dock, ServerPlayer player) {
        use(h, dock, player);
        ShepherdBoot.answer(player, true);
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
