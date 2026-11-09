package com.frozendawn.client;

import org.lwjgl.glfw.GLFW;

/**
 * Where the field manual's front-page search bar sits, in Patchouli book coordinates.
 * It follows Patchouli's lower category rule, which drops a row for every four icons.
 */
public final class ManualSearchBarPolicy {
    public static final int LEFT = 149;
    public static final int WIDTH = 100;
    public static final int HEIGHT = 13;
    /** Three rows of icons; a fourth would push the bar off the paper. */
    public static final int MAX_ICONS = 12;

    private static final int LOWER_RULE_BASE = 18 + 12 + 25;
    private static final int GAP_BELOW_RULE = 7;

    private ManualSearchBarPolicy() {
    }

    public static int top(int icons) {
        return LOWER_RULE_BASE + 24 * ((icons - 1) / 4 + 1) + GAP_BELOW_RULE;
    }

    public static boolean shown(int icons) {
        return icons >= 1 && icons <= MAX_ICONS;
    }

    public static boolean contains(int icons, double x, double y) {
        int top = top(icons);
        return shown(icons) && x >= LEFT && x < LEFT + WIDTH && y >= top && y < top + HEIGHT;
    }

    /** Letter and digit keys, so Patchouli's keyPressed cannot close the page before the character arrives. */
    public static boolean keepsKeyForSearch(int glfwKey) {
        return glfwKey >= GLFW.GLFW_KEY_A && glfwKey <= GLFW.GLFW_KEY_Z
                || glfwKey >= GLFW.GLFW_KEY_0 && glfwKey <= GLFW.GLFW_KEY_9;
    }

    public static boolean startsSearch(char c) {
        return Character.isLetterOrDigit(c);
    }
}
