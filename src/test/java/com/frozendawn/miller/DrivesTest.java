package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.Drives.Drive;
import org.junit.jupiter.api.Test;

class DrivesTest {
    private static Drives begun(long now) {
        var drives = new Drives();
        drives.begin(now);
        return drives;
    }

    @Test
    void everyDriveStartsAtTheBaseline() {
        var drives = begun(500);
        for (Drive drive : Drive.values()) assertEquals(Temperament.DRIVE_START, drives.value(drive, 500), 1e-12);
    }

    @Test
    void beforeSheWakesNoClockRuns() {
        for (Drive drive : Drive.values()) assertEquals(Temperament.DRIVE_START, new Drives().value(drive, 10_000_000), 0);
    }

    @Test
    void aDriveRisesToItsCeilingAndNoFurther() {
        for (Drive drive : Drive.values()) {
            double far = begun(0).value(drive, 100 * drive.riseHalfLife());
            assertEquals(drive.ceiling(), far, 1e-9, drive.name());
        }
    }

    @Test
    void theCeilingsAndRisesAreTheRulings() {
        assertEquals(1.0, Drive.COMPANY.ceiling());
        assertEquals(12000, Drive.COMPANY.riseHalfLife());
        assertEquals(1.0, Drive.SKY.ceiling());
        assertEquals(48000, Drive.SKY.riseHalfLife());
        assertEquals(0.8, Drive.OTHERS.ceiling());
        assertEquals(96000, Drive.OTHERS.riseHalfLife());
        assertEquals(0.8, Drive.STAYING_ON.ceiling());
        assertEquals(48000, Drive.STAYING_ON.riseHalfLife());
        assertEquals(1.0, Drive.CURIOSITY.ceiling());
        assertEquals(24000, Drive.CURIOSITY.riseHalfLife());
    }

    @Test
    void feedingLowersADriveAndStopsAtZero() {
        var drives = begun(0);
        drives.feed(Drive.SKY, 0.2, 0);
        assertEquals(0.3, drives.value(Drive.SKY, 0), 1e-12);
        drives.feed(Drive.SKY, 5, 0);
        assertEquals(0, drives.value(Drive.SKY, 0), 0);
    }

    @Test
    void feedingOneLeavesTheOthers() {
        var drives = begun(0);
        drives.feed(Drive.SKY, 0.2, 0);
        assertEquals(0.5, drives.value(Drive.COMPANY, 0), 1e-12);
    }
}
