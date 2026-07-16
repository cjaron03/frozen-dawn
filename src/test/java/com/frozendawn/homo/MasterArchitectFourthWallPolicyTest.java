package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.MasterArchitectCombatAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterArchitectFourthWallPolicyTest {

    @Test
    void onlyLivingPeacefulNearbyMasterCanBeginTheMoment() {
        double inRange = HearthMasterArchitectPolicy.WATCH_DISTANCE
                * HearthMasterArchitectPolicy.WATCH_DISTANCE;
        assertTrue(MasterArchitectFourthWallPolicy.isPeacefulWatchEligible(
                true, true, 0, MasterArchitectCombatAction.IDLE,
                ReturnedHearthSavedData.HiveRelationship.NEUTRAL, inRange));
        assertTrue(MasterArchitectFourthWallPolicy.isPeacefulWatchEligible(
                true, true, 0, MasterArchitectCombatAction.IDLE,
                ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS, inRange));
        assertFalse(MasterArchitectFourthWallPolicy.isPeacefulWatchEligible(
                true, true, 0, MasterArchitectCombatAction.IDLE,
                ReturnedHearthSavedData.HiveRelationship.ORSATHAE, inRange));
        assertFalse(MasterArchitectFourthWallPolicy.isPeacefulWatchEligible(
                false, true, 0, MasterArchitectCombatAction.IDLE,
                ReturnedHearthSavedData.HiveRelationship.NEUTRAL, inRange));
        assertFalse(MasterArchitectFourthWallPolicy.isPeacefulWatchEligible(
                true, true, 0, MasterArchitectCombatAction.THERMAL_SEVER,
                ReturnedHearthSavedData.HiveRelationship.NEUTRAL, inRange));
        assertFalse(MasterArchitectFourthWallPolicy.isPeacefulWatchEligible(
                true, true, 0, MasterArchitectCombatAction.IDLE,
                ReturnedHearthSavedData.HiveRelationship.NEUTRAL, inRange + 0.01D));
    }

    @Test
    void eyeContactRequiresThreeSecondsAndAnUnobstructedVisibleCamera() {
        assertTrue(MasterArchitectFourthWallPolicy.hasCameraEyeContact(
                MasterArchitectFourthWallPolicy.CAMERA_DOT_THRESHOLD, true));
        assertFalse(MasterArchitectFourthWallPolicy.hasCameraEyeContact(
                MasterArchitectFourthWallPolicy.CAMERA_DOT_THRESHOLD - 0.001D, true));
        assertFalse(MasterArchitectFourthWallPolicy.hasCameraEyeContact(1.0D, false));
        assertFalse(MasterArchitectFourthWallPolicy.contactComplete(59));
        assertTrue(MasterArchitectFourthWallPolicy.contactComplete(60));
    }

    @Test
    void cameraTrackingRemainsUnlockedWhenTheMomentCompletes() {
        assertTrue(MasterArchitectFourthWallPolicy.shouldTrackCamera(
                false, true, true, true, true));
        assertTrue(MasterArchitectFourthWallPolicy.shouldTrackCamera(
                true, false, true, true, true));
        assertFalse(MasterArchitectFourthWallPolicy.shouldTrackCamera(
                false, false, true, true, true));
        assertFalse(MasterArchitectFourthWallPolicy.shouldTrackCamera(
                true, false, true, false, true));
        assertFalse(MasterArchitectFourthWallPolicy.shouldTrackCamera(
                true, false, true, true, false));
    }

    @Test
    void weatherAudioFadesOutThenReturnsWithoutStayingMuted() {
        assertEquals(1.0F, MasterArchitectFourthWallPolicy.weatherAudioMultiplier(-1));
        assertEquals(1.0F, MasterArchitectFourthWallPolicy.weatherAudioMultiplier(0));
        assertEquals(0.5F, MasterArchitectFourthWallPolicy.weatherAudioMultiplier(10));
        assertEquals(0.0F, MasterArchitectFourthWallPolicy.weatherAudioMultiplier(20));
        assertEquals(0.0F, MasterArchitectFourthWallPolicy.weatherAudioMultiplier(69));
        assertEquals(0.5F, MasterArchitectFourthWallPolicy.weatherAudioMultiplier(85));
        assertEquals(1.0F, MasterArchitectFourthWallPolicy.weatherAudioMultiplier(100));
    }
}
