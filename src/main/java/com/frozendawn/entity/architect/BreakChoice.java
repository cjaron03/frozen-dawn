package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import java.util.Objects;

public record BreakChoice(BlockPos pos, BreakReason reason) {
    public BreakChoice {
        pos = Objects.requireNonNull(pos).immutable();
        Objects.requireNonNull(reason);
    }
}
