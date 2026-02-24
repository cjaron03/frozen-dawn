package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FrozenDawn.MOD_ID);

    // Grass Block -> Dead Grass Block (phase 2+). Drops dirt.
    public static final DeferredBlock<Block> DEAD_GRASS_BLOCK = BLOCKS.register("dead_grass_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(0.5F)
                    .sound(SoundType.GRAVEL)));

    // Dirt -> Frozen Dirt (phase 4+). Drops dirt + ice shard. 1.5x hardness.
    public static final DeferredBlock<Block> FROZEN_DIRT = BLOCKS.register("frozen_dirt",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.75F) // 1.5x dirt's 0.5
                    .sound(SoundType.STONE)));

    // Sand -> Frozen Sand / permafrost (phase 3+). Drops sand. NOT gravity-affected.
    public static final DeferredBlock<Block> FROZEN_SAND = BLOCKS.register("frozen_sand",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.75F)
                    .sound(SoundType.STONE)));

    // Logs -> Dead Log (phase 3+). Drops 2-4 sticks. Grey, cracked.
    public static final DeferredBlock<RotatedPillarBlock> DEAD_LOG = BLOCKS.register("dead_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.5F)
                    .sound(SoundType.WOOD)));

    // Dead Log -> Frozen Log (phase 4+). Drops 1-2 sticks + ice shard. Ice-encased.
    public static final DeferredBlock<RotatedPillarBlock> FROZEN_LOG = BLOCKS.register("frozen_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(2.5F)
                    .sound(SoundType.GLASS)));

    // Leaves -> Dead Leaves (phase 2+). Drops nothing (rare stick). Brown.
    public static final DeferredBlock<Block> DEAD_LEAVES = BLOCKS.register("dead_leaves",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.1F)
                    .sound(SoundType.GRASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .pushReaction(PushReaction.DESTROY)));

    // Dead Leaves -> Frozen Leaves (phase 4+). Drops ice shard. Shatters like glass.
    public static final DeferredBlock<Block> FROZEN_LEAVES = BLOCKS.register("frozen_leaves",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.1F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .pushReaction(PushReaction.DESTROY)));

    // Obsidian -> Frozen Obsidian (phase 5). Drops obsidian. Visual variant only.
    public static final DeferredBlock<Block> FROZEN_OBSIDIAN = BLOCKS.register("frozen_obsidian",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F)));

    // --- Player Agency blocks ---

    // Thermal Heater: right-click fuel, radius 7, +35C when lit
    public static final DeferredBlock<ThermalHeaterBlock> THERMAL_HEATER = BLOCKS.register("thermal_heater",
            () -> new ThermalHeaterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F)
                    .sound(SoundType.METAL)));

    // Insulated Glass: transparent, counts as shelter (roof check)
    public static final DeferredBlock<HalfTransparentBlock> INSULATED_GLASS = BLOCKS.register("insulated_glass",
            () -> new HalfTransparentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(0.5F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    // Frozen Coal Ore: coal ore that freezes in configurable phase, Y >= 0 only
    public static final DeferredBlock<Block> FROZEN_COAL_ORE = BLOCKS.register("frozen_coal_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F)
                    .sound(SoundType.STONE)));

    // Geothermal Core: endgame block, massive warm zone, light level 15
    public static final DeferredBlock<Block> GEOTHERMAL_CORE = BLOCKS.register("geothermal_core",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 15)));
}
