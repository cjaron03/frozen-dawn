package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModEntities;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.network.ShepherdLinePayload;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdDialogue.Line;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdProjectionGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int FRIENDLY = 300, TRUSTED = 600;
    private static final String FIRST = "shepherd.advice.hunter.1", SECOND = "shepherd.advice.hunter.2";
    private static final List<Line> TWO = List.of(new Line(FIRST, null, Mood.RECORDING, null), new Line(SECOND, null, Mood.THINKING, null));

    @BeforeBatch(batch = "shepherd_projection_plays") public static void playsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_projection_plays") public static void playsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_projection_stops") public static void stopsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_projection_stops") public static void stopsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_projection_quiet") public static void quietBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_projection_quiet") public static void quietAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_projection_worry") public static void worryBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_projection_worry") public static void worryAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_projection_architect") public static void architectBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_projection_architect") public static void architectAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_projection_egg") public static void eggBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_projection_egg") public static void eggAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_projection_reload") public static void reloadBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_projection_reload") public static void reloadAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_projection_skip") public static void skipBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_projection_skip") public static void skipAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_projection_holds") public static void holdsBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_projection_holds") public static void holdsAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    private static ShepherdEntity awake(GameTestHelper h, ServerPlayer player, int affinity) {
        data(h).stage(Stage.AWAKE);
        data(h).affinity(affinity);
        MillerPattern.setCoherence(h.getLevel().getServer(), 1);
        return ShepherdEntity.spawnFrom(h.getLevel(), h.absolutePos(SPOT), player.getUUID());
    }

    /** The clock of a long projection, without waiting for it on a gate. */
    private static void run(GameTestHelper h, int ticks) {
        for (int i = 0; i < ticks; i++) ShepherdProjection.tick(h.getLevel().getServer());
    }

    private static int typing(String key) {
        return Language.getInstance().getOrDefault(key).length() * ShepherdPolicy.TICKS_PER_CHAR;
    }

    private static int held(String key) {
        return typing(key) + ShepherdPolicy.STRIP_HOLD_TICKS;
    }

    private static List<String> keys() {
        return ShepherdProjection.spoken().stream().map(ShepherdLinePayload::key).toList();
    }

    private static void ask(ShepherdEntity she, ServerPlayer player, String text) {
        she.interact(player, InteractionHand.MAIN_HAND);
        ShepherdTalk.action(player, ShepherdTalk.liveId(), Action.TALK);
        she.thinkFor(0);
        ShepherdTalk.typed(player, ShepherdTalk.liveId(), text);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_projection_plays", timeoutTicks = 400)
    public static void shepherdAProjectionClosesTheTalkAndPlays(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            ask(she, player, "hunter");
            h.assertTrue(ShepherdTalk.liveId() == 0, "The talk closed");
            h.assertTrue(ShepherdProjection.active(), "It plays");
            h.assertTrue(she.projection() == ShepherdProjection.HUNTER, "The Hunter: " + she.projection());
            h.assertTrue(data(h).flag("orsa_heard_hunter"), "It counts as heard");
            run(h, ShepherdProjection.BEAM_TICKS - 1);
            h.assertTrue(keys().isEmpty(), "The beam lands first: " + keys());
            run(h, 1);
            String open = "shepherd.advice.hunter.open";
            h.assertTrue(List.of(open).equals(keys()), "Then she starts: " + keys());
            h.assertTrue(Language.getInstance().has(open), "The server can measure her lines");
            h.assertTrue(she.talking(), "Her body speaks it");
            run(h, held(open) - 1);
            h.assertTrue(keys().size() == 1, "She waits out the typing and the hold: " + keys());
            run(h, 1);
            h.assertTrue(List.of(open, FIRST).equals(keys()), "Then the next: " + keys());
            h.assertTrue(she.mood() == Mood.RECORDING, "Each line brings its face: " + she.mood());
            run(h, 20000);
            h.assertTrue(!ShepherdProjection.active() && she.projection() == 0, "It ends by itself");
            h.assertTrue(keys().size() == 6, "Every line played: " + keys());
            h.succeed();
        });
    }

    private static void stops(GameTestHelper h, ShepherdEntity she, ServerPlayer player, String why, Runnable what) {
        ShepherdProjection.start(player, she, "hunter", TWO);
        h.assertTrue(ShepherdProjection.active(), "Started, for " + why);
        what.run();
        run(h, 1);
        h.assertTrue(!ShepherdProjection.active(), "Stopped: " + why);
        h.assertTrue(she.projection() == 0, "Nothing left behind: " + why);
        h.assertTrue(!she.talking(), "Her mouth stops: " + why);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_projection_stops", timeoutTicks = 400)
    public static void shepherdAProjectionStopsWhenItShould(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            player.setGameMode(GameType.SURVIVAL);
            var she = awake(h, player, FRIENDLY);
            var home = player.position();
            stops(h, she, player, "walked off", () -> player.setPos(home.add(ShepherdPolicy.TALK_RANGE + 3, 0, 0)));
            player.setPos(home);
            stops(h, she, player, "hurt", () -> player.hurt(h.getLevel().damageSources().genericKill(), 1));
            var zombie = EntityType.ZOMBIE.create(h.getLevel());
            zombie.moveTo(home.add(4, 0, 0));
            h.getLevel().addFreshEntity(zombie);
            stops(h, she, player, "hunted", () -> zombie.setTarget(player));
            zombie.discard();
            stops(h, she, player, "logged out", () -> player.disconnect());
            var other = player(h);
            stops(h, she, other, "offline", she::goOffline);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_projection_quiet", timeoutTicks = 400)
    public static void shepherdNothingInterruptsAProjection(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            var level = h.getLevel();
            data(h).flag(ShepherdPrompt.FIRST_MEETING_DONE, true);
            h.assertTrue(ShepherdReactions.answer(level, "shepherd.hub.talk", Mood.NEUTRAL), "She can speak up beforehand");
            ShepherdProjection.start(player, she, "hunter", TWO);
            run(h, ShepherdProjection.BEAM_TICKS);
            h.assertTrue(!ShepherdReactions.answer(level, "shepherd.hub.talk", Mood.NEUTRAL), "Nothing unasked");
            String said = ShepherdReactions.said();
            ShepherdReactions.saw(player, Deed.CALLED_SHEPHERD);
            h.assertTrue(said.equals(ShepherdReactions.said()), "No deed reaches the strip");
            she.react(Mood.ALARMED);
            h.assertTrue(she.mood() == Mood.RECORDING, "Her face stays on the line: " + she.mood());
            she.interact(player, InteractionHand.MAIN_HAND);
            h.assertTrue(ShepherdTalk.liveId() == 0, "A right-click does nothing");
            ShepherdTalk.start(player, "hub.talk");
            h.assertTrue(ShepherdTalk.liveId() == 0, "No scene opens over it");
            ShepherdProjection.start(player, she, "mimic", TWO);
            h.assertTrue(she.projection() == ShepherdProjection.HUNTER, "One at a time: " + she.projection());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_projection_worry", timeoutTicks = 400)
    public static void shepherdWorryHoldsTheLine(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            String before = "shepherd.advice.architect.6", worried = "shepherd.advice.architect.7";
            ShepherdProjection.start(player, she, "architect", List.of(new Line(before, null, Mood.NEUTRAL, null), new Line(worried, null, Mood.WORRIED, null)));
            run(h, ShepherdProjection.BEAM_TICKS + held(before));
            h.assertTrue(she.mood() == Mood.WORRIED, "She stops: " + she.mood());
            h.assertTrue(!she.talking(), "Her mouth stops with her");
            run(h, ShepherdFace.WORRY_TICKS - 1);
            h.assertTrue(List.of(before).equals(keys()), "The line waits: " + keys());
            run(h, 1);
            h.assertTrue(List.of(before, worried).equals(keys()), "Then it comes: " + keys());
            h.assertTrue(ShepherdProjection.spoken().getLast().mood() == Mood.NEUTRAL, "As Neutral: " + ShepherdProjection.spoken().getLast().mood());
            h.assertTrue(she.mood() == Mood.NEUTRAL && she.talking(), "She comes back");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_projection_architect", timeoutTicks = 400)
    public static void shepherdTheArchitectCommentsThroughout(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            ask(she, player, "architect");
            List<String> wanted = new ArrayList<>(List.of("shepherd.advice.architect.open"));
            for (int line = 1; line <= 10; line++) wanted.add("shepherd.advice.architect." + line);
            while (ShepherdProjection.active()) {
                h.assertTrue(she.projection() == ShepherdProjection.ARCHITECT, "The figure stays up for all of it: " + she.projection());
                run(h, 1);
            }
            h.assertTrue(wanted.equals(keys()), "All of it, in order: " + keys());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_projection_egg", timeoutTicks = 400)
    public static void shepherdTheEggPlaysOnce(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            ShepherdProjection.start(player, she, "hunter", TWO);
            run(h, 20000);
            h.assertTrue(List.of(FIRST, SECOND).equals(keys()), "Not the first time: " + keys());
            ShepherdProjection.start(player, she, "mimic", TWO);
            run(h, 20000);
            h.assertTrue(List.of(FIRST, SECOND).equals(keys()), "Not below Trusted: " + keys());
            data(h).affinity(TRUSTED);
            ShepherdProjection.start(player, she, "mimic", TWO);
            run(h, ShepherdProjection.BEAM_TICKS + held(FIRST));
            h.assertTrue(List.of(FIRST, ShepherdProjection.EGG_LINE).equals(keys()), "It waits for the figure to form: " + keys());
            h.assertTrue(she.projection() == ShepherdProjection.EGG_FRAME, "The other shape: " + she.projection());
            h.assertTrue(data(h).flag(ShepherdProjection.EGG), "Spent as it plays");
            run(h, ShepherdProjection.EGG_TICKS);
            h.assertTrue(she.projection() == ShepherdProjection.MIMIC, "Only for a moment: " + she.projection());
            run(h, 20000);
            h.assertTrue(List.of(FIRST, ShepherdProjection.EGG_LINE, SECOND).equals(keys()), "Then on as it was: " + keys());
            ShepherdProjection.start(player, she, "hunter", TWO);
            run(h, 20000);
            h.assertTrue(List.of(FIRST, SECOND).equals(keys()), "Once: " + keys());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_projection_reload", timeoutTicks = 400)
    public static void shepherdNoProjectionSurvivesAReload(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            ShepherdProjection.start(player, she, "hunter", TWO);
            var saved = she.saveWithoutId(new CompoundTag());
            var loaded = ModEntities.CS1_UNIT.get().create(h.getLevel());
            loaded.load(saved);
            h.assertTrue(loaded.projection() == 0, "A body loads with no figure: " + loaded.projection());
            ShepherdTalk.clearForTests();
            h.assertTrue(!ShepherdProjection.active(), "And a stopped server forgets it");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_projection_skip", timeoutTicks = 400)
    public static void shepherdTheSpeakerCanSkipForward(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var other = player(h);
            var she = awake(h, player, FRIENDLY);
            ShepherdProjection.start(player, she, "hunter", TWO);
            run(h, ShepherdProjection.BEAM_TICKS);
            h.assertTrue(List.of(FIRST).equals(keys()), "The first line: " + keys());
            she.interact(other, InteractionHand.MAIN_HAND);
            run(h, 1);
            h.assertTrue(keys().size() == 1, "Only the one who asked can skip: " + keys());
            she.interact(player, InteractionHand.MAIN_HAND);
            run(h, 1);
            h.assertTrue(keys().size() == 1 && ShepherdProjection.active(), "The first right-click only finishes the typing: " + keys());
            she.interact(player, InteractionHand.MAIN_HAND);
            run(h, 1);
            h.assertTrue(List.of(FIRST, SECOND).equals(keys()), "The next goes to the next line: " + keys());
            she.interact(player, InteractionHand.MAIN_HAND);
            she.interact(player, InteractionHand.MAIN_HAND);
            run(h, 1);
            h.assertTrue(!ShepherdProjection.active() && she.projection() == 0, "And past the last, it ends");
            h.assertTrue(ShepherdTalk.liveId() == 0, "Without opening a talk");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_projection_skip_all", timeoutTicks = 400)
    public static void shepherdASneakingClickEndsIt(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var other = player(h);
            var she = awake(h, player, FRIENDLY);
            ShepherdProjection.start(player, she, "hunter", TWO);
            run(h, ShepherdProjection.BEAM_TICKS);
            other.setShiftKeyDown(true);
            she.interact(other, InteractionHand.MAIN_HAND);
            h.assertTrue(ShepherdProjection.active(), "Only the one who asked can end it");
            player.setShiftKeyDown(true);
            she.interact(player, InteractionHand.MAIN_HAND);
            h.assertTrue(!ShepherdProjection.active() && she.projection() == 0, "It ends there, with lines still to come");
            run(h, 1);
            h.assertTrue(keys().size() == 1 && ShepherdTalk.liveId() == 0, "With nothing more said, and no talk: " + keys());
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_projection_holds", timeoutTicks = 400)
    public static void shepherdHoldsStillForAProjection(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            var she = awake(h, player, FRIENDLY);
            ShepherdProjection.start(player, she, "hunter", TWO);
            var at = she.position();
            player.setPos(player.position().add(5, 0, 0));
            h.runAfterDelay(60, () -> {
                h.assertTrue(ShepherdProjection.active(), "Still inside the range");
                h.assertTrue(she.position().distanceTo(at) < 0.3, "She stays with the figure, or nobody could walk off: " + she.position().distanceTo(at));
                h.succeed();
            });
        });
    }
}
