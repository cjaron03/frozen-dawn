package com.frozendawn.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Shared scaffolding for Frozen Dawn game tests.
 *
 * <p>Not a {@code @GameTestHolder}, so NeoForge's annotation scan ignores it.
 */
public final class GameTestTemplates {

    /**
     * A 9x5x9 template containing nothing at all.
     *
     * <p>Frozen Dawn's smoke tests do not care about scenery, and an empty template keeps the
     * setup readable in Java rather than buried in NBT that nobody can review in a diff. Tests
     * that need ground call {@link #placeFloor} instead of relying on the framework's own
     * terrain clearing, whose exact fill height sits just outside the test bounds.
     */
    public static final String EMPTY = "empty_9x5x9";

    /** Edge length of {@link #EMPTY} on the X and Z axes. */
    public static final int EMPTY_WIDTH = 9;

    /**
     * Lays a stone floor across the bottom layer of the test area, so entities spawned at
     * relative y = 1 land inside the bounds instead of falling out of them.
     */
    public static void placeFloor(GameTestHelper helper) {
        for (int x = 0; x < EMPTY_WIDTH; x++) {
            for (int z = 0; z < EMPTY_WIDTH; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private GameTestTemplates() {}
}
