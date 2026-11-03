package com.frozendawn.entity;

import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import java.util.UUID;

/** Development presentation fixture. Real departure controller, without combat AI. */
public final class LabSignalActor extends ArchitectEntity {
    private final ArchitectAttentionController departure = new ArchitectAttentionController(this);
    public LabSignalActor(ServerLevel level, BlockPos origin) {
        super(ModEntities.ARCHITECT.get(), level);
        moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
        setInvulnerable(true); setPersistenceRequired(); setNoGravity(true); setOnGround(true);
    }
    public void begin() {
        departure.begin(UUID.randomUUID(), blockPosition().offset(0, 0, -2), "RECON_SIGNAL_REPLAY");
    }
    @Override public void aiStep() {
        // Client receives the ordinary Architect type and production model/FX.
        setOnGround(true); departure.tick(); move(MoverType.SELF, getDeltaMovement());
    }
    @Override public boolean shouldBeSaved() { return false; }
}
