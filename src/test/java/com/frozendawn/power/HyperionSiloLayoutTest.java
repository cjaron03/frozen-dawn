package com.frozendawn.power;

import static com.frozendawn.power.HyperionSiloLayout.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HyperionSiloLayoutTest {
    /** Front steps north, east, south, west, and the matching quarter turn applied to an offset drawn for a north front. */
    private static final int[][] FRONTS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static double[] turn(double dx, double dz, int quarter) {
        for (int i = 0; i < quarter; i++) { double t = dx; dx = -dz; dz = t; }
        return new double[]{dx, dz};
    }
    private static boolean contains(double[] box, double x, double y, double z) {
        return x >= box[0] && y >= box[1] && z >= box[2] && x <= box[3] && y <= box[4] && z <= box[5];
    }

    /** Review focus: looking up at an 8-tall, its rings or its pipes must not cull the silo with the Controller off-screen. */
    @Test void renderBoxCoversRingsPipesAndVent() {
        for (int q = 0; q < 4; q++) {
            int fx = FRONTS[q][0], fz = FRONTS[q][1];
            var box = renderBox(0, 64, 0, fx, fz, 8);
            double ax = 0.5 - fx, az = 0.5 - fz;
            if (q == 0) { assertEquals(0.5, ax); assertEquals(1.5, az); }
            assertTrue(contains(box, ax, 73.5, az), "Steam above the vent, front " + q);
            for (var d : new double[][]{{2.375, 0}, {-2.375, 0}, {0, 2.375}, {0, -2.375}})
                assertTrue(contains(box, ax + d[0], 70.4, az + d[1]), "Ring edge " + d[0] + "," + d[1] + ", front " + q);
            var pipe = turn(-2.5, 0, q);
            assertTrue(contains(box, ax + pipe[0], 61, az + pipe[1]), "A side cooler's pipe end 3 blocks down, front " + q);
        }
    }
}
