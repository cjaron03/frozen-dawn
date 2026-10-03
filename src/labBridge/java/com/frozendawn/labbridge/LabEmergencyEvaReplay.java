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
                && (name.equals("Emergency EVA Respawn Lab") || name.equals(RECOVERY_WORLD))
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
        if (!allowed(source) || !source.getServer().getWorldData().getLevelName().equals(RECOVERY_WORLD)
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
        if (respawn == null) return 0;
        respawn = new BlockPos(respawn.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                respawn.getX(), respawn.getZ()), respawn.getZ());
        BlockPos base = findGround(level, respawn.getX() + 320, respawn.getZ());
        if (base == null) return 0;
        // Load every room chunk before commands. The former distant-room preview
        // attempted fill on unloaded chunks and then teleported above a missing floor.
        for (int x = (base.getX() - 8) >> 4; x <= (base.getX() + 8) >> 4; x++) {
            for (int z = (base.getZ() - 8) >> 4; z <= (base.getZ() + 8) >> 4; z++) level.getChunk(x, z);
        }
        source.getServer().getCommands().performPrefixedCommand(
                source.withPosition(Vec3.atBottomCenterOf(base)), "function emergency_eva:base");
        if (!level.getBlockState(base.below()).is(Blocks.STONE)
                || !level.getBlockState(base.offset(0, 5, 0)).is(Blocks.STONE)
                || !level.getBlockState(base.offset(3, 0, 0)).is(Blocks.CHEST)
                || !level.getBlockState(base.offset(-3, 0, 0)).is(com.frozendawn.init.ModBlocks.THERMAL_HEATER.get())) {
            source.sendFailure(Component.literal("Recovery base failed validation; no teleport or survival start occurred."));
            return 0;
        }
        level.setDefaultSpawnPos(respawn, 0);
        player.setRespawnPosition(Level.OVERWORLD, respawn, 0, true, false);
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
    private static BlockPos findGround(ServerLevel level, int x, int z) {
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
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + ox, cz + oz);
                        BlockPos floor = new BlockPos(cx + ox, y - 1, cz + oz);
                        dry &= level.getFluidState(floor).isEmpty()
                                && level.getBlockState(floor).isSolidRender(level, floor)
                                && !level.getBlockState(floor).is(Blocks.POWDER_SNOW);
                        min = Math.min(min, y); max = Math.max(max, y);
                    }
                }
                if (dry && max - min <= 2) return new BlockPos(cx, max, cz);
            }
        }
        return null;
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
