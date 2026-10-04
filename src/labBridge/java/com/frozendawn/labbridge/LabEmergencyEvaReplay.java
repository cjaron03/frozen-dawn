package com.frozendawn.labbridge;

import com.frozendawn.data.EmergencyEvaState;
import com.frozendawn.event.EmergencyEvaHandler;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModDataComponents;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Disposable emergency EVA preview controls. Excluded from the release jar. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabEmergencyEvaReplay {
    public static final String RECOVERY_WORLD = "Emergency EVA Recovery - Phase 6";
    public static final String CONTINUITY_WORLD = "ORSA Continuity - Phase 6";
    private LabEmergencyEvaReplay() {}
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("fdlab").requires(s -> s.hasPermission(2))
                .then(Commands.literal("emergency_eva")
                        .then(Commands.literal("access").executes(c -> allowed(c.getSource()) ? 1 : 0))
                        .then(Commands.literal("prepare_recovery").executes(c -> prepareRecovery(c.getSource())))
                        .then(Commands.literal("load_area").executes(c -> loadLegacyArea(c.getSource())))
                        .then(Commands.literal("reserve")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
                                        .executes(c -> shorten(c.getSource(), IntegerArgumentType.getInteger(c, "seconds")))))));
    }
    private static boolean allowed(CommandSourceStack source) {
        String name = source.getServer().getWorldData().getLevelName();
        return !FMLEnvironment.production && Boolean.getBoolean("frozendawn.labBridge")
                && (name.equals("Emergency EVA Respawn Lab") || name.equals(RECOVERY_WORLD) || name.equals(CONTINUITY_WORLD))
                && source.getEntity() instanceof ServerPlayer;
    }
    private static int loadLegacyArea(CommandSourceStack source) {
        if (!allowed(source) || !source.getServer().getWorldData().getLevelName().equals("Emergency EVA Respawn Lab")) return 0;
        var level = source.getServer().overworld();
        for (int x = 11990 >> 4; x <= 12010 >> 4; x++) {
            for (int z = 11990 >> 4; z <= 12010 >> 4; z++) level.getChunk(x, z);
        }
        return 1;
    }
    private static int prepareRecovery(CommandSourceStack source) {
        String worldName = source.getServer().getWorldData().getLevelName();
        if (!allowed(source) || !(worldName.equals(RECOVERY_WORLD) || worldName.equals(CONTINUITY_WORLD))
                || !(source.getEntity() instanceof ServerPlayer player) || !player.isCreative()
                || !player.getTags().contains("emergency_eva_lab")) return 0;
        var scoreboard = source.getServer().getScoreboard();
        var objective = scoreboard.getObjective("eeva");
        if (objective == null) return 0;
        var stage = scoreboard.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly("#stage"), objective);
        if (stage == null || stage.value() != -1) return 0;
        var level = source.getServer().overworld();
        BlockPos origin = level.getSharedSpawnPos();
        BlockPos respawn = findGround(level, origin.getX(), origin.getZ());
        if (respawn == null) return failed(source, "No dry respawn site within 64 blocks of world spawn");
        respawn = groundFeet(level, respawn.getX(), respawn.getZ());
        if (respawn == null) return failed(source, "Respawn column has no solid dry support");
        BlockPos base = findGround(level, respawn.getX() + 320, respawn.getZ());
        if (base == null) return failed(source, "No dry base site with at most four blocks of slope within 64 blocks of the target");
        // Load every room chunk before commands. The former distant-room preview
        // attempted fill on unloaded chunks and then teleported above a missing floor.
        for (int x = (base.getX() - 8) >> 4; x <= (base.getX() + 8) >> 4; x++) {
            for (int z = (base.getZ() - 8) >> 4; z <= (base.getZ() + 8) >> 4; z++) level.getChunk(x, z);
        }
        placeBase(level, base);
        if (!level.getBlockState(base.below()).is(Blocks.STONE)
                || !level.getBlockState(base.offset(0, 5, 0)).is(Blocks.STONE)
                || !level.getBlockState(base.offset(3, 0, 0)).is(Blocks.CHEST)
                || !level.getBlockState(base.offset(-3, 0, 0)).is(com.frozendawn.init.ModBlocks.THERMAL_HEATER.get())) {
            return failed(source, "Recovery base failed floor/roof/heater/chest validation at " + base);
        }
        if (!buildEntrance(level, base)) return failed(source, "Base entrance could not meet terrain within twelve steps at " + base);
        // Clear only the spawn's two standing blocks after locating solid ground.
        level.setBlockAndUpdate(respawn, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(respawn.above(), Blocks.AIR.defaultBlockState());
        level.setDefaultSpawnPos(respawn, 0);
        if (worldName.equals(CONTINUITY_WORLD)) {
            // Exercise a real lost-bed return: record a valid bed, then remove it.
            // Vanilla falls back to world spawn on death while ORSA retains the old fix.
            var bed = base.offset(2, 0, 2);
            var facing = net.minecraft.core.Direction.NORTH;
            var bedState = Blocks.WHITE_BED.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, facing);
            level.setBlockAndUpdate(bed, bedState);
            level.setBlockAndUpdate(bed.relative(facing), bedState
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            player.setRespawnPosition(Level.OVERWORLD, bed, 0, false, false);
            if (player.getData(ModAttachments.CONTINUITY_RECOVERY).shelterEstimate() == null) {
                return failed(source, "Continuity preview could not register the real bed");
            }
            level.setBlockAndUpdate(bed, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(bed.relative(facing), Blocks.AIR.defaultBlockState());
            player.sendSystemMessage(Component.literal("ORSA Continuity preview: a real bed was registered, then removed. "
                    + "Death will use ordinary world-spawn fallback. The damaged shelter record remains."));
        } else player.setRespawnPosition(Level.OVERWORLD, respawn, 0, true, false);
        player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 90, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.EVA_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.EVA_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.EVA_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.EVA_BOOTS.get()));
        var tank = new ItemStack(ModItems.O2_TANK_MK3.get());
        tank.set(ModDataComponents.O2_LEVEL.get(), 3600);
        player.getInventory().add(tank);
        player.getInventory().add(new ItemStack(Items.BREAD, 32));
        player.getInventory().add(new ItemStack(Items.IRON_SWORD));
        player.sendSystemMessage(Component.literal("BASE: " + base.getX() + ", " + base.getY() + ", " + base.getZ()
                + " | RESPAWN: " + respawn.getX() + ", " + respawn.getY() + ", " + respawn.getZ()));
        source.sendSuccess(() -> Component.literal("Verified solid terrain and loaded base floor, roof, heater and chest."), false);
        return 1;
    }
    static BlockPos findGround(ServerLevel level, int x, int z) {
        // A small dry, fairly level clearing keeps both spawn and the doorstep on terrain.
        for (int ring = 0; ring <= 4; ring++) {
            for (int side = 0; side < (ring == 0 ? 1 : 4); side++) {
                int dx = side == 0 ? ring * 16 : side == 1 ? -ring * 16 : 0;
                int dz = side == 2 ? ring * 16 : side == 3 ? -ring * 16 : 0;
                int cx = x + dx, cz = z + dz;
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                boolean dry = true;
                for (int ox = -6; ox <= 6; ox += 3) {
                    for (int oz = -6; oz <= 6; oz += 3) {
                        level.getChunk((cx + ox) >> 4, (cz + oz) >> 4);
                        BlockPos feet = groundFeet(level, cx + ox, cz + oz);
                        if (feet == null) { dry = false; continue; }
                        min = Math.min(min, feet.getY()); max = Math.max(max, feet.getY());
                    }
                }
                if (dry && max - min <= 4) return new BlockPos(cx, max, cz);
            }
        }
        return null;
    }
    static void placeBase(ServerLevel level, BlockPos base) {
        // Synchronous placement: nested /function calls inside a command are queued,
        // so validating immediately after dispatch could observe unbuilt geometry.
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            for (int y = -8; y <= -1; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = 0; y <= 5; y++) {
            boolean wall = Math.abs(x) == 5 || Math.abs(z) == 5 || y == 5;
            level.setBlockAndUpdate(base.offset(x, y, z), (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        var lowerDoor = Blocks.IRON_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH);
        level.setBlockAndUpdate(base.offset(0, 0, -5), lowerDoor);
        level.setBlockAndUpdate(base.offset(0, 1, -5), lowerDoor
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var button = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL);
        level.setBlockAndUpdate(base.offset(1, 1, -4), button
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, net.minecraft.core.Direction.SOUTH));
        level.setBlockAndUpdate(base.offset(1, 1, -6), button
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, net.minecraft.core.Direction.NORTH));
        var heater = base.offset(-3, 0, 0);
        level.setBlockAndUpdate(heater, com.frozendawn.init.ModBlocks.THERMAL_HEATER.get().defaultBlockState());
        ((com.frozendawn.block.ThermalHeaterBlockEntity) level.getBlockEntity(heater)).addFuel(240000);
        var chest = base.offset(3, 0, 0);
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.WEST));
        var inventory = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chest);
        inventory.setItem(0, new ItemStack(Items.BREAD, 64));
        inventory.setItem(1, new ItemStack(Items.COAL, 64));
        level.setBlockAndUpdate(base.offset(-3, 3, -3), Blocks.GLOWSTONE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(3, 3, 3), Blocks.GLOWSTONE.defaultBlockState());
    }
    static BlockPos groundFeet(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        for (int y = top; y >= Math.max(level.getMinBuildHeight(), top - 32); y--) {
            var pos = new BlockPos(x, y, z);
            var state = level.getBlockState(pos);
            if (!level.getFluidState(pos).isEmpty() || state.is(Blocks.POWDER_SNOW)) return null;
            if (com.frozendawn.world.SurfaceColumnScanner.shouldSkipForGroundScan(state)) continue;
            if (state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)
                    && state.isSolidRender(level, pos)) return pos.above();
        }
        return null;
    }
    private static boolean buildEntrance(ServerLevel level, BlockPos base) {
        for (int i = 0; i < 12; i++) {
            BlockPos step = base.offset(0, -1 - i, -7 - i);
            BlockPos feet = groundFeet(level, step.getX(), step.getZ());
            if (feet == null) return false;
            if (feet.getY() - 1 >= step.getY()) return true;
            for (int y = feet.getY(); y < step.getY(); y++) {
                level.setBlockAndUpdate(new BlockPos(step.getX(), y, step.getZ()), Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(step, Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.SOUTH));
            level.setBlockAndUpdate(step.above(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(step.above(2), Blocks.AIR.defaultBlockState());
        }
        return false;
    }
    private static int failed(CommandSourceStack source, String message) {
        com.frozendawn.FrozenDawn.LOGGER.warn("[EmergencyEvaLab] Preparation failed: {}", message);
        source.sendFailure(Component.literal(message));
        return 0;
    }
    private static int shorten(CommandSourceStack source, int seconds) {
        if (!allowed(source) || !(source.getEntity() instanceof ServerPlayer player)
                || !player.getTags().contains("emergency_eva_lab")
                || !EmergencyEvaHandler.isWearingIssuedPiece(player)) return 0;
        var current = player.getData(ModAttachments.EMERGENCY_EVA);
        player.setData(ModAttachments.EMERGENCY_EVA, new EmergencyEvaState(
                current.issue(), Math.min(current.remainingTicks(), seconds * 20)));
        return 1;
    }
}
