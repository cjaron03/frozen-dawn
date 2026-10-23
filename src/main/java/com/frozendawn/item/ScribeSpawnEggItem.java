package com.frozendawn.item;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModEntities;
import com.frozendawn.maeve.MaeveDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Test egg for a Scribe Architect. Not a SpawnEggItem, so middle-clicking an Architect still
 * picks the ordinary egg. The Scribe takes Maeve's one Scribe slot for the player who used it.
 */
public class ScribeSpawnEggItem extends Item {

    public static final int BASE_COLOR = 0x4A5568;
    public static final int SPOT_COLOR = 0xE8EEF2;

    public ScribeSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        BlockPos at = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked
                : clicked.relative(context.getClickedFace());
        ArchitectEntity architect = ModEntities.ARCHITECT.get().create(level, null, at, MobSpawnType.SPAWN_EGG, true, false);
        if (architect == null) {
            return InteractionResult.FAIL;
        }
        architect.preSeedObservation(level, player);
        if (!MaeveDirector.designateTestScribe(architect, player)) {
            architect.discard();
            player.displayClientMessage(Component.translatable("item.frozendawn.scribe_spawn_egg.unavailable"), true);
            return InteractionResult.FAIL;
        }
        architect.becomeScribe();
        if (!player.isCreative()) {
            architect.armSpawnObserveCue(player);
        }
        if (!level.addFreshEntity(architect)) {
            MaeveDirector.scribeEnded(architect, "SPAWN_REJECTED");
            return InteractionResult.FAIL;
        }
        MaeveDirector.observePawn(architect);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }
}
