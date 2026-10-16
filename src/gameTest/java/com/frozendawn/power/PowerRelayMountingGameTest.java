package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.PowerRelayBlock;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerRelayMountingGameTest {
    @BeforeBatch(batch = "relay_mounting")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "relay_mounting", timeoutTicks = 100)
    public static void relayPlacementMountsBothSizesOnAllSixFaces(GameTestHelper h) {
        var level = h.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "relay_mount"));
        player.setPos(h.absolutePos(new BlockPos(10, 12, 10)).getCenter());
        for (var block : List.of(ModBlocks.POWER_RELAY.get(), ModBlocks.LARGE_POWER_RELAY.get())) {
            int index = 0;
            for (var face : Direction.values()) {
                var support = h.absolutePos(new BlockPos(3 + 3 * index++, 5, 4));
                var pos = support.relative(face);
                level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                var item = new ItemStack(block.asItem());
                var hit = new BlockHitResult(support.getCenter().relative(face, .5), face, support, false);
                var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, item, hit);
                h.assertTrue(((BlockItem) item.getItem()).place(context).consumesAction(), "Actual placement succeeds on " + face);
                var state = level.getBlockState(pos);
                h.assertTrue(state.is(block) && state.getValue(PowerRelayBlock.FACING) == face, "Cap points away from clicked surface: " + face);
                // Probe the broad foot and narrow stem, rather than just a full-block bounding box.
                var across = face.getAxis() == Direction.Axis.X ? Direction.UP : Direction.EAST;
                var foot = support.getCenter().relative(face, 9.0 / 16)
                        .subtract(pos.getX(), pos.getY(), pos.getZ()).relative(across, 5.0 / 16);
                var stemEdge = foot.relative(face, 7.0 / 16);
                h.assertTrue(state.getShape(level, pos).bounds().contains(foot)
                        && state.getShape(level, pos).toAabbs().stream().anyMatch(box -> box.contains(foot)), "Broad foot sits against " + face + " support");
                h.assertTrue(state.getShape(level, pos).toAabbs().stream().noneMatch(box -> box.contains(stemEdge)), "Hitbox rotates with narrow stem on " + face);
                var saved = NbtUtils.writeBlockState(state);
                h.assertTrue(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), saved).equals(state), "All six placements round-trip their saved orientation");
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(support, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "relay_mounting", timeoutTicks = 100)
    public static void relayLegacyStatesAndRotatedLinksRemainCompatible(GameTestHelper h) {
        var level = h.getLevel();
        for (var block : List.of(ModBlocks.POWER_RELAY.get(), ModBlocks.LARGE_POWER_RELAY.get(), ModBlocks.BULKHEAD_FEEDTHROUGH.get())) {
            var legacy = NbtUtils.writeBlockState(block.defaultBlockState());
            legacy.remove("Properties");
            var restored = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), legacy);
            h.assertTrue(restored.is(block) && restored.getValue(PowerRelayBlock.FACING) == Direction.UP, "Old palettes without facing retain upright relay/feedthrough");
        }
        var pos = h.absolutePos(new BlockPos(4, 4, 4));
        var other = pos.east(5);
        var block = ModBlocks.POWER_RELAY.get();
        level.setBlock(pos, block.defaultBlockState().setValue(PowerRelayBlock.FACING, Direction.NORTH), 3);
        level.setBlock(other, ModBlocks.LARGE_POWER_RELAY.get().defaultBlockState().setValue(PowerRelayBlock.FACING, Direction.DOWN), 3);
        h.assertTrue(PowerNetworks.link(level, pos, other) == PowerLinkRules.Result.OK, "Wall and ceiling relays use the normal graph");
        var turned = level.getBlockState(pos).rotate(Rotation.CLOCKWISE_90);
        h.assertTrue(turned.getValue(PowerRelayBlock.FACING) == Direction.EAST
                && turned.mirror(Mirror.FRONT_BACK).getValue(PowerRelayBlock.FACING) == Direction.WEST, "Structure rotation and mirror preserve attachment direction");
        level.setBlock(pos, turned, 3);
        for (int i = 0; i < 128; i++) PowerNetworks.update(level);
        var data = PowerNetworkSavedData.get(level);
        var loaded = PowerNetworkSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        h.assertTrue(data.links().contains(new PowerLink(pos, other)) && loaded.links().equals(data.links())
                && PowerNetworks.reading(level, pos).nodes() == 2, "Orientation update retains live and saved links");
        var feedthrough = ModBlocks.BULKHEAD_FEEDTHROUGH.get().defaultBlockState();
        h.assertTrue(!Shapes.joinIsNotEmpty(feedthrough.getShape(level, pos), Shapes.block(), BooleanOp.NOT_SAME), "Feedthrough remains a full airtight shape");
        h.succeed();
    }
}
