package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ContinuityRecoveryState;
import com.frozendawn.data.EmergencyEvaState;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.EmergencyEvaArmorItem;
import com.frozendawn.network.ContinuityRecoveryPayload;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContinuityRecoveryGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void continuityRecordsSuccessfulBedsAndAnchors(GameTestHelper helper) {
        EmergencyEvaGameTest.scene(helper, player -> {
            var bed = helper.absolutePos(new BlockPos(3, 2, 4));
            helper.getLevel().setBlockAndUpdate(bed, Blocks.RED_BED.defaultBlockState());
            player.setRespawnPosition(player.level().dimension(), bed, 0, false, false);
            var record = player.getData(ModAttachments.CONTINUITY_RECOVERY);
            helper.assertTrue(GlobalPos.of(player.level().dimension(), bed).equals(record.shelter()),
                    "Actual successful spawn update automatically records the bed");
            var estimate = record.shelterEstimate();
            helper.getLevel().setBlockAndUpdate(bed, Blocks.AIR.defaultBlockState());
            player.setRespawnPosition(Level.OVERWORLD, null, 0, false, false);
            helper.assertTrue(estimate.equals(record.shelterEstimate()), "Destroyed/reset bed retains historical fix");
            var anchor = bed.east(2);
            helper.getLevel().setBlockAndUpdate(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState()
                    .setValue(RespawnAnchorBlock.CHARGE, 1));
            player.setRespawnPosition(player.level().dimension(), anchor, 0, false, false);
            helper.assertTrue(GlobalPos.of(player.level().dimension(), anchor).equals(record.shelter()),
                    "Charged anchor updates the archive");
            player.setRespawnPosition(player.level().dimension(), bed.east(3), 0, true, false);
            helper.assertTrue(record.shelter().pos().equals(anchor), "Forced command spawn is not a new shelter");

            java.util.function.Consumer<PlayerSetSpawnEvent> cancel = event -> {
                if (event.getEntity() == player) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, cancel);
            try {
                helper.getLevel().setBlockAndUpdate(bed, Blocks.RED_BED.defaultBlockState());
                player.setRespawnPosition(player.level().dimension(), bed, 0, false, false);
                helper.assertTrue(record.shelter().pos().equals(anchor), "Canceled spawn never changes recorded shelter");
            } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void continuityFixStaysBoundedAndPrivateAcrossReload(GameTestHelper helper) {
        EmergencyEvaGameTest.scene(helper, player -> {
            var bed = GlobalPos.of(Level.OVERWORLD, player.blockPosition());
            for (int i = 0; i < 1000; i++) {
                var state = new ContinuityRecoveryState();
                state.recordShelter(bed, new UUID(i * 71L, i * 1337L));
                var fix = state.shelterEstimate();
                int dx = fix.pos().getX() - bed.pos().getX(), dz = fix.pos().getZ() - bed.pos().getZ();
                helper.assertTrue(dx * dx + dz * dz <= 256, "Every estimate is within sixteen horizontal blocks");
                helper.assertTrue(dx != 0 || dz != 0, "Degraded record does not silently expose exact shelter");
                var restored = new ContinuityRecoveryState();
                restored.deserializeNBT(player.registryAccess(), state.serializeNBT(player.registryAccess()));
                helper.assertTrue(fix.equals(restored.shelterEstimate()), "Reload never rerolls the estimate");
                restored.recordShelter(bed, UUID.randomUUID());
                helper.assertTrue(fix.equals(restored.shelterEstimate()), "Re-registering the same bed preserves the fix");
            }
            var state = player.getData(ModAttachments.CONTINUITY_RECOVERY);
            state.recordShelter(bed, player.getUUID());
            state.recordLoss(GlobalPos.of(Level.NETHER, bed.pos().above(7)), true);
            var buffer = Unpooled.buffer();
            try {
                ContinuityRecoveryPayload.STREAM_CODEC.encode(buffer, new ContinuityRecoveryPayload(state.clientRecord()));
                var payload = ContinuityRecoveryPayload.STREAM_CODEC.decode(buffer);
                helper.assertFalse(payload.record().contains("shelter"), "Exact shelter is never sent to the HUD");
                var client = new ContinuityRecoveryState();
                client.deserializeNBT(player.registryAccess(), payload.record());
                helper.assertTrue(client.shelter() == null && client.shelterEstimate().equals(state.shelterEstimate()),
                        "Client receives only saved approximate shelter");
                helper.assertTrue(client.telemetry().dimension().equals(Level.NETHER), "Dimension survives network codec");
            } finally { buffer.release(); }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void continuityArchiveSurvivesDeathAndMigratesOldBeds(GameTestHelper helper) {
        EmergencyEvaGameTest.scene(helper, player -> {
            var bed = helper.absolutePos(new BlockPos(3, 2, 4));
            helper.getLevel().setBlockAndUpdate(bed, Blocks.RED_BED.defaultBlockState());
            player.setRespawnPosition(player.level().dimension(), bed, 0, false, false);
            player.removeData(ModAttachments.CONTINUITY_RECOVERY);
            ContinuityRecoveryHandler.initialize(player);
            var old = player.getData(ModAttachments.CONTINUITY_RECOVERY);
            helper.assertTrue(old.shelter().pos().equals(bed), "Existing saved bed requires no new registration");
            old.recordLoss(GlobalPos.of(player.level().dimension(), bed.east(100)), true);
            old.selectShelter(true);
            var replacement = new ServerPlayer(player.getServer(), player.serverLevel(), player.getGameProfile(),
                    ClientInformation.createDefault());
            replacement.connection = player.connection;
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, player, true));
            var copied = replacement.getData(ModAttachments.CONTINUITY_RECOVERY);
            helper.assertTrue(copied != old && copied.telemetry().equals(old.telemetry())
                    && copied.shelterEstimate().equals(old.shelterEstimate()) && copied.shelterSelected(),
                    "Real clone event copies archive across death");
            copied.recordLoss(GlobalPos.of(Level.END, bed), true);
            helper.assertFalse(copied.telemetry().equals(old.telemetry()), "Players do not share mutable archive state");
            helper.getLevel().setBlockAndUpdate(bed, Blocks.AIR.defaultBlockState());
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void continuityEmptyEmergencyDeathPreservesRecoveryTarget(GameTestHelper helper) {
        EmergencyEvaGameTest.scene(helper, player -> {
            EmergencyEvaGameTest.setProgress(player, 0.90F);
            var ordinary = new ItemEntity(player.level(), 0, 0, 0, new ItemStack(ModItems.EVA_CHESTPLATE.get()));
            var drops = new ArrayList<ItemEntity>(); drops.add(ordinary);
            NeoForge.EVENT_BUS.post(new LivingDropsEvent(player, player.damageSources().generic(), drops, false));
            var original = player.getData(ModAttachments.CONTINUITY_RECOVERY).telemetry();
            helper.assertTrue(ordinary.lifespan == 18000, "Original gear receives fifteen ticking minutes");
            EmergencyEvaHandler.issueKit(player);
            player.setPos(player.position().add(20, 0, 20));
            var issued = new ItemEntity(player.level(), 0, 0, 0, player.getItemBySlot(EquipmentSlot.CHEST).copy());
            drops = new ArrayList<>(); drops.add(issued);
            NeoForge.EVENT_BUS.post(new LivingDropsEvent(player, player.damageSources().generic(), drops, false));
            helper.assertTrue(drops.isEmpty(), "Emergency pieces do not become loot");
            helper.assertTrue(original.equals(player.getData(ModAttachments.CONTINUITY_RECOVERY).telemetry()),
                    "Empty emergency death does not move original destination");
            helper.assertTrue(ordinary.lifespan == 18000, "Replacement kit cannot refresh older drops");
            EmergencyEvaGameTest.setProgress(player, 0.50F);
            var earlier = new ItemEntity(player.level(), 0, 0, 0, new ItemStack(Items.DIAMOND));
            NeoForge.EVENT_BUS.post(new LivingDropsEvent(player, player.damageSources().generic(),
                    new ArrayList<>(java.util.List.of(earlier)), false));
            helper.assertTrue(earlier.lifespan == 6000, "Earlier phases keep normal drop lifetime");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void continuityDropLifetimePersistsAndCannotMergeRefresh(GameTestHelper helper) {
        EmergencyEvaGameTest.scene(helper, player -> {
            EmergencyEvaGameTest.setProgress(player, 0.90F);
            var drop = new ItemEntity(player.level(), 0, 0, 0, new ItemStack(Items.DIAMOND, 2));
            NeoForge.EVENT_BUS.post(new LivingDropsEvent(player, player.damageSources().generic(),
                    new ArrayList<>(java.util.List.of(drop)), false));
            var saved = new CompoundTag(); drop.saveWithoutId(saved); saved.putShort("Age", (short) 17999);
            var restored = new ItemEntity(player.level(), 0, 0, 0, new ItemStack(Items.DIAMOND, 2));
            restored.load(saved); restored.setPos(player.position()); restored.setNoGravity(true); restored.setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(restored.getAge() == 17999 && restored.lifespan == 18000
                    && ContinuityRecoveryHandler.isRecoveryDrop(restored), "Saved drop keeps its age and bounded extension");
            var younger = new ItemEntity(player.level(), 0, 0, 0, new ItemStack(Items.DIAMOND, 1));
            try {
                var merge = ItemEntity.class.getDeclaredMethod("tryToMerge", ItemEntity.class);
                merge.setAccessible(true); merge.invoke(restored, younger);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            helper.assertTrue(restored.getAge() == 17999 && younger.getItem().getCount() == 1 && younger.isAlive(),
                    "Younger identical items cannot merge and refresh recovery age");
            restored.tick();
            helper.assertTrue(restored.isRemoved(), "Ordinary expiry actually removes the item at fifteen minutes");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void continuityCombatDoesNotBreakClockAndExpiryRemovesArmor(GameTestHelper helper) {
        EmergencyEvaGameTest.scene(helper, player -> {
            EmergencyEvaGameTest.setProgress(player, 0.90F);
            EmergencyEvaHandler.issueKit(player);
            var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            helmet.hurtAndBreak(1000, player, EquipmentSlot.HEAD);
            helper.assertTrue(!helmet.isEmpty() && EmergencyEvaHandler.hasLifeSupport(player),
                    "Physical wear cannot terminate reserve early");
            player.setHealth(20); player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), 4);
            helper.assertTrue(player.getHealth() < 20, "Emergency service does not grant damage immunity");
            var issue = player.getData(ModAttachments.EMERGENCY_EVA).issue();
            ContinuityRecoveryHandler.selectTarget(player, issue, true);
            helper.assertTrue(player.getData(ModAttachments.CONTINUITY_RECOVERY).shelterSelected(),
                    "Current issue can choose recorded shelter");
            var reload = new ContinuityRecoveryState();
            reload.deserializeNBT(player.registryAccess(), player.getData(ModAttachments.CONTINUITY_RECOVERY)
                    .serializeNBT(player.registryAccess()));
            helper.assertTrue(reload.shelterSelected(), "Reload preserves the chosen destination");
            ContinuityRecoveryHandler.selectTarget(player, UUID.randomUUID(), false);
            helper.assertTrue(player.getData(ModAttachments.CONTINUITY_RECOVERY).shelterSelected(),
                    "Stale issue packet cannot change the current destination");
            player.setData(ModAttachments.EMERGENCY_EVA, new EmergencyEvaState(issue, 6001));
            EmergencyEvaHandler.tick(player);
            var item = (EmergencyEvaArmorItem) helmet.getItem();
            helper.assertTrue(item.getBarWidth(helmet) == 4, "Five-minute bar follows fifteen-minute service power");
            helper.assertTrue(!helmet.getAttributeModifiers().modifiers().isEmpty(), "Active issue retains weak armor");
            player.setData(ModAttachments.EMERGENCY_EVA, new EmergencyEvaState(issue, 1));
            EmergencyEvaHandler.tick(player);
            helper.assertTrue(!EmergencyEvaHandler.hasLifeSupport(player) && item.getBarWidth(helmet) == 0,
                    "Life support and item bar expire together");
            helper.assertTrue(helmet.getAttributeModifiers().modifiers().isEmpty(), "Spent kit grants no armor attributes");
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Expired issued gear is removed");
            ContinuityRecoveryHandler.selectTarget(player, issue, false);
            helper.assertTrue(player.getData(ModAttachments.CONTINUITY_RECOVERY).shelterSelected(),
                    "Expired issue cannot operate navigation");
            var stranger = new net.neoforged.neoforge.common.util.FakePlayer(player.serverLevel(),
                    new GameProfile(UUID.randomUUID(), "continuity_other"));
            EmergencyEvaHandler.issueKit(player);
            helper.assertFalse(player.getData(ModAttachments.CONTINUITY_RECOVERY).shelterSelected(),
                    "Fresh issue starts with the equipment destination");
            stranger.setItemSlot(EquipmentSlot.CHEST, player.getItemBySlot(EquipmentSlot.CHEST).copy());
            EmergencyEvaHandler.tick(stranger);
            helper.assertTrue(stranger.getItemBySlot(EquipmentSlot.CHEST).getAttributeModifiers().modifiers().isEmpty(),
                    "Transferred kit cannot supply another player with permanent armor");
        });
    }
}
