package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** The few things she says that are not about the moment. A once-only flag is only set when the line played, or a busy moment would spend it. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdEggs {
    static final String ARCHITECT = "egg_architect", SWARM = "egg_swarm", EXPLOSION = "egg_explosion", YOUR_PART = "egg_your_part", WALL = "lore_lattice";
    static final String ARCHITECT_LINE = "shepherd.egg.architect", SWARM_LINE = "shepherd.egg.swarm", EXPLOSION_LINE = "shepherd.egg.explosion",
            YOUR_PART_LINE = "shepherd.egg.your_part", RESUPPLY_LINE = "shepherd.egg.resupply", RAW_FISH_LINE = "shepherd.egg.raw_fish", WALL_LINE = "shepherd.lore.lattice";
    /** Set by "Or what, Shepherd?" in the first meeting. */
    private static final String NARRATED = "fm_narrated";
    private static final String RESUPPLY_CODE = "down down up right";
    private static final String RESUPPLY_READY = "egg.resupply", RAW_FISH_READY = "egg.raw_fish";

    private ShepherdEggs() {}

    /** What a poll can see. Each is a no-op once its line has played. */
    static void look(ServerLevel level, ShepherdEntity she, List<ServerPlayer> near) {
        var data = ShepherdSavedData.get(level.getServer());
        if (she.watching() instanceof ArchitectEntity) once(data, ARCHITECT, () -> ShepherdReactions.blurt(level, ARCHITECT_LINE, Mood.THINKING));
        if (!data.flag(SWARM) && near.stream().anyMatch(player -> hostilesAround(level, player) >= ShepherdPolicy.SWARM_HOSTILES)) {
            once(data, SWARM, () -> ShepherdReactions.blurt(level, SWARM_LINE, Mood.ALARMED));
        }
        if (she.stoodFor() >= ShepherdPolicy.YOUR_PART_TICKS) once(data, YOUR_PART, () -> ShepherdReactions.murmur(level, YOUR_PART_LINE, Mood.RECORDING));
        if (data.flag(WALL) || level != level.getServer().overworld()) return;
        var hearths = ReturnedHearthSavedData.get(level.getServer());
        wall(level, near, hearths.hearths().stream().filter(ReturnedHearthSavedData.HearthRecord::structurePlaced).map(ReturnedHearthSavedData.HearthRecord::center).toList(), hearths.maeveErased());
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
