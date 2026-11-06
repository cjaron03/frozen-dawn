package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.ReturnedEntity;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdDialogue.Line;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.world.HeaterRegistry;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The few things she says that are not about the moment. A once-only flag is only set when the line played, or a busy moment would spend it. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdEggs {
    static final String ARCHITECT = "egg_architect", SWARM = "egg_swarm", EXPLOSION = "egg_explosion", YOUR_PART = "egg_your_part", WALL = "lore_lattice";
    static final String ARCHITECT_LINE = "shepherd.egg.architect", SWARM_LINE = "shepherd.egg.swarm", EXPLOSION_LINE = "shepherd.egg.explosion",
            YOUR_PART_LINE = "shepherd.egg.your_part", RESUPPLY_LINE = "shepherd.egg.resupply", RAW_FISH_LINE = "shepherd.egg.raw_fish", WALL_LINE = "shepherd.lore.lattice";
    static final String HUNTER_BURN = "reacted_hunter_burn", CLOVIS = "clovis_said", CAUGHT = "caught", GAG = "gag", MOMENT = "moment";
    static final String HUNTER_BURN_LINE = "shepherd.egg.hunter_burn", CLOVIS_LINE = "shepherd.egg.clovis", CAUGHT_LINE = "shepherd.egg.caught",
            GAG_LINE = "shepherd.egg.gag", PENGUIN_LINE = "shepherd.egg.gag.penguin";
    private static final String T5_DONE = "t5_done";
    /** What she has seen near them, what she has already offered on it, and the node that offers it. */
    private static final String CONFRONT = "caught.confront";
    private static final String SAW = "offer_saw_", OFFERED = "offer_made_", OFFER = "advice.offer.";
    private static final List<String> OFFERS = List.of("frostbitten", "frostmite", "hollow", "hunter", "mimic", "architect");
    /** Set by "Or what, Shepherd?" in the first meeting. */
    private static final String NARRATED = "fm_narrated";
    private static final String RESUPPLY_CODE = "down down up right";
    private static final String RESUPPLY_READY = "egg.resupply", RAW_FISH_READY = "egg.raw_fish";
    private static final String FEWER = "said_fewer", FEWER_LINE = "shepherd.bleed.fewer", FEWER_ASIDE = "shepherd.bleed.fewer.aside";
    private static final int FEWER_ASIDE_TICKS = 80;
    /** When she takes it back. It need not outlive the server, and past a reload the word stands alone. */
    private static long asideAt = ShepherdSavedData.NOT_SET;

    private ShepherdEggs() {}

    /** What a poll can see. Each is a no-op once its line has played. */
    static void look(ServerLevel level, ShepherdEntity she, List<ServerPlayer> near) {
        var data = ShepherdSavedData.get(level.getServer());
        if (she.watching() instanceof ArchitectEntity) once(data, ARCHITECT, () -> ShepherdReactions.blurt(level, ARCHITECT_LINE, Mood.THINKING));
        if (!data.flag(SWARM) && near.stream().anyMatch(player -> hostilesAround(level, player) >= ShepherdPolicy.SWARM_HOSTILES)) {
            once(data, SWARM, () -> ShepherdReactions.blurt(level, SWARM_LINE, Mood.ALARMED));
        }
        if (she.stoodFor() >= ShepherdPolicy.YOUR_PART_TICKS) once(data, YOUR_PART, () -> ShepherdReactions.murmur(level, YOUR_PART_LINE, Mood.RECORDING));
        if (data.flag(T5_DONE)) once(data, CLOVIS, () -> ShepherdReactions.murmur(level, CLOVIS_LINE, Mood.THINKING));
        offer(level, near, data);
        idle(level, she, near, data);
        if (data.flag(WALL) || level != level.getServer().overworld()) return;
        var hearths = ReturnedHearthSavedData.get(level.getServer());
        wall(level, near, hearths.hearths().stream().filter(ReturnedHearthSavedData.HearthRecord::structurePlaced).map(ReturnedHearthSavedData.HearthRecord::center).toList(), hearths.maeveErased());
    }

    /** She has seen one, so she noticed: it goes on file for when things are calm. */
    static void sighted(ShepherdSavedData data, Mob mob) {
        String id = mob instanceof FrostbittenEntity ? "frostbitten" : mob instanceof FrostmiteEntity ? "frostmite" : mob instanceof HollowEntity ? "hollow"
                : mob instanceof ReturnedEntity ? "hunter" : mob instanceof MimicEntity ? "mimic" : mob instanceof ArchitectEntity ? "architect" : null;
        if (id != null && !data.flag(SAW + id)) data.flag(SAW + id, true);
    }

    /** Once she likes them, she offers what she knows on what she saw, once a mob, unless they asked already or the advice has stopped being true. */
    private static void offer(ServerLevel level, List<ServerPlayer> near, ShepherdSavedData data) {
        var server = level.getServer();
        long now = server.overworld().getGameTime();
        if (!data.tier().atLeast(Tier.FRIENDLY) || data.promptNode() != null || data.resumeNode() != null || ShepherdTalk.live() || !data.flag(ShepherdPrompt.FIRST_MEETING_DONE)) return;
        if (ShepherdReactions.quiet(data, now) || PostMaeveWorldState.isErased(server) || !ShepherdPrompt.calm(server, level, near)) return;
        for (String mob : OFFERS) {
            if (!data.flag(SAW + mob) || data.flag(OFFERED + mob) || data.flag(ShepherdTopics.HEARD + mob)) continue;
            data.flag(OFFERED + mob, true);
            ShepherdPrompt.ask(data, OFFER + mob);
            return;
        }
    }

    /** A quiet minute of her own, with her follower by her: the gag at Close, else what she shows herself when nobody is looking. */
    private static void idle(ServerLevel level, ShepherdEntity she, List<ServerPlayer> near, ShepherdSavedData data) {
        var server = level.getServer();
        long now = server.overworld().getGameTime();
        var follower = ShepherdEntity.followerIn(level);
        if (follower == null || !near.contains(follower) || follower.distanceTo(she) > ShepherdPolicy.TALK_RANGE || ShepherdTalk.live() || she.asking()) return;
        if (data.tier() == Tier.WARY || !data.flag(ShepherdPrompt.FIRST_MEETING_DONE) || ShepherdReactions.quiet(data, now) || !ShepherdPrompt.calm(server, level, near)) return;
        if (data.lastTalk() == ShepherdSavedData.NOT_SET || now - data.lastTalk() < ShepherdPolicy.MOMENT_IDLE_TICKS || now < data.deedReady(MOMENT)) return;
        // She stays put for either, so someone on the move would walk straight out of it.
        if (she.stoodFor() < ShepherdPolicy.STILL_TICKS) return;
        if (data.tier().atLeast(Tier.CLOSE) && now >= data.deedReady(GAG)) {
            ShepherdProjection.start(follower, she, "item", List.of(new Line(GAG_LINE, null, Mood.BEAMING, null), new Line(PENGUIN_LINE, null, Mood.AMUSED, null)));
        } else if (now >= data.deedReady(CAUGHT) && near.stream().noneMatch(player -> looking(player, she))) {
            data.deedReady(CAUGHT, now + ShepherdPolicy.CAUGHT_RETRY_TICKS);
            ShepherdProjection.secretly(follower, she);
        }
    }

    private static boolean looking(ServerPlayer player, ShepherdEntity she) {
        var toHer = she.getEyePosition().subtract(player.getEyePosition()).normalize();
        return player.getViewVector(1).dot(toHer) >= ShepherdPolicy.LOOKING_AT && player.hasLineOfSight(she);
    }

    /** Whether anyone near has turned round. */
    static boolean spotted(ShepherdEntity she) {
        return ((ServerLevel) she.level()).getPlayers(player -> !player.isSpectator() && player.distanceTo(she) <= ShepherdPolicy.HEARD_BLOCKS && looking(player, she)).size() > 0;
    }

    /** "You didn't see that!" The day is only spent on being seen. */
    static void caught(ShepherdEntity she) {
        var level = (ServerLevel) she.level();
        var data = ShepherdSavedData.get(level.getServer());
        data.deedReady(CAUGHT, level.getServer().overworld().getGameTime() + ShepherdPolicy.DAY_TICKS);
        ShepherdReactions.answer(level, CAUGHT_LINE, Mood.FLUSTERED);
        she.fluster();
        // Whoever talks to her next gets to bring it up, however long they leave it.
        if (data.resumeNode() == null) data.resumeNode(CONFRONT);
    }

    /** Her own strategy, working where she can see it. Fire from anywhere else is not hers. */
    @SubscribeEvent public static void burned(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ReturnedEntity hunter) || !(hunter.level() instanceof ServerLevel level) || !event.getSource().is(DamageTypes.ON_FIRE)) return;
        var she = ShepherdReactions.loaded(level.getServer());
        if (she == null || she.level() != level || she.distanceTo(hunter) > ShepherdPolicy.WATCH_BLOCKS) return;
        boolean atAHeater = HeaterRegistry.getHeaters(level).stream().anyMatch(heater -> hunter.blockPosition().closerToCenterThan(heater.getCenter(), ShepherdPolicy.HEATER_BURN_BLOCKS));
        if (atAHeater) once(ShepherdSavedData.get(level.getServer()), HUNTER_BURN, () -> ShepherdReactions.blurt(level, HUNTER_BURN_LINE, Mood.SAD));
    }

    /** Takes the centers so a GameTest or a flat lab need not place a Hearth, which other tests count on being unplaced. */
    public static void wall(ServerLevel level, List<ServerPlayer> near, List<BlockPos> hearths, boolean maeveErased) {
        if (maeveErased || !ShepherdPrompt.unhunted(level.getServer(), level, near)) return;
        boolean close = near.stream().anyMatch(player -> hearths.stream().anyMatch(center -> player.blockPosition().closerThan(center, ShepherdPolicy.WALL_BLOCKS)));
        if (close) once(ShepherdSavedData.get(level.getServer()), WALL, () -> ShepherdReactions.blurt(level, WALL_LINE, Mood.THINKING));
    }

    private static void once(ShepherdSavedData data, String flag, BooleanSupplier say) {
        if (!data.flag(flag) && say.getAsBoolean()) data.flag(flag, true);
    }

    private static int hostilesAround(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ShepherdPolicy.WATCH_BLOCKS), mob -> mob instanceof Enemy && mob.isAlive()).size();
    }

    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var she = ShepherdReactions.loaded(level.getServer());
        if (she == null || she.level() != level || she.position().distanceTo(event.getExplosion().center()) > ShepherdPolicy.WATCH_BLOCKS) return;
        once(ShepherdSavedData.get(level.getServer()), EXPLOSION, () -> ShepherdReactions.blurt(level, EXPLOSION_LINE, Mood.UNIMPRESSED));
    }

    @SubscribeEvent public static void chat(ServerChatEvent event) {
        if (!RESUPPLY_CODE.equalsIgnoreCase(event.getRawText().trim())) return;
        var player = event.getPlayer();
        player.getServer().execute(() -> daily(player, RESUPPLY_READY, () -> ShepherdReactions.answer(player.serverLevel(), RESUPPLY_LINE, Mood.UNIMPRESSED)));
    }

    /** A correction in a voice that is not hers. It says nothing of what they typed. */
    @SubscribeEvent public static void corrected(ServerChatEvent event) {
        if (!ShepherdPolicy.lessBeforeAPlural(event.getRawText())) return;
        var player = event.getPlayer();
        player.getServer().execute(() -> fewer(player));
    }

    private static void fewer(ServerPlayer player) {
        if (ShepherdReactions.watching(player) == null) return;
        var server = player.getServer();
        once(ShepherdSavedData.get(server), FEWER, () -> {
            boolean said = ShepherdReactions.answer(player.serverLevel(), FEWER_LINE, Mood.NEUTRAL);
            if (said) asideAt = server.overworld().getGameTime() + FEWER_ASIDE_TICKS;
            return said;
        });
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (asideAt == ShepherdSavedData.NOT_SET || server.overworld().getGameTime() < asideAt) return;
        asideAt = ShepherdSavedData.NOT_SET;
        var she = ShepherdReactions.loaded(server);
        if (she != null) ShepherdReactions.aside((ServerLevel) she.level(), FEWER_ASIDE, Mood.THINKING);
    }

    static void clearForTests() {
        asideAt = ShepherdSavedData.NOT_SET;
    }

    @SubscribeEvent public static void ate(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getItem().is(Items.COD) || event.getItem().is(Items.SALMON))) return;
        if (!ShepherdSavedData.get(player.getServer()).flag(NARRATED)) return;
        daily(player, RAW_FISH_READY, () -> ShepherdReactions.murmur(player.serverLevel(), RAW_FISH_LINE, Mood.RECORDING));
    }

    private static void daily(ServerPlayer player, String ready, BooleanSupplier say) {
        if (ShepherdReactions.watching(player) == null) return;
        var data = ShepherdSavedData.get(player.getServer());
        long now = player.getServer().overworld().getGameTime();
        if (now >= data.deedReady(ready) && say.getAsBoolean()) data.deedReady(ready, now + ShepherdPolicy.DAY_TICKS);
    }
}
