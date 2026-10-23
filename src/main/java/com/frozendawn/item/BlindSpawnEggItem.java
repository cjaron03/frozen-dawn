package com.frozendawn.item;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModEntities;
import com.frozendawn.maeve.MaeveDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Test egg for a Blind Architect. Not a SpawnEggItem, so middle-clicking an Architect still
 * picks the ordinary egg. The Blind hunts by sound like a natural cave spawn.
 */
public class BlindSpawnEggItem extends Item {

    public static final int BASE_COLOR = 0x26252B;
    public static final int SPOT_COLOR = 0x8B5A3C;

    public BlindSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
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
        architect.setBlindArchitect(true);
        if (!level.addFreshEntity(architect)) {
            return InteractionResult.FAIL;
        }
        MaeveDirector.observePawn(architect);
        level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, at);
        context.getItemInHand().consume(1, context.getPlayer());
        return InteractionResult.CONSUME;
    }
}
