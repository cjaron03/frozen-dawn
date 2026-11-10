import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * The spinning ORSA icon's sheet: the white ring frame, the planet, and a moon orbiting along the planet's ring,
 * passing behind the planet on the far side, with a short trail. 64 frames of 208 px, laid out 32 across and 2 down.
 *
 * The replay's copy is the same sheet drained to grey, as its other _muted textures are: 0.755 of the luminance,
 * a little cool.
 *
 * Usage: java tools/GenerateOrsaBootSheet.java <output png> <muted output png>
 */
public final class GenerateOrsaBootSheet {
    static final int FRAME = 208;
    static final int FRAMES = 64;
    static final int COLUMNS = 32;
    private static final double C = FRAME / 2.0;

    private static final Color WHITE = new Color(242, 241, 240);
    private static final Color WHITE_EDGE = new Color(204, 204, 204);
    private static final Color PLANET = new Color(126, 177, 228);
    private static final Color PLANET_SHADE = new Color(88, 138, 196);
    private static final Color PLANET_LIGHT = new Color(176, 210, 242);
    private static final Color RING = new Color(225, 204, 73);
    private static final Color RING_LIGHT = new Color(246, 226, 120);
    private static final Color OUTLINE = new Color(26, 24, 7);
    private static final Color MOON = new Color(150, 200, 240);
    private static final Color MOON_SHADE = new Color(102, 150, 200);

    private static final double OUTER = 74;
    private static final double INNER = 53;
    private static final double PLANET_R = 25;
    private static final double RING_A = 60;
    private static final double RING_B = 16.5;
    private static final double TILT = Math.toRadians(-26);
    private static final double MOON_R = 7;

    public static void main(String[] args) throws IOException {
        BufferedImage sheet = new BufferedImage(FRAME * COLUMNS, FRAME * (FRAMES / COLUMNS), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        for (int frame = 0; frame < FRAMES; frame++) {
            g.drawImage(frame(frame), (frame % COLUMNS) * FRAME, (frame / COLUMNS) * FRAME, null);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(args[0]));
        ImageIO.write(muted(sheet), "png", new File(args[1]));
    }

    static BufferedImage muted(BufferedImage sheet) {
        BufferedImage out = new BufferedImage(sheet.getWidth(), sheet.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = 0; x < sheet.getWidth(); x++) {
                int p = sheet.getRGB(x, y);
                int lum = (int) Math.round(((p >> 16 & 0xFF) * 299 + (p >> 8 & 0xFF) * 587 + (p & 0xFF) * 114) / 1000.0 * 0.755);
                int r = Math.max(0, lum - 3);
                int g = Math.min(255, lum + 1);
                int b = Math.min(255, lum + 6);
                out.setRGB(x, y, p & 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        return out;
    }

    static BufferedImage frame(int frame) {
        BufferedImage img = new BufferedImage(FRAME, FRAME, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // The white frame, open in the middle.
        g.setColor(WHITE);
        g.fill(circle(C, C, OUTER));
        g.setColor(WHITE_EDGE);
        g.fill(circle(C, C, INNER + 2));
        g.setComposite(AlphaComposite.Clear);
        g.fill(circle(C, C, INNER));
        g.setComposite(AlphaComposite.SrcOver);

        // The moon runs one orbit over the sheet, counter to the clock, along the ring's ellipse.
        double theta = -2 * Math.PI * frame / FRAMES;
        ring(g, false);
        moon(g, theta, false);
        planet(g);
        ring(g, true);
        moon(g, theta, true);
        g.dispose();
        return img;
    }

    /** Half the ring: the far half (above the planet's centre in the ring's own frame) or the near half. */
    private static void ring(Graphics2D g, boolean near) {
        Graphics2D r = (Graphics2D) g.create();
        r.translate(C, C);
        r.rotate(TILT);
        r.clip(new Rectangle2D.Double(-RING_A - 10, near ? 0 : -RING_B - 10, 2 * RING_A + 20, RING_B + 10));
        Ellipse2D ellipse = new Ellipse2D.Double(-RING_A, -RING_B, 2 * RING_A, 2 * RING_B);
        r.setStroke(new BasicStroke(11f));
        r.setColor(OUTLINE);
        r.draw(ellipse);
        r.setStroke(new BasicStroke(6.5f));
        r.setColor(near ? RING : RING.darker());
        r.draw(ellipse);
        if (near) {
            r.setStroke(new BasicStroke(1.6f));
            r.setColor(RING_LIGHT);
            r.draw(new Ellipse2D.Double(-RING_A + 1.5, -RING_B + 1.5, 2 * RING_A - 3, 2 * RING_B - 3));
        }
        r.dispose();
    }

    private static void planet(Graphics2D g) {
        Graphics2D p = (Graphics2D) g.create();
        p.setColor(OUTLINE);
        p.fill(circle(C, C, PLANET_R + 2.5));
        p.clip(circle(C, C, PLANET_R));
        p.setColor(PLANET_SHADE);
        p.fill(circle(C, C, PLANET_R));
        p.setColor(PLANET);
        p.fill(circle(C - 5, C - 5, PLANET_R));
        p.setColor(PLANET_LIGHT);
        p.fill(circle(C - 11, C - 12, 7));
        p.dispose();
    }

    /** The moon and its trail, each drawn only on the side of the planet it is on. */
    private static void moon(Graphics2D g, double theta, boolean near) {
        for (int k = 6; k >= 0; k--) {
            double t = theta + k * 0.085;
            double depth = Math.sin(t);
            if ((depth >= 0) != near) {
                continue;
            }
            double[] at = onRing(t);
            double scale = 1.0 + 0.16 * depth;
            if (k == 0) {
                double r = MOON_R * scale;
                Color body = near ? MOON : blend(MOON, OUTLINE, 0.35);
                g.setColor(OUTLINE);
                g.fill(circle(at[0], at[1], r + 2));
                Graphics2D m = (Graphics2D) g.create();
                m.clip(circle(at[0], at[1], r));
                m.setColor(near ? MOON_SHADE : blend(MOON_SHADE, OUTLINE, 0.35));
                m.fill(circle(at[0], at[1], r));
                m.setColor(body);
                m.fill(circle(at[0] - r * 0.25, at[1] - r * 0.25, r));
                m.dispose();
            } else {
                float alpha = (float) (0.55 * (1 - k / 7.0)) * (near ? 1f : 0.6f);
                g.setColor(new Color(MOON.getRed(), MOON.getGreen(), MOON.getBlue(), Math.round(alpha * 255)));
                g.fill(circle(at[0], at[1], MOON_R * scale * (1 - k * 0.11)));
            }
        }
    }

    private static double[] onRing(double t) {
        double x = RING_A * Math.cos(t);
        double y = RING_B * Math.sin(t);
        AffineTransform tilt = AffineTransform.getRotateInstance(TILT);
        double[] out = {x, y};
        tilt.transform(out, 0, out, 0, 1);
        return new double[] {C + out[0], C + out[1]};
    }

    private static Ellipse2D circle(double x, double y, double r) {
        return new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
    }

    private static Color blend(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
