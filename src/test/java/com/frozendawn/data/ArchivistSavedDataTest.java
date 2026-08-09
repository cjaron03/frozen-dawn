package com.frozendawn.data;

import com.frozendawn.homo.ArchivistPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArchivistSavedDataTest {
    @Test
    void regionBindingAndReplacementCooldownPersist() {
        ArchivistSavedData data = new ArchivistSavedData();
        long region = ArchivistPolicy.regionKey(new BlockPos(128, 70, -128));
        var site = data.ensureSite(region, new BlockPos(140, 71, -120), 88L);
        UUID entityId = UUID.randomUUID();
        data.bindArchivist(region, entityId, new BlockPos(150, 72, -116), site.id());
        data.releaseArchivist(region, entityId, 4_000L);

        var record = data.existingRegion(region).orElseThrow();
        assertTrue(record.activeArchivistId().isEmpty());
        assertEquals(4_000L + ArchivistPolicy.REPLACEMENT_COOLDOWN_TICKS,
                record.nextSpawnGameTime());
        assertEquals(site.id(), record.siteId().orElseThrow());
    }

    @Test
    void relicClaimIsAuthoritativeAndCannotDuplicate() {
        ArchivistSavedData data = new ArchivistSavedData();
        var site = data.ensureSite(7L, BlockPos.ZERO, 91L);
        var relic = data.addRelic(site.id(), new ItemStack(Items.DIAMOND, 3), false)
                .orElseThrow();

        ItemStack claimed = data.claimRelic(site.id(), relic.id()).orElseThrow();
        assertEquals(3, claimed.getCount());
        assertTrue(data.claimRelic(site.id(), relic.id()).isEmpty());
        assertEquals(0, data.relicCount());
    }

    @Test
    void completeItemComponentsSurviveSavedDataRoundTrip() {
        RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(
                BuiltInRegistries.REGISTRY);
        ArchivistSavedData original = new ArchivistSavedData();
        var site = original.ensureSite(13L, new BlockPos(3, 70, 4), 121L);
        ItemStack namedTool = new ItemStack(Items.DIAMOND_PICKAXE);
        namedTool.setDamageValue(73);
        namedTool.set(DataComponents.CUSTOM_NAME, Component.literal("Kept exactly"));
        original.addRelic(site.id(), namedTool, false).orElseThrow();

        CompoundTag encoded = original.save(new CompoundTag(), registries);
        ArchivistSavedData restored = ArchivistSavedData.load(encoded, registries);
        ItemStack stack = restored.siteForRegion(13L).orElseThrow()
                .relics().iterator().next().stack();

        assertTrue(stack.is(Items.DIAMOND_PICKAXE));
        assertEquals(73, stack.getDamageValue());
        assertEquals("Kept exactly", stack.getHoverName().getString());
    }
}
