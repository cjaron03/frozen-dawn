package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.EmergencyEvaState;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.EmergencyEvaArmorItem;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmergencyEvaGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaRespawnOnlyInLatePhaseSix(GameTestHelper helper) {
        scene(helper, player -> {
            var state = ApocalypseState.get(player.getServer());
            for (float progress : new float[] {0.25F, 0.65F, 0.80F}) {
                setProgress(player, progress);
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
                helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "No emergency suit before vacuum");
            }
            setProgress(player, 0.90F);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, true));
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "End return is not death");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
            helper.assertTrue(MobFreezeHandler.getFullSetTier(player) == 3, "Real respawn subscriber equips sealed full rig");
            helper.assertTrue(EmergencyEvaHandler.remainingTicks(player) == 18000
                    && player.getData(ModAttachments.EMERGENCY_EVA).oxygenTicks() == 12000
                    && player.getData(ModAttachments.EMERGENCY_EVA).wornTicks() == 0, "Fifteen-minute service and ten-minute oxygen issued without prior age");
            for (var piece : player.getArmorSlots()) {
                helper.assertTrue(piece.getItem() instanceof EmergencyEvaArmorItem, "All four slots auto-equipped");
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaColdAndVacuumProtectionEndsAtExpiry(GameTestHelper helper) {
        // Deep ground is now a refuge; this protection/expiry contract needs genuine surface cold.
        var originalCenter = helper.absolutePos(new BlockPos(3, 2, 3));
        var preparedCenter = new BlockPos(originalCenter.getX(), 64, originalCenter.getZ());
        for (int x = -2; x <= 2; x++) for (int y = -1; y <= 3; y++) for (int z = -2; z <= 2; z++) {
            boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 3;
            helper.getLevel().setBlockAndUpdate(preparedCenter.offset(x, y, z),
                    (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        var heaterPos = preparedCenter.west();
        helper.getLevel().setBlockAndUpdate(heaterPos,
                com.frozendawn.init.ModBlocks.THERMAL_HEATER.get().defaultBlockState());
        ((com.frozendawn.block.ThermalHeaterBlockEntity) helper.getLevel().getBlockEntity(heaterPos)).addFuel(24000);
        // The sealed-air authority uses actual sky light; wait for roof lighting to propagate.
        helper.runAfterDelay(20, () -> scene(helper, player -> {
            player.setPos(preparedCenter.getCenter());
            setProgress(player, 1.0F);
            var center = player.blockPosition();
            PlayerTickHandler.syncBreathableState(player);
            helper.assertTrue(PlayerTickHandler.isPlayerBreathable(player),
                    "Fixture must be sealed: pos=" + center + " eye=" + player.getEyeY()
                    + " dimension=" + player.level().dimension()
                    + " roof=" + player.level().getBlockState(center.above(3))
                    + " sky=" + player.level().canSeeSky(center.above()));
            helper.assertTrue(PlayerTickHandler.getFreezeResolvedTemperature(player, ApocalypseState.get(player.getServer())) < -70,
                    "Fixture must have lethal indoor cold");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
            float health = player.getHealth();
            for (int t = 0; t < EmergencyEvaState.SERVICE_TICKS - 1; t++) {
                player.tickCount = t;
                EmergencyEvaHandler.tick(player);
                if (t % 40 == 0) MobFreezeHandler.onEntityTick(new EntityTickEvent.Post(player));
            }
            helper.assertTrue(player.getHealth() == health, "No environmental cold damage before reserve zero");
            helper.assertTrue(EmergencyEvaHandler.hasLifeSupport(player), "Last tick still protects");
            EmergencyEvaHandler.tick(player);
            helper.assertFalse(EmergencyEvaHandler.hasLifeSupport(player), "Reserve zero disables life support");
            helper.assertTrue(MobFreezeHandler.getFullSetTier(player) != 3, "Expired set loses climate control");
            player.tickCount = EmergencyEvaState.SERVICE_TICKS;
            player.invulnerableTime = 0;
            MobFreezeHandler.onEntityTick(new EntityTickEvent.Post(player));
            helper.assertTrue(player.getHealth() < health, "Real indoor cold damage returns at expiry: temp=" + PlayerTickHandler.getFreezeResolvedTemperature(player, ApocalypseState.get(player.getServer())) + " breathable=" + PlayerTickHandler.isPlayerBreathable(player) + " enabled=" + com.frozendawn.config.FrozenDawnConfig.ENABLE_MOB_FREEZING.get());

            // Remove the roof to test the real atmospheric suffocation path without ordinary tanks.
            player.serverLevel().setBlockAndUpdate(center.above(3), Blocks.AIR.defaultBlockState());
            EmergencyEvaHandler.issueKit(player);
            PlayerTickHandler.stabilizeAfterRescue(player);
            player.setHealth(20);
            try {
                for (int t = 0; t < 260; t++) suffocate(player);
                helper.assertTrue(player.getHealth() == 20, "Internal reserve prevents real vacuum damage");
                player.setData(ModAttachments.EMERGENCY_EVA,
                        new EmergencyEvaState(player.getData(ModAttachments.EMERGENCY_EVA).issue(), 0));
                for (int t = 0; t < 260; t++) {
                    player.invulnerableTime = 0;
                    suffocate(player);
                }
                helper.assertTrue(player.getHealth() < 20, "Real atmospheric damage returns at expiry");
            } finally {
                PlayerTickHandler.stabilizeAfterRescue(player);
            }
        }));
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaPersistsRemovalReloadAndRejectsOldKits(GameTestHelper helper) {
        scene(helper, player -> {
            setProgress(player, 0.90F);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
            for (int i = 0; i < 137; i++) { player.tickCount = i; EmergencyEvaHandler.tick(player); }
            int remaining = EmergencyEvaHandler.remainingTicks(player);
            var pieces = new java.util.ArrayList<ItemStack>();
            for (var slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                pieces.add(player.getItemBySlot(slot).copy());
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            for (int i = 0; i < 100; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(EmergencyEvaHandler.remainingTicks(player) == remaining, "Removing suit pauses shared reserve");
            var saved = player.getData(ModAttachments.EMERGENCY_EVA).serializeNBT(player.registryAccess());
            var reloaded = new EmergencyEvaState();
            reloaded.deserializeNBT(player.registryAccess(), saved);
            player.setData(ModAttachments.EMERGENCY_EVA, reloaded);
            player.setItemSlot(EquipmentSlot.CHEST, pieces.get(1));
            EmergencyEvaHandler.tick(player);
            helper.assertTrue(EmergencyEvaHandler.remainingTicks(player) == remaining - 1, "Reload and re-equip do not refill");
            var stranger = new FakePlayer(player.serverLevel(), new GameProfile(UUID.randomUUID(), "other_eva"));
            helper.assertFalse(EmergencyEvaHandler.isActivePiece(stranger, pieces.get(1)), "Transferred piece has no matching lease");
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(player, player, true));
            helper.assertFalse(EmergencyEvaHandler.isActivePiece(player, pieces.get(1)), "Death invalidates stored pieces");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
            helper.assertFalse(EmergencyEvaHandler.isActivePiece(player, pieces.get(1)), "New issue never revives old chest-stored gear");
            helper.assertTrue(EmergencyEvaHandler.remainingTicks(player) == 18000, "New death grants fresh fifteen-minute recovery attempt");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaRetainedGearAndNoRefill(GameTestHelper helper) {
        scene(helper, player -> {
            setProgress(player, 0.90F);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.EVA_HELMET.get()));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.EVA_CHESTPLATE.get()));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.EVA_LEGGINGS.get()));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.EVA_BOOTS.get()));
            var tank = new ItemStack(ModItems.O2_TANK.get());
            tank.set(ModDataComponents.O2_LEVEL, 200);
            player.getInventory().add(tank);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
            helper.assertFalse(EmergencyEvaHandler.isWearingIssuedPiece(player), "Retained working rig takes priority");
            player.getInventory().getItem(0).set(ModDataComponents.O2_LEVEL, 0);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
            helper.assertTrue(EmergencyEvaHandler.hasLifeSupport(player), "Retained rig without air gets reserve chest");
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.EVA_HELMET), "Retained helmet preserved");
            helper.assertTrue(player.getInventory().contains(new ItemStack(ModItems.EVA_CHESTPLATE.get())), "Displaced chest preserved");
            for (int i = 0; i < 100; i++) EmergencyEvaHandler.tick(player);
            int before = EmergencyEvaHandler.remainingTicks(player);
            SuitIntegrityHandler.useEmergencyO2Cartridge(player);
            SuitIntegrityHandler.stabilizeAfterRescue(player, 1.0F);
            helper.assertTrue(EmergencyEvaHandler.remainingTicks(player) == before, "Cartridges and rescue never refill service lease");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaDeathDropsExcludeIssuedEquipment(GameTestHelper helper) {
        scene(helper, player -> {
            setProgress(player, 0.90F);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
            var drops = new java.util.ArrayList<ItemEntity>();
            drops.add(new ItemEntity(player.level(), 0, 0, 0, player.getItemBySlot(EquipmentSlot.CHEST).copy()));
            drops.add(new ItemEntity(player.level(), 0, 0, 0, new ItemStack(ModItems.EVA_CHESTPLATE.get())));
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(
                    player, player.damageSources().generic(), drops, false));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(ModItems.EVA_CHESTPLATE),
                    "Issued gear disappears while original EVA still drops");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaExertionRecoversPersistsAndSyncs(GameTestHelper helper) {
        scene(helper, player -> {
            setProgress(player, 0.90F);
            EmergencyEvaHandler.issueKit(player);
            player.setSprinting(true);
            player.setKnownMovement(net.minecraft.world.phys.Vec3.ZERO);
            for (int i = 0; i < 100; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(EmergencyEvaHandler.remainingTicks(player) == 17900
                    && player.getData(ModAttachments.EMERGENCY_EVA).oxygenTicks() == 11900,
                    "Standing with sprint flag does not increase reserve draw");
            helper.assertTrue(player.getData(ModAttachments.EMERGENCY_EVA).exertionLoad() == 0,
                    "Actual movement required for exertion");
            player.setKnownMovement(new net.minecraft.world.phys.Vec3(0.2, 0, 0));
            for (int i = 0; i < 63; i++) EmergencyEvaHandler.tick(player);
            var state = player.getData(ModAttachments.EMERGENCY_EVA);
            helper.assertTrue(state.exertionLoad() == 200 && state.oxygenTicks() < 11837 && state.remainingTicks() == 17837,
                    "Real server tick ramps exertion and charges more reserve");
            var saved = state.serializeNBT(player.registryAccess());
            var restored = new EmergencyEvaState();
            restored.deserializeNBT(player.registryAccess(), saved);
            helper.assertTrue(restored.serializeNBT(player.registryAccess()).equals(saved),
                    "Load and fractional debit survive NBT serialization");
            var expected = state.copy();
            player.setData(ModAttachments.EMERGENCY_EVA, restored);
            for (int i = 0; i < 9; i++) {
                expected.tickWorn(true);
                EmergencyEvaHandler.tick(player);
            }
            helper.assertTrue(restored.oxygenTicks() == expected.oxygenTicks() && restored.remainingTicks() == expected.remainingTicks(),
                    "Reload never discards accrued fractional debit");
            var packet = new com.frozendawn.network.EmergencyEvaPayload(restored.issue(),
                    restored.remainingTicks(), restored.oxygenTicks(), restored.exertionLoad(), restored.wornTicks(),
                    restored.thermalLoad(), restored.ambientIntake(), restored.retirement(), 0);
            var bytes = io.netty.buffer.Unpooled.buffer();
            try {
                com.frozendawn.network.EmergencyEvaPayload.STREAM_CODEC.encode(bytes, packet);
                helper.assertTrue(packet.equals(com.frozendawn.network.EmergencyEvaPayload.STREAM_CODEC.decode(bytes)),
                        "Actual wire codec carries authoritative reserve and metabolic load");
            } finally { bytes.release(); }
            player.setSprinting(false);
            player.setKnownMovement(net.minecraft.world.phys.Vec3.ZERO);
            int beforeRecovery = restored.oxygenTicks();
            for (int i = 0; i < 200; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(restored.exertionLoad() == 0 && restored.oxygenTicks() < beforeRecovery - 200,
                    "Draw tapers during recovery rather than snapping to normal");
            int settled = restored.oxygenTicks();
            for (int i = 0; i < 100; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(restored.oxygenTicks() == settled - 100, "Settled draw is normal again");
            EmergencyEvaHandler.issueKit(player);
            helper.assertTrue(player.getData(ModAttachments.EMERGENCY_EVA).exertionLoad() == 0,
                    "Fresh death recovery issue does not inherit old load");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaSprintDrawCannotBypassExpiryAndWalkingRemainsTenMinutes(GameTestHelper helper) {
        scene(helper, player -> {
            setProgress(player, 0.90F);
            EmergencyEvaHandler.issueKit(player);
            player.setKnownMovement(new net.minecraft.world.phys.Vec3(0.12, 0, 0));
            for (int i = 0; i < 11999; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(EmergencyEvaHandler.remainingTicks(player) == 6001 && EmergencyEvaHandler.hasLifeSupport(player)
                    && player.getData(ModAttachments.EMERGENCY_EVA).oxygenTicks() == 1,
                    "Walking retains the full ten-minute oxygen reserve with five extra service minutes");
            EmergencyEvaHandler.tick(player);
            helper.assertFalse(EmergencyEvaHandler.hasLifeSupport(player), "Oxygen expires at ten minutes");
            helper.assertTrue(EmergencyEvaHandler.hasThermalSupport(player) && EmergencyEvaHandler.remainingTicks(player) == 6000,
                    "Thermal service still has five minutes after reserve oxygen expires");
            for (int i = 0; i < 5999; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(EmergencyEvaHandler.hasThermalSupport(player), "Thermal service protects through its final tick");
            EmergencyEvaHandler.tick(player);
            helper.assertFalse(EmergencyEvaHandler.hasThermalSupport(player), "Thermal service expires at fifteen minutes");
            EmergencyEvaHandler.issueKit(player);
            var issue = player.getData(ModAttachments.EMERGENCY_EVA).issue();
            player.setData(ModAttachments.EMERGENCY_EVA, new EmergencyEvaState(issue, 1, 200));
            player.setSprinting(true);
            player.setKnownMovement(new net.minecraft.world.phys.Vec3(0.2, 0, 0));
            EmergencyEvaHandler.tick(player);
            helper.assertTrue(EmergencyEvaHandler.remainingTicks(player) == 0, "Extra debit clamps at zero");
            helper.assertFalse(EmergencyEvaHandler.hasLifeSupport(player), "Sprinting expiry immediately ends support");
            for (var piece : player.getArmorSlots()) {
                helper.assertTrue(EmergencyEvaArmorItem.serviceTicks(piece) == 0,
                        "Equipment service bars agree with sprint exhaustion on the expiry tick");
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaCoolingAgeHeatPersistenceAndRecovery(GameTestHelper helper) {
        scene(helper, player -> {
            setProgress(player, 1.0F);
            EmergencyEvaHandler.issueKit(player);
            var issue = player.getData(ModAttachments.EMERGENCY_EVA).issue();
            player.setData(ModAttachments.EMERGENCY_EVA, new EmergencyEvaState(issue, 8000, 200, 5999, 0));
            player.setSprinting(true);
            player.setKnownMovement(new net.minecraft.world.phys.Vec3(0.2, 0, 0));
            EmergencyEvaHandler.tick(player);
            var state = player.getData(ModAttachments.EMERGENCY_EVA);
            helper.assertTrue(state.coolingDegraded() && state.thermalLoad() == 0,
                    "Five-minute worn clock crosses without an instant heat penalty or reserve threshold dependency");
            for (int i = 0; i < 100; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(state.thermalLoad() > 0 && !state.highThermalLoad(), "Short escape remains below high heat");
            player.setData(ModAttachments.EMERGENCY_EVA, new EmergencyEvaState(issue, 5000, 200, 7200, 0));
            for (int i = 0; i < 200; i++) EmergencyEvaHandler.tick(player);
            state = player.getData(ModAttachments.EMERGENCY_EVA);
            helper.assertTrue(state.highThermalLoad(), "Sustained real server sprint accumulates internal heat");
            var saved = state.serializeNBT(player.registryAccess());
            var restored = new EmergencyEvaState();
            restored.deserializeNBT(player.registryAccess(), saved);
            helper.assertTrue(saved.equals(restored.serializeNBT(player.registryAccess()))
                    && saved.equals(state.copy().serializeNBT(player.registryAccess())), "Reload and dimension copy retain clock, heat and debt");
            var packet = new com.frozendawn.network.EmergencyEvaPayload(restored.issue(), restored.remainingTicks(),
                    restored.oxygenTicks(), restored.exertionLoad(), restored.wornTicks(), restored.thermalLoad(),
                    restored.ambientIntake(), restored.retirement(), 0);
            var bytes = io.netty.buffer.Unpooled.buffer();
            try {
                com.frozendawn.network.EmergencyEvaPayload.STREAM_CODEC.encode(bytes, packet);
                helper.assertTrue(packet.equals(com.frozendawn.network.EmergencyEvaPayload.STREAM_CODEC.decode(bytes)),
                        "Real wire codec carries hot thermal state and actual worn age");
            } finally { bytes.release(); }
            player.setData(ModAttachments.EMERGENCY_EVA, restored);
            helper.assertTrue(EmergencyEvaHandler.hasLifeSupport(player), "Heat does not disable thermal or air protection");
            float health = player.getHealth();
            player.tickCount = 40;
            MobFreezeHandler.onEntityTick(new EntityTickEvent.Post(player));
            for (int i = 0; i < 80; i++) suffocate(player);
            helper.assertTrue(player.getHealth() == health, "Hot suit still prevents real cold and vacuum damage");
            player.setSprinting(false);
            for (int i = 0; i < 300; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(restored.thermalLoad() == 0 && restored.exertionLoad() == 0,
                    "Walking restores cool heat and normal metabolic draw");
            player.setSprinting(true);
            player.setKnownMovement(net.minecraft.world.phys.Vec3.ZERO);
            for (int i = 0; i < 100; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(restored.thermalLoad() == 0, "Stationary sprint flag cannot produce heat");
            for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
                player.setItemSlot(slot, ItemStack.EMPTY);
            int age = restored.wornTicks();
            int reserve = restored.remainingTicks();
            for (int i = 0; i < 100; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(restored.wornTicks() == age && restored.remainingTicks() == reserve,
                    "Removing all issued pieces pauses both clocks");
            var legacy = saved.copy();
            legacy.remove("wornTicks"); legacy.remove("thermalLoad");
            restored.deserializeNBT(player.registryAccess(), legacy);
            helper.assertTrue(restored.wornTicks() == Math.max(0, EmergencyEvaState.OXYGEN_TICKS - restored.remainingTicks())
                    && restored.thermalLoad() == 0, "Legacy saves preserve reserve and migrate age without invented heat");
            EmergencyEvaHandler.issueKit(player);
            var fresh = player.getData(ModAttachments.EMERGENCY_EVA);
            helper.assertTrue(fresh.wornTicks() == 0 && fresh.thermalLoad() == 0 && !fresh.coolingDegraded(),
                    "A new death recovery kit begins with its own fresh coolant clock");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaAmbientIsolatesOxygenWithoutExtendingThermalService(GameTestHelper helper) {
        // Keep the oxygen/service contract in lethal cold, independent of the deep-refuge balance.
        var originalCenter = helper.absolutePos(new BlockPos(3, 2, 3));
        var center = new BlockPos(originalCenter.getX(), 64, originalCenter.getZ());
        for (int x = -2; x <= 2; x++) for (int y = -1; y <= 3; y++) for (int z = -2; z <= 2; z++) {
            boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 3;
            helper.getLevel().setBlockAndUpdate(center.offset(x, y, z), (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        helper.runAfterDelay(20, () -> scene(helper, player -> {
            player.setPos(center.getCenter());
            setProgress(player, 1.0F);
            EmergencyEvaHandler.issueKit(player);
            PlayerTickHandler.syncBreathableState(player);
            helper.assertTrue(PlayerTickHandler.isPlayerBreathable(player), "Actual sealed chamber must contain breathable air");
            helper.assertTrue(PlayerTickHandler.getFreezeResolvedTemperature(player, ApocalypseState.get(player.getServer())) < -70,
                    "Breathable chamber must still be lethally cold without EVA");
            for (int i = 0; i < 39; i++) EmergencyEvaHandler.tick(player);
            var state = player.getData(ModAttachments.EMERGENCY_EVA);
            helper.assertFalse(state.ambientIntake(), "Forty stable ticks required before bypass");
            EmergencyEvaHandler.tick(player);
            helper.assertTrue(state.ambientIntake(), "Stable breathable authority opens intake");
            int oxygen = state.oxygenTicks(), service = state.remainingTicks();
            for (int i = 0; i < 100; i++) EmergencyEvaHandler.tick(player);
            helper.assertTrue(state.oxygenTicks() == oxygen && state.remainingTicks() == service - 100,
                    "Ambient air saves oxygen while thermal service counts down");
            float health = player.getHealth();
            player.tickCount = 40;
            MobFreezeHandler.onEntityTick(new EntityTickEvent.Post(player));
            helper.assertTrue(player.getHealth() == health && EmergencyEvaHandler.hasThermalSupport(player),
                    "Air bypass must preserve protection from real lethal indoor cold");
            var tag = state.serializeNBT(player.registryAccess());
            var restored = new EmergencyEvaState();
            restored.deserializeNBT(player.registryAccess(), tag);
            helper.assertTrue(tag.equals(restored.serializeNBT(player.registryAccess())), "Both clocks and intake survive save/load");
            player.setData(ModAttachments.EMERGENCY_EVA, restored);
            player.setPos(center.east(4).getCenter());
            PlayerTickHandler.syncBreathableState(player);
            helper.assertFalse(PlayerTickHandler.isPlayerBreathable(player), "Actual exterior must be unsafe");
            EmergencyEvaHandler.tick(player);
            helper.assertTrue(!restored.ambientIntake() && restored.oxygenTicks() == oxygen - 1,
                    "First unsafe observation closes the restored intake and resumes reserve draw");
            var legacy = tag.copy();
            legacy.remove("oxygenTicks"); legacy.remove("ambientTicks"); legacy.remove("retirement");
            legacy.remove("wornTicks"); legacy.remove("thermalLoad");
            legacy.putInt("remainingTicks", 8000);
            restored.deserializeNBT(player.registryAccess(), legacy);
            helper.assertTrue(restored.oxygenTicks() == 8000 && restored.remainingTicks() == 8000
                    && restored.wornTicks() == 4000 && !restored.ambientIntake(),
                    "Legacy shared budget becomes two equally spent clocks without a refill");
            player.setPos(center.getCenter());
            PlayerTickHandler.syncBreathableState(player);
            player.setData(ModAttachments.EMERGENCY_EVA, new EmergencyEvaState(state.issue(), 1, oxygen, 0, 11999, 0, true, 0));
            EmergencyEvaHandler.tick(player);
            var expired = player.getData(ModAttachments.EMERGENCY_EVA);
            helper.assertTrue(expired.retirement() == EmergencyEvaState.EXPIRED && !EmergencyEvaHandler.hasThermalSupport(player),
                    "Conserved oxygen cannot outlive thermal service");
            for (var piece : player.getArmorSlots()) helper.assertTrue(piece.isEmpty(), "Service expiry removes issued gear");
        }));
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void emergencyEvaPrimaryRigHandoffRequiresCompleteSealedOxygenSupply(GameTestHelper helper) {
        scene(helper, player -> {
            setProgress(player, 1.0F);
            EmergencyEvaHandler.issueKit(player);
            var state = player.getData(ModAttachments.EMERGENCY_EVA);
            var oldIssue = state.issue();
            var savedChest = player.getItemBySlot(EquipmentSlot.CHEST).copy();
            var slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            var items = new net.minecraft.world.item.Item[]{ModItems.EVA_HELMET.get(), ModItems.EVA_CHESTPLATE.get(),
                    ModItems.EVA_LEGGINGS.get(), ModItems.EVA_BOOTS.get()};
            player.getInventory().add(player.getItemBySlot(slots[0]).copy());
            player.setItemSlot(slots[0], new ItemStack(items[0]));
            EmergencyEvaHandler.tick(player);
            helper.assertTrue(!state.retired() && EmergencyEvaHandler.hasThermalSupport(player),
                    "One ordinary piece must not retire the needed mixed rig");
            for (int i = 1; i < 4; i++) {
                player.getInventory().add(player.getItemBySlot(slots[i]).copy());
                player.setItemSlot(slots[i], new ItemStack(items[i]));
            }
            int service = state.remainingTicks();
            EmergencyEvaHandler.tick(player);
            helper.assertTrue(!state.retired() && state.remainingTicks() == service,
                    "Complete armor without oxygen leaves the unworn emergency kit available and paused");
            var tank = new ItemStack(ModItems.O2_TANK.get());
            tank.set(ModDataComponents.O2_LEVEL, 1000);
            player.getInventory().add(tank);
            int tankSlot = -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++)
                if (player.getInventory().getItem(i).is(ModItems.O2_TANK.get())) { tankSlot = i; break; }
            helper.assertTrue(tankSlot >= 0, "Fixture must own the inserted primary tank");
            var beforePrimaryTank = player.getInventory().getItem(tankSlot).copy();
            player.getData(ModAttachments.SUIT_INTEGRITY).setPunctures(1);
            EmergencyEvaHandler.tick(player);
            helper.assertFalse(state.retired(), "Leaking primary rig cannot pass verification");
            player.getData(ModAttachments.SUIT_INTEGRITY).setPunctures(0);
            EmergencyEvaHandler.tick(player);
            helper.assertTrue(state.retirement() == EmergencyEvaState.HANDOFF && state.remainingTicks() == 0,
                    "Complete sealed ordinary EVA plus usable air permanently retires the issue");
            for (int i = 0; i < 4; i++) helper.assertTrue(player.getItemBySlot(slots[i]).is(items[i])
                    && player.getItemBySlot(slots[i]).getDamageValue() == 0, "Primary armor stays intact");
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) helper.assertFalse(
                    player.getInventory().getItem(i).getItem() instanceof EmergencyEvaArmorItem, "Old carried pieces removed");
            helper.assertFalse(EmergencyEvaHandler.isActivePiece(player, savedChest), "Stored copy is permanently invalid");
            helper.assertTrue(ItemStack.matches(beforePrimaryTank, player.getInventory().getItem(tankSlot)),
                    "Handoff preserves the actual stored primary tank's count and components");
            var restored = new EmergencyEvaState();
            restored.deserializeNBT(player.registryAccess(), state.serializeNBT(player.registryAccess()));
            player.setData(ModAttachments.EMERGENCY_EVA, restored);
            player.getInventory().add(savedChest.copy());
            EmergencyEvaHandler.tick(player);
            helper.assertTrue(restored.retired() && !EmergencyEvaHandler.isActivePiece(player, savedChest),
                    "Reload cannot revive the retired reserve");
            var packet = new com.frozendawn.network.EmergencyEvaPayload(oldIssue, 0, 0, restored.exertionLoad(),
                    restored.wornTicks(), restored.thermalLoad(), false, restored.retirement(),
                    com.frozendawn.network.EmergencyEvaPayload.HANDOFF_NOTICE);
            var bytes = io.netty.buffer.Unpooled.buffer();
            try {
                com.frozendawn.network.EmergencyEvaPayload.STREAM_CODEC.encode(bytes, packet);
                helper.assertTrue(packet.equals(com.frozendawn.network.EmergencyEvaPayload.STREAM_CODEC.decode(bytes)),
                        "Real wire codec preserves the one-shot handoff event and retired issue");
            } finally { bytes.release(); }
        });
    }

    private static void suffocate(ServerPlayer player) {
        PlayerTickHandler.tickPlayerSuffocation(player, ApocalypseState.get(player.getServer()), true);
    }
    static void setProgress(ServerPlayer player, float progress) {
        var state = ApocalypseState.get(player.getServer());
        state.setApocalypseTicks((long) Math.ceil(state.getTotalDays() * progress) * 24000, player.getServer());
    }
    static void scene(GameTestHelper helper, Consumer<ServerPlayer> test) {
        var server = helper.getLevel().getServer();
        var apocalypse = ApocalypseState.get(server);
        long originalTicks = apocalypse.getApocalypseTicks();
        var profile = new GameProfile(UUID.randomUUID(), "emergency_eva");
        var transport = new FakePlayer(helper.getLevel(), profile);
        var player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault()) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        player.connection = transport.connection;
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        // Clear vanilla spawn immunity so damage assertions exercise actual hazards.
        player.tickCount = 12000;
        try {
            var immunity = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            immunity.setAccessible(true);
            immunity.setInt(player, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        helper.getLevel().addNewPlayer(player);
        try { test.accept(player); helper.succeed(); }
        finally {
            apocalypse.setApocalypseTicks(originalTicks, server);
            PlayerTickHandler.onPlayerLogout(player);
            player.discard();
        }
    }
}
