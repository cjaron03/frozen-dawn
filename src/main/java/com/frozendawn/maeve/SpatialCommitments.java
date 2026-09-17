package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;
import java.util.ArrayList;
import java.util.List;

final class SpatialCommitments {
    private SpatialCommitments() { }
    static List<MaeveDirector.PositionCandidate> candidates(BeliefStore store, ArchitectEntity observer, net.minecraft.server.level.ServerPlayer player,
                                                          List<MaeveDirector.CommitmentHint> hints) {
        if (store == null) return List.of();
        var world = store.world(player.getUUID()); if (world == null) return List.of();
        long now = observer.getServer().overworld().getGameTime();
        var result = new ArrayList<MaeveDirector.PositionCandidate>();
        for (var hint : hints) {
            if (!WorldModel.BEARINGS.contains(hint.pattern()) || hint.confidence() < CommitmentPolicy.THRESHOLD) continue;
            var target = world.resolve(observer.level().dimension().location().toString(), hint.pattern(), observer.blockPosition(), now);
            if (target == null) continue;
            result.add(new MaeveDirector.PositionCandidate(hint.pattern(), target.outside(), null,
                    Math.sqrt(observer.blockPosition().distSqr(target.outside())), target));
        }
        return List.copyOf(result);
    }
}
