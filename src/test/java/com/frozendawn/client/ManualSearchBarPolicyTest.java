package com.frozendawn.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualSearchBarPolicyTest {
    // Patchouli 1.21.1-92 landing page geometry, book coordinates.
    private static final int RIGHT_PAGE_X = 141;
    private static final int PAGE_WIDTH = 116;
    private static final int ICON_TOP = 18 + 25;
    private static final int ICON_SIZE = 20;
    private static final int PAPER_BOTTOM = 18 + 149;

    @Test
    void sitsUnderTheLowerRuleAsInTheApprovedMockup() {
        // Six categories and the index: two rows, lower rule at y 103.
        assertEquals(110, ManualSearchBarPolicy.top(7));
        assertEquals(149, ManualSearchBarPolicy.LEFT);
    }

    @Test
    void neverCoversACategoryIcon() {
        for (int icons = 1; icons <= ManualSearchBarPolicy.MAX_ICONS; icons++) {
            int lastRowBottom = ICON_TOP + 24 * ((icons - 1) / 4) + ICON_SIZE;
            assertTrue(ManualSearchBarPolicy.top(icons) > lastRowBottom, icons + " icons");
        }
    }

    @Test
    void staysOnTheRightPagePaper() {
        assertTrue(ManualSearchBarPolicy.LEFT >= RIGHT_PAGE_X);
        assertTrue(ManualSearchBarPolicy.LEFT + ManualSearchBarPolicy.WIDTH <= RIGHT_PAGE_X + PAGE_WIDTH);
        int bottom = ManualSearchBarPolicy.top(ManualSearchBarPolicy.MAX_ICONS) + ManualSearchBarPolicy.HEIGHT;
        assertTrue(bottom <= PAPER_BOTTOM, "bottom " + bottom);
    }

    @Test
    void hiddenWhenTheCategoriesFillThePage() {
        assertTrue(ManualSearchBarPolicy.shown(ManualSearchBarPolicy.MAX_ICONS));
        assertFalse(ManualSearchBarPolicy.shown(ManualSearchBarPolicy.MAX_ICONS + 1));
    }

    @Test
    void clicksOnlyCountInsideTheBar() {
        int top = ManualSearchBarPolicy.top(7);
        assertTrue(ManualSearchBarPolicy.contains(7, 150, top + 5));
        assertFalse(ManualSearchBarPolicy.contains(7, 150, top - 1));
        assertFalse(ManualSearchBarPolicy.contains(7, 150 + ManualSearchBarPolicy.WIDTH, top + 5));
        assertFalse(ManualSearchBarPolicy.contains(ManualSearchBarPolicy.MAX_ICONS + 1, 150, top + 5));
    }

    @Test
    void lettersAndDigitsStartASearch() {
        assertTrue(ManualSearchBarPolicy.startsSearch('d'));
        assertTrue(ManualSearchBarPolicy.startsSearch('H'));
        assertTrue(ManualSearchBarPolicy.startsSearch('7'));
        assertFalse(ManualSearchBarPolicy.startsSearch(' '));
        assertFalse(ManualSearchBarPolicy.startsSearch('\t'));
    }
}
