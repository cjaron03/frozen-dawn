package com.frozendawn.labbridge;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.maeve.MaeveDirector;
import com.frozendawn.world.BlindArchitectSpawner;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** One-use operator ERASED fixture. Never erases an ordinary world or the natural encounter save. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabBlindErased {
    public static final String WORLD = "Blind Architect 11 ERASED";
    private static Session active;
    private LabBlindErased() { }
    private static boolean allowed(CommandSourceStack source) {
        return !FMLEnvironment.production && Boolean.getBoolean("frozendawn.labBridge")
                && source.getServer().getWorldData().getLevelName().equals(WORLD)
                && source.getServer().getPlayerList().getPlayerCount() == 1
                && source.getEntity() instanceof ServerPlayer p && p.serverLevel() == source.getServer().overworld();
    }
    private static int stage(ServerLevel level) {
        var objective = level.getScoreboard().getObjective("blind_erased");
        if (objective == null) return -1;
        var value = level.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly("#stage"), objective);
        return value == null ? -1 : value.value();
    }
    private static void stage(ServerLevel level, int value) {
        level.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#stage"),
                level.getScoreboard().getObjective("blind_erased")).set(value);
    }
    public static int access(CommandSourceStack source) {
        if (!allowed(source) || stage(source.getLevel()) != 0 || !source.getPlayer().isCreative()
                || PostMaeveWorldState.isErased(source.getServer()) || active != null
                || Files.exists(source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-erased-results"))) return 0;
        for (var e : source.getLevel().getAllEntities()) if (e instanceof ArchitectEntity) return 0;
        source.getLevel().getChunk(0, 0);
        return 1;
    }
    public static int start(CommandSourceStack source) {
        if (!allowed(source) || stage(source.getLevel()) != 1 || !source.getServer().isPaused()
                || active != null || PostMaeveWorldState.isErased(source.getServer())) return 0;
        var p = source.getPlayer(); var level = source.getLevel(); var manager = source.getServer().tickRateManager();
        if (!p.isAlive() || p.isCreative() || p.isSpectator() || !p.onGround()
                || p.position().distanceToSqr(new net.minecraft.world.phys.Vec3(23.5, -44, 5.5)) > .01
                || p.getTags().contains("blind_erased_used") || manager.isFrozen() || manager.isSprinting()
                || manager.isSteppingForward() || manager.tickrate() != 20 || FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get()) return 0;
        ArchitectEntity actor = null;
        for (var e : level.getAllEntities()) if (e instanceof ArchitectEntity a) {
            if (actor != null || !a.isBlindArchitect() || !a.isNoAi() || !a.isAlive() || a.getHealth() != 40
                    || !a.getTags().contains("blind_erased_actor")) return 0;
            actor = a;
        }
        if (actor == null || !geometry(level)) return 0;
        var root = source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-erased-results");
        if (Files.exists(root)) return 0;
        p.addTag("blind_erased_used"); stage(level, 9);
        try {
            Files.createDirectory(root);
            active = new Session(level, p, actor, root);
            active.begin();
            stage(level, 2); actor.setNoAi(false);
            cue(p, "VIEW RUNNING", "Stay still. The ERASED transition and test hit are automatic.");
            return 1;
        } catch (Exception e) {
            manager.setFrozen(true); active = null;
            source.sendFailure(Component.literal("Evidence failed; preserve this used attempt: " + e)); return 0;
        }
    }
    public static boolean geometry(ServerLevel level) {
        for (int z = 1; z <= 9; z++) {
            if (!level.getBlockState(new BlockPos(16, -44, z)).is(net.minecraft.world.level.block.Blocks.BEDROCK)
                    || !level.getBlockState(new BlockPos(16, -43, z)).isAir()) return false;
        }
        for (int x = 1; x <= 29; x++) for (int z = 1; z <= 9; z++)
            if (!level.getBlockState(new BlockPos(x, -42, z)).is(net.minecraft.world.level.block.Blocks.BEDROCK)) return false;
        return true;
    }
    private static void cue(ServerPlayer p, String title, String detail) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 60, 0));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(detail)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(title)));
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (active == null || event.getLevel() != active.level || active.level.getServer().isPaused()) return;
        try { active.tick(); }
        catch (Exception failure) { active.stop("EVIDENCE_FAILURE: " + failure); }
        if (active != null && active.finished) active = null;
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        if (active != null && active.level.getServer() == event.getServer()) {
            active.stop("WORLD_CLOSED_EARLY"); active = null;
        }
    }
    /** Native regression seam shares all transition and evidence logic with the integrated view. */
    public static final class Session {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final ArchitectEntity actor;
        private final Path root;
        private final net.minecraft.world.phys.Vec3 playerPosition;
        private long began;
        private int timer;
        private int contactFrames;
        private boolean erased, damageApplied, finished;
        private float playerHealth;
        public Session(ServerLevel level, ServerPlayer player, ArchitectEntity actor, Path root) {
            this.level = level; this.player = player; this.actor = actor; this.root = root;
            playerPosition = player.position();
        }
        public void begin() throws java.io.IOException {
            playerHealth = player.getHealth(); began = level.getGameTime();
            if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || !BlindArchitectSpawner.enabled(level))
                throw new IllegalStateException("Pre-erasure natural spawn gate must be open");
            actor.startDecisionRecording(null); actor.decisionJournal().useEnduranceLabBuffer();
            var row = sample(); row.addProperty("operatorFixture", true); row.addProperty("forcedActorPlacement", true);
            row.addProperty("fullHearthSequenceTested", false); row.addProperty("debugForceErased", false);
            row.addProperty("actorNbt", actor.saveWithoutId(new CompoundTag()).toString());
            row.addProperty("playerNbt", player.saveWithoutId(new CompoundTag()).toString());
            write("start.json", row, false);
        }
        private void write(String file, JsonObject row, boolean append) throws java.io.IOException {
            Files.writeString(root.resolve(file), row + "\n", append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW);
        }
        private JsonObject sample() {
            var row = new JsonObject(); row.addProperty("timer", timer); row.addProperty("gameTime", level.getGameTime());
            row.addProperty("actor", actor.getUUID().toString()); row.addProperty("player", player.getUUID().toString());
            row.addProperty("actorPosition", actor.position().toString()); row.addProperty("playerPosition", player.position().toString());
            row.addProperty("actorHealth", actor.getHealth()); row.addProperty("playerHealth", player.getHealth());
            row.addProperty("actorAlive", actor.isAlive()); row.addProperty("noAI", actor.isNoAi());
            row.addProperty("erased", PostMaeveWorldState.isErased(level));
            row.addProperty("spawnGateOpen", BlindArchitectSpawner.enabled(level));
            row.addProperty("mobSpawning", level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING));
            row.addProperty("diagnostics", actor.blindController().diagnostics());
            row.addProperty("tracks", actor.blindController().tracks().toString());
            row.addProperty("vanillaTarget", actor.getTarget() == null ? "" : actor.getTarget().getUUID().toString());
            row.addProperty("frozen", level.getServer().tickRateManager().isFrozen());
            return row;
        }
        public static boolean cleared(ArchitectEntity actor) {
            return actor.getTarget() == null && actor.blindController().tracks().isEmpty()
                    && actor.blindController().signal() == null
                    && actor.blindController().diagnostics().contains("pending=0");
        }
        public void tick() throws java.io.IOException {
            if (finished) return;
            timer++;
            if (level.getGameTime() - began != timer || !player.isAlive() || !actor.isAlive() || actor.isNoAi()
                    || player.getHealth() != playerHealth || player.position().distanceToSqr(playerPosition) > .01
                    || level.getServer().tickRateManager().isFrozen()
                    || level.getServer().tickRateManager().isSprinting() || level.getServer().tickRateManager().isSteppingForward()) {
                stop("PLAYER_ACTOR_OR_CLOCK_CHANGED"); return;
            }
            var signal = actor.blindController().signal();
            if (!erased && signal != null && player.getUUID().equals(signal.subject())) contactFrames++;
            if (timer == 160) {
                if (contactFrames < 40 || signal == null || !player.getUUID().equals(signal.subject())
                        || PostMaeveWorldState.isErased(level) || !BlindArchitectSpawner.enabled(level)) {
                    stop("NO_CONFIRMED_PRE_ERASURE_CONTACT"); return;
                }
                write("transition-before.json", sample(), false);
                // Actual saved-world lifecycle, not a config shortcut or synthetic sensing injection.
                if (!PostMaeveWorldState.markErased(level) || !cleared(actor)) {
                    stop("ERASURE_DID_NOT_CLEAR_IMMEDIATELY"); return;
                }
                erased = true; write("transition-after.json", sample(), false);
                cue(player, "MAEVE ERASED", "Watch the BA lose its contact.");
            }
            if (timer > 160 && (!PostMaeveWorldState.isErased(level) || !cleared(actor)
                    || !actor.blindController().diagnostics().startsWith("blind=ERASED") || BlindArchitectSpawner.enabled(level))) {
                stop("POST_ERASURE_CONTACT_OR_SPAWN_GATE_OPEN"); return;
            }
            if (timer == 220) {
                var damage = sample(); damage.addProperty("healthBefore", actor.getHealth());
                damage.addProperty("damageSource", "minecraft:player_attack");
                damageApplied = actor.hurt(level.damageSources().playerAttack(player), 2);
                damage.addProperty("accepted", damageApplied); damage.addProperty("healthAfter", actor.getHealth());
                write("damage.json", damage, false);
                if (!damageApplied || damage.get("healthAfter").getAsFloat() >= damage.get("healthBefore").getAsFloat()) {
                    stop("DAMAGE_NOT_APPLIED"); return;
                }
                cue(player, "TEST HIT", "Watch for retaliation; stay still.");
            }
            write("trace.jsonl", sample(), Files.exists(root.resolve("trace.jsonl")));
            if (timer == 440) stop(erased && damageApplied && contactFrames >= 40 ? "COMPLETE_OWNER_ACCOUNT_PENDING" : "MISSING_TRANSITION");
        }
        public void stop(String reason) {
            if (finished) return;
            finished = true;
            try {
                var result = sample(); result.addProperty("outcome", reason); result.addProperty("contactFrames", contactFrames);
                result.addProperty("erasurePerformed", erased); result.addProperty("damageApplied", damageApplied);
                result.addProperty("dropped", actor.decisionJournal().dropped());
                result.addProperty("actorNbt", actor.saveWithoutId(new CompoundTag()).toString());
                write("end.json", result, false);
                Files.writeString(root.resolve("decisions.tsv"), actor.decisionJournal().tsv(), StandardOpenOption.CREATE_NEW);
            } catch (Exception failure) { System.err.println("[LabBlindErased] Preserve evidence failure: " + failure); }
            level.getServer().tickRateManager().stopSprinting(); level.getServer().tickRateManager().stopStepping();
            level.getServer().tickRateManager().setFrozen(true);
            if (level.getScoreboard().getObjective("blind_erased") != null) stage(level, 3);
            cue(player, reason.equals("COMPLETE_OWNER_ACCOUNT_PENDING") ? "VIEW COMPLETE" : "INCONCLUSIVE", "Press Esc and describe what happened.");
        }
    }
}
