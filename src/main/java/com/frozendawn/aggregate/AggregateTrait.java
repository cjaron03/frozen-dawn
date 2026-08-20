package com.frozendawn.aggregate;

import com.frozendawn.entity.AggregateEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** One bounded inherited behavior. Traits never own phase or persistence. */
public interface AggregateTrait {
    AggregateLineage lineage();

    boolean canStart(AggregateEntity aggregate, LivingEntity target);

    void start(AggregateEntity aggregate, LivingEntity target);

    void tick(ServerLevel level, AggregateEntity aggregate, LivingEntity target,
              int actionTick);
}
