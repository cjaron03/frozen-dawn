package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ShepherdPolicyTest {
    private static final double NO_HOSTILE = Double.MAX_VALUE;

    @Test
    void talkClosesOnDistanceHurtOrDanger() {
        assertFalse(ShepherdPolicy.talkMustClose(8, false, NO_HOSTILE));
        assertTrue(ShepherdPolicy.talkMustClose(8.01, false, NO_HOSTILE));
        assertTrue(ShepherdPolicy.talkMustClose(2, true, NO_HOSTILE));
        assertTrue(ShepherdPolicy.talkMustClose(2, false, 12));
        assertFalse(ShepherdPolicy.talkMustClose(2, false, 13));
    }

    @Test
    void savedDataRoundTrips() {
        var data = new ShepherdSavedData();
        UUID entity = UUID.randomUUID(), follower = UUID.randomUUID();
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("frozendawn", "test"));
        data.stage(ShepherdSavedData.Stage.AWAKE);
        data.entity(entity);
        data.dimension(dimension);
        data.lastPos(new BlockPos(3, -40, 900));
        data.follower(follower);
        data.waiting(true);
        data.lamp(false);
        data.affinity(-35);
        data.flag("used_flashlight", true);
        data.resumeNode("fm.name");
        data.heavy(true);

        var back = ShepherdSavedData.load(data.save(new CompoundTag(), null), null);

        assertEquals(ShepherdSavedData.Stage.AWAKE, back.stage());
        assertEquals(entity, back.entity());
        assertEquals(dimension, back.dimension());
        assertEquals(new BlockPos(3, -40, 900), back.lastPos());
        assertEquals(follower, back.follower());
        assertTrue(back.waiting());
        assertFalse(back.lamp());
        assertEquals(-35, back.affinity());
        assertTrue(back.flag("used_flashlight"));
        assertFalse(back.flag("profanity_heavy"));
        assertEquals("fm.name", back.resumeNode());
        assertTrue(back.heavy());
    }

    @Test
    void aFreshWorldHasHerDormantWithNothingRemembered() {
        var back = ShepherdSavedData.load(new ShepherdSavedData().save(new CompoundTag(), null), null);
        assertEquals(ShepherdSavedData.Stage.DORMANT, back.stage());
        assertNull(back.entity());
        assertNull(back.dimension());
        assertNull(back.lastPos());
        assertNull(back.follower());
        assertNull(back.resumeNode());
        assertTrue(back.lamp());
    }

    @Test
    void affinityClamps() {
        var data = new ShepherdSavedData();
        data.affinity(5000);
        assertEquals(1000, data.affinity());
        data.affinity(-5000);
        assertEquals(-1000, data.affinity());
    }

    @Test
    void calmNeedsNoHostileAndNoRecentHurt() {
        assertTrue(ShepherdPolicy.calm(24.1, 600));
        assertFalse(ShepherdPolicy.calm(24, 600), "A hostile at the line is within it");
        assertFalse(ShepherdPolicy.calm(100, 599), "Hurt in the last 30 seconds");
    }

    @Test
    void theDoorBoxCoversTheCotRoomSide() {
        assertTrue(ShepherdPolicy.atLastDoor(96, -14, -3));
        assertTrue(ShepherdPolicy.atLastDoor(100, -11, 3));
        assertFalse(ShepherdPolicy.atLastDoor(95, -14, 0), "The Morgue's door");
        assertFalse(ShepherdPolicy.atLastDoor(101, -14, 0), "Still in the vault");
        assertFalse(ShepherdPolicy.atLastDoor(98, -14, 4));
        assertFalse(ShepherdPolicy.atLastDoor(98, -15, 0));
        assertFalse(ShepherdPolicy.atLastDoor(98, -10, 0));
    }

    @Test
    void theExitBoxCoversTheLadder() {
        assertTrue(ShepherdPolicy.atVaultExit(-2, -14, -2), "The landing");
        assertTrue(ShepherdPolicy.atVaultExit(0, 0, 0), "The top of the ladder");
        assertFalse(ShepherdPolicy.atVaultExit(3, -14, 0), "Door 1");
        assertFalse(ShepherdPolicy.atVaultExit(0, 1, 0), "Out in the hall");
        assertFalse(ShepherdPolicy.atVaultExit(0, -15, 0));
        assertFalse(ShepherdPolicy.atVaultExit(0, -14, 3));
    }
}
