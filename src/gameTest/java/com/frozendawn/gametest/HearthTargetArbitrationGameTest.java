package com.frozendawn.gametest;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.homo.HearthArchitectPolicy;
import com.frozendawn.homo.HearthPopulationPolicy;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.init.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Covers what happens to the Hearth Architect when more than one player is in range at once.
 *
 * <p>Every other automated test in this repository exercises the Architect against zero or one
 * player. The game test server runs with no players connected at all, so the entire
 * player-targeting half of {@code ArchitectHearthResidentController} is currently unexecuted by
 * the gate. That matters because single-candidate selection is correct by construction — a
 * "pick the nearest" reduction over one element cannot pick wrong — so the only way to reach the
 * arbitration logic is to put two candidates in front of it and make the nearest one change.
 *
 * <p>The test therefore stands up real players. NeoForge's {@link FakePlayer} is a real
 * {@code ServerPlayer} with a no-op tick and a no-op network handler, which is exactly what a
 * head-less test wants: it will not move on its own and it cannot crash on an unsent packet. It
 * has to be inserted with {@link ServerLevel#addNewPlayer(ServerPlayer)} rather than
 * {@code FakePlayerFactory}, because the factory only caches the instance in a static map and
 * never puts it into {@link ServerLevel#players()} — and {@code players()} is precisely the list
 * the controller streams over. A test built on the factory would pass while asserting nothing.
 *
 * <p>Both phases live in one test method on purpose. Game tests inside a batch run concurrently
 * on platforms only a few blocks apart, so two separate player-spawning tests could see each
 * other's players and pick a foreign target. One method means the fake players in this world are
 * always this test's own.
 *
 * <p>Setup deliberately stays at the {@code PLANNED} stage: it never resolves a surface and never
 * advances maturation. That keeps {@code HearthReconciliationGameTest}'s
 * {@code assertFalse(surfaceResolved())} true whichever order the two tests run in, and it also
 * leaves {@code HearthPopulationManager} and {@code HearthArchitectManager} inert, since both
 * refuse to spawn residents for a hearth that is not INTACT with a placed structure. Nothing else
 * in the world will produce an Architect that could confuse the assertions.
 */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public class HearthTargetArbitrationGameTest {

    /** Layer the test lays its own floor on; {@code GameTestTemplates.placeFloor} only spans 9. */
    private static final int FLOOR_Y = 0;

    /** Feet height for every entity in the test, one block above the floor slab. */
    private static final int STAND_Y = 1;

    /** Architect corner. Leaves room for a 15-block arm along both +X and +Z inside the 21 box. */
    private static final int ARCHITECT_X = 3;
    private static final int ARCHITECT_Z = 3;

    /** Sole-player distance for the control phase; comfortably mid-window. */
    private static final int CONTROL_RADIUS = 14;

    /** The two radii the pair alternates between. Both must stay inside the assessment window. */
    private static final int NEAR_RADIUS = 13;
    private static final int FAR_RADIUS = 15;

    /**
     * How often the pair trades places. Must be shorter than
     * {@link HearthArchitectPolicy#ASSESSMENT_TICKS} or the swap would land after an assessment
     * had already completed and the test would prove nothing.
     */
    private static final int SWAP_TICKS = 30;

    /**
     * Budget for the control phase: the Architect's 40-tick spawn warmup in {@code aiStep}, plus a
     * full assessment, plus slack. The phase always burns the whole budget — the player is
     * released early, the clock is not.
     */
    private static final int CONTROL_TICKS = 130;

    /** Ten swaps' worth of arbitration, far more than one assessment needs. */
    private static final int ARBITRATION_TICKS = 300;

    /**
     * A resident Architect must still finish an assessment when two players keep swapping which
     * one is nearest.
     *
     * <p>Phase one is the control and runs first so a red result is never ambiguous: one player,
     * held still in the assessment window. If that assertion is the one that fires, the rig itself
     * is broken — the Architect never reached the assessment code at all — and phase two's result
     * means nothing.
     *
     * <p>Phase two is the real subject. Two fresh players alternate between {@link #NEAR_RADIUS}
     * and {@link #FAR_RADIUS} every {@link #SWAP_TICKS}, so the controller's nearest-player
     * reduction returns a different player before either one can hold still long enough. Both are
     * always inside the assessment window and always in line of sight, so nothing but the choice
     * of target is changing. The Architect is expected to commit to whichever player it locked on
     * to and finish; if it re-arms its counter every time the nearest player changes, neither
     * player is ever assessed and no amount of standing there will help.
     */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 900)
    public static void architectFinishesAnAssessmentWhileTwoPlayersTradePlaces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        assertGeometryMatchesPolicy(helper);
        placeFloor(helper);

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = orCreateMajorHearth(helper, data, level);
        UUID hearthId = hearth.id();

        BlockPos home = helper.absolutePos(new BlockPos(ARCHITECT_X, STAND_Y, ARCHITECT_Z));
        Vec3 anchor = new Vec3(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D);
        assertNoRealPlayerInWatchRange(helper, level, anchor);

        ArchitectEntity architect = helper.spawn(
                ModEntities.ARCHITECT.get(), new BlockPos(ARCHITECT_X, STAND_Y, ARCHITECT_Z));
        architect.bindToHearthPopulation(hearthId, home, 0);
        helper.assertTrue(architect.isHearthPopulationResident(),
                "the Architect did not take the hearth-population binding, so the resident"
                        + " controller will never run | hearth=" + hearthId
                        + " home=" + home + " boundId=" + architect.getHearthPopulationId());

        // Mutable one-slot holders: the sequence callbacks below run on later ticks and have to
        // hand state to each other, which a plain local cannot do from inside a lambda.
        ServerPlayer[] slots = new ServerPlayer[3];
        boolean[] controlAssessed = new boolean[1];
        int[] arbitrationTick = new int[1];

        slots[0] = join(level, "fd_arb_solo", onX(anchor, CONTROL_RADIUS));

        helper.startSequence()
                // Control phase. The player is dropped the instant the assessment lands so the
                // controller never gets a tick where it would open a Thaeven transmission, which
                // would leave a session keyed to a discarded player in a static map.
                .thenExecuteFor(CONTROL_TICKS, () -> {
                    hold(architect, anchor);
                    ServerPlayer solo = slots[0];
                    if (solo == null) {
                        return;
                    }
                    if (assessmentComplete(data, hearthId, solo.getUUID())) {
                        controlAssessed[0] = true;
                        release(solo);
                        slots[0] = null;
                        return;
                    }
                    hold(solo, onX(anchor, CONTROL_RADIUS));
                })
                .thenExecute(() -> {
                    // Release before asserting: a failing assertion aborts the sequence, and a
                    // leaked fake player would stay in level.players() for every later test.
                    if (slots[0] != null) {
                        release(slots[0]);
                        slots[0] = null;
                    }
                    helper.assertTrue(controlAssessed[0],
                            "control phase failed: a single motionless player at " + CONTROL_RADIUS
                                    + " blocks was never assessed in " + CONTROL_TICKS + " ticks."
                                    + " The arbitration phase below cannot be trusted until this"
                                    + " passes — the Architect is not reaching the assessment code"
                                    + " at all. | hearth=" + hearthId
                                    + " architectTicks=" + architect.tickCount
                                    + " alive=" + architect.isAlive()
                                    + " resident=" + architect.isHearthPopulationResident()
                                    + " architectPos=" + architect.position()
                                    + " home=" + home);

                    slots[1] = join(level, "fd_arb_one", onX(anchor, NEAR_RADIUS));
                    slots[2] = join(level, "fd_arb_two", onZ(anchor, FAR_RADIUS));
                })
                // Arbitration phase: swap which of the two is nearest, faster than an assessment
                // can complete.
                .thenExecuteFor(ARBITRATION_TICKS, () -> {
                    if (slots[1] == null || slots[2] == null) {
                        return;
                    }
                    boolean firstIsNear = (arbitrationTick[0]++ / SWAP_TICKS) % 2 == 0;
                    hold(architect, anchor);
                    hold(slots[1], onX(anchor, firstIsNear ? NEAR_RADIUS : FAR_RADIUS));
                    hold(slots[2], onZ(anchor, firstIsNear ? FAR_RADIUS : NEAR_RADIUS));
                })
                .thenExecute(() -> {
                    boolean first = assessmentComplete(data, hearthId, slots[1].getUUID());
                    boolean second = assessmentComplete(data, hearthId, slots[2].getUUID());
                    release(slots[1]);
                    release(slots[2]);
                    helper.assertTrue(first || second,
                            "two players swapping places every " + SWAP_TICKS + " ticks starved the"
                                    + " Architect: after " + ARBITRATION_TICKS + " ticks with both"
                                    + " of them inside the assessment window the whole time,"
                                    + " neither was assessed. The Architect is re-arming its"
                                    + " assessment counter every time the nearest player changes"
                                    + " instead of committing to the target it started on."
                                    + " | hearth=" + hearthId
                                    + " swapTicks=" + SWAP_TICKS
                                    + " assessmentTicksRequired=" + HearthArchitectPolicy.ASSESSMENT_TICKS
                                    + " nearRadius=" + NEAR_RADIUS + " farRadius=" + FAR_RADIUS
                                    + " playerOne=" + slots[1].getUUID() + " assessed=" + first
                                    + " playerTwo=" + slots[2].getUUID() + " assessed=" + second
                                    + " architectPos=" + architect.position()
                                    + " architectTicks=" + architect.tickCount
                                    + " alive=" + architect.isAlive()
                                    + " resident=" + architect.isHearthPopulationResident());
                })
                .thenSucceed();
    }

    /**
     * Fails loudly if a policy change has moved the assessment window out from under the fixed
     * radii above, so a future tuning pass gets a clear message instead of a silent no-op test.
     */
    private static void assertGeometryMatchesPolicy(GameTestHelper helper) {
        for (int radius : new int[] {CONTROL_RADIUS, NEAR_RADIUS, FAR_RADIUS}) {
            double squared = (double) radius * radius;
            helper.assertTrue(HearthArchitectPolicy.isAssessmentDistance(squared),
                    "test radius " + radius + " no longer sits inside the assessment window ["
                            + HearthArchitectPolicy.ASSESSMENT_MIN_DISTANCE + ", "
                            + HearthArchitectPolicy.ASSESSMENT_MAX_DISTANCE + "]");
            helper.assertTrue(radius < HearthPopulationPolicy.WATCH_DISTANCE,
                    "test radius " + radius + " is outside the resident watch distance "
                            + HearthPopulationPolicy.WATCH_DISTANCE
                            + ", so the Architect would never see the player at all");
        }
        helper.assertTrue(SWAP_TICKS < HearthArchitectPolicy.ASSESSMENT_TICKS,
                "swap interval " + SWAP_TICKS + " is no longer shorter than the "
                        + HearthArchitectPolicy.ASSESSMENT_TICKS
                        + " ticks an assessment needs, so the swap could not interrupt one");
        helper.assertTrue(ARCHITECT_X + FAR_RADIUS < GameTestTemplates.EMPTY_LARGE_WIDTH
                        && ARCHITECT_Z + FAR_RADIUS < GameTestTemplates.EMPTY_LARGE_WIDTH,
                "the far player would stand outside the " + GameTestTemplates.EMPTY_LARGE_WIDTH
                        + "-wide test platform");
    }

    /**
     * Lays stone across the full platform. {@code GameTestTemplates.placeFloor} only covers the
     * 9-wide template, and an entity dropped into the 21-wide one would fall out of the world.
     */
    private static void placeFloor(GameTestHelper helper) {
        for (int x = 0; x < GameTestTemplates.EMPTY_LARGE_WIDTH; x++) {
            for (int z = 0; z < GameTestTemplates.EMPTY_LARGE_WIDTH; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
            }
        }
    }

    /**
     * Returns the major hearth, creating the selection plan only if no run has made one yet.
     *
     * <p>Deliberately stops at {@code PLANNED}: the assessment path only needs the record to
     * exist, and leaving the surface unresolved is what keeps this test from disturbing
     * {@code HearthReconciliationGameTest}, which asserts the surface is still unresolved when it
     * starts.
     */
    private static ReturnedHearthSavedData.HearthRecord orCreateMajorHearth(
            GameTestHelper helper, ReturnedHearthSavedData data, ServerLevel level) {
        if (data.hearth(HearthSelectionPolicy.HearthType.MAJOR).isEmpty()) {
            data.applySelectionPlan(
                    HearthSelectionPolicy.createPlan(
                            level.getSeed(), helper.absolutePos(BlockPos.ZERO)),
                    level.getGameTime());
        }
        ReturnedHearthSavedData.HearthRecord hearth =
                data.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        helper.assertTrue(hearth != null,
                "no major hearth record exists, so the Architect has nothing to record an"
                        + " assessment against");
        return hearth;
    }

    /**
     * Fails up front when a real player is close enough to steal the Architect's attention.
     *
     * <p>This test runs in a live client as well as headless, and the resident controller commits
     * to a single assessment target and deliberately refuses to flip to a closer one. A developer
     * standing inside {@link HearthPopulationPolicy#WATCH_DISTANCE} of the structure therefore
     * wins the commitment before this test has placed any of its own players, and the control
     * phase below simply times out reporting that its player was never assessed — which is true
     * but says nothing about the code under test. Catching it here turns 130 confusing ticks into
     * one actionable message. Spectators are excluded because the controller excludes them too.
     */
    private static void assertNoRealPlayerInWatchRange(GameTestHelper helper, ServerLevel level,
                                                       Vec3 anchor) {
        double watchRangeSquared = (double) HearthPopulationPolicy.WATCH_DISTANCE
                * HearthPopulationPolicy.WATCH_DISTANCE;
        ServerPlayer intruder = null;
        for (ServerPlayer player : level.players()) {
            if (player instanceof FakePlayer || player.isSpectator()) {
                continue;
            }
            if (player.position().distanceToSqr(anchor) <= watchRangeSquared) {
                intruder = player;
                break;
            }
        }
        helper.assertTrue(intruder == null,
                "a real player is standing inside the Architect's "
                        + HearthPopulationPolicy.WATCH_DISTANCE + "-block watch radius. The"
                        + " resident controller commits to one assessment target and holds it, so"
                        + " that player wins the commitment and this test's own players are never"
                        + " assessed. Move away from the test structure or run"
                        + " /gamemode spectator, then run the test again. | intruder="
                        + (intruder == null ? "none" : intruder.getGameProfile().getName())
                        + " distance=" + (intruder == null ? -1L
                                : Math.round(Math.sqrt(intruder.position().distanceToSqr(anchor))))
                        + " anchor=" + anchor);
    }

    /**
     * Puts a fake player into the level for real. {@code addNewPlayer} is the narrow path that
     * lands the entity in {@link ServerLevel#players()} without any of the login, packet or
     * player-list machinery a mock server player would drag in.
     */
    private static FakePlayer join(ServerLevel level, String name, Vec3 pos) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addNewPlayer(player);
        player.setDeltaMovement(Vec3.ZERO);
        return player;
    }

    /**
     * Re-pins an entity every tick.
     *
     * <p>The resident controller is driven from {@code aiStep}, so {@code setNoAi(true)} would
     * switch off the very thing under test. Holding position instead lets the controller run
     * normally while keeping the distances the test depends on from drifting as the Architect
     * paths around.
     */
    private static void hold(Entity entity, Vec3 pos) {
        entity.moveTo(pos.x, pos.y, pos.z, entity.getYRot(), entity.getXRot());
        entity.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * Removes a fake player from the world once the test is done with it.
     *
     * <p>Completing an assessment makes the Architect open a Thaeven transmission against the
     * player on that same tick, so the session cannot be headed off from here. It does not need to
     * be: {@code HearthTransmissionManager.tick} drops any session whose player is absent from the
     * server's {@code PlayerList}, and a fake player inserted straight into the level is never in
     * that list. The session is gone on the next tick without the test touching manager statics
     * that a concurrently running test might be relying on.
     */
    private static void release(Entity player) {
        player.discard();
    }

    private static Vec3 onX(Vec3 anchor, int radius) {
        return anchor.add(radius, 0.0D, 0.0D);
    }

    private static Vec3 onZ(Vec3 anchor, int radius) {
        return anchor.add(0.0D, 0.0D, radius);
    }

    private static boolean assessmentComplete(
            ReturnedHearthSavedData data, UUID hearthId, UUID playerId) {
        return data.hearth(hearthId)
                .flatMap(record -> record.playerContact(playerId))
                .map(ReturnedHearthSavedData.HearthContactMemory::architectAssessmentComplete)
                .orElse(false);
    }
}
