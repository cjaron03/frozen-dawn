package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LashSalvageTest {
    private static final int SITES = 10_000;

    @Test
    void everyHelionSiteHoldsOneOrTwoEmitters() {
        int two = 0;
        for (int i = 0; i < SITES; i++) {
            int n = LashSalvage.helionEmitters(42L, site(i));
            assertTrue(n == 1 || n == 2, "Helion emitters: " + n);
            if (n == 2) two++;
        }
        assertTrue(two > SITES * .4 && two < SITES * .6, "About half the Helion sites hold two: " + two);
    }

    @Test
    void aboutOneReactorSiteInFourHasAnEmitterInTheMaintenanceBay() {
        int lucky = 0;
        for (int i = 0; i < SITES; i++) if (LashSalvage.reactorBayEmitter(42L, site(i))) lucky++;
        assertTrue(lucky > SITES * .22 && lucky < SITES * .28, "About 1 in 4: " + lucky);
    }

    @Test
    void theHellwhipMemoIsRare() {
        int memos = 0;
        for (int i = 0; i < SITES; i++) if (LashSalvage.helionMemo(42L, site(i))) memos++;
        assertTrue(memos > SITES * .22 && memos < SITES * .28, "About 1 Helion site in 4: " + memos);
    }

    @Test
    void rollsFollowTheWorldSeedAndSite() {
        long site = site(7);
        assertEquals(LashSalvage.reactorBayEmitter(9L, site), LashSalvage.reactorBayEmitter(9L, site));
        assertEquals(LashSalvage.helionEmitters(9L, site), LashSalvage.helionEmitters(9L, site));
        int differs = 0;
        for (long seed = 0; seed < 200; seed++) if (LashSalvage.reactorBayEmitter(seed, site) != LashSalvage.reactorBayEmitter(seed + 1, site)) differs++;
        assertTrue(differs > 40, "Another seed rolls differently: " + differs);
        int independent = 0;
        for (int i = 0; i < SITES; i++) if (LashSalvage.helionMemo(42L, site(i)) && LashSalvage.helionEmitters(42L, site(i)) == 2) independent++;
        assertTrue(independent > SITES * .09 && independent < SITES * .16, "The memo roll is independent of the emitter roll: " + independent);
    }

    /** Packed like {@code BlockPos.asLong}: sites on a 1,500-block grid at y 64. */
    private static long site(int i) {
        long x = (i % 100 - 50) * 1500L, z = (i / 100 - 50) * 1500L;
        return (x & 0x3FFFFFFL) << 38 | (z & 0x3FFFFFFL) << 12 | 64;
    }
}
