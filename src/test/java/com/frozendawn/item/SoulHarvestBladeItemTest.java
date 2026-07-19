package com.frozendawn.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoulHarvestBladeItemTest {

    @Test
    void masterArchitectAlwaysReceivesExactlyOneSoulHarvestBonus() {
        assertEquals(3.0F, SoulHarvestBladeItem.soulHarvestDamageBonus(false, true));
        assertEquals(3.0F, SoulHarvestBladeItem.soulHarvestDamageBonus(true, true));
        assertEquals(3.0F, SoulHarvestBladeItem.soulHarvestDamageBonus(true, false));
        assertEquals(0.0F, SoulHarvestBladeItem.soulHarvestDamageBonus(false, false));
    }
}
