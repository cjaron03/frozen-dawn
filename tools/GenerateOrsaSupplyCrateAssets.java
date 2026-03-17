import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class GenerateOrsaSupplyCrateAssets {

    private static final int SIZE = 64;
    private static final Color METAL_DARK = new Color(31, 36, 42);
    private static final Color METAL = new Color(59, 66, 74);
    private static final Color METAL_LIGHT = new Color(89, 97, 108);
    private static final Color PANEL = new Color(72, 78, 86);
    private static final Color ORANGE = new Color(214, 129, 28);
    private static final Color ORANGE_BRIGHT = new Color(247, 181, 63);
    private static final Color HAZARD = new Color(226, 183, 53);
    private static final Color STRIPE_DARK = new Color(26, 26, 28);
    private static final Color BOLT = new Color(146, 153, 161);

    public static void main(String[] args) throws IOException {
        File base = new File("src/main/resources/assets/frozendawn/textures/block");
        base.mkdirs();
        write(new File(base, "orsa_supply_crate_front.png"), createFront());
        write(new File(base, "orsa_supply_crate_side.png"), createSide());
        write(new File(base, "orsa_supply_crate_top.png"), createTop());
        write(new File(base, "orsa_supply_crate_bottom.png"), createBottom());
    }

    private static BufferedImage createFront() {
        BufferedImage img = blank();
        Graphics2D g = prepare(img);
        paintBase(g);
        panel(g, 9, 15, 46, 31, PANEL);
        rail(g, 4, 6, 9, 52);
        rail(g, 51, 6, 9, 52);
        light(g, 7, 8);
        light(g, 49, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        drawCentered(g, "ORSA", 32, 26);
        g.setFont(new Font("SansSerif", Font.BOLD, 6));
        drawCentered(g, "SUPPLY CRATE", 32, 35);
        handle(g, 25, 41, 14, 7);
        hazardBar(g, 10, 49, 44, 8);
        brace(g, 6, 50, true);
        brace(g, 48, 50, false);
        g.dispose();
        return img;
    }

    private static BufferedImage createSide() {
        BufferedImage img = blank();
        Graphics2D g = prepare(img);
        paintBase(g);
        panel(g, 10, 10, 44, 38, PANEL);
        rail(g, 5, 5, 8, 54);
        rail(g, 51, 5, 8, 54);
        vent(g, 25, 31, 14, 8);
        accentBar(g, 16, 6, 32, 5);
        hazardBar(g, 9, 50, 46, 7);
        g.dispose();
        return img;
    }

    private static BufferedImage createTop() {
        BufferedImage img = blank();
        Graphics2D g = prepare(img);
        paintBase(g);
        panel(g, 8, 8, 48, 48, PANEL);
        rail(g, 4, 6, 56, 8);
        rail(g, 4, 50, 56, 8);
        accentBar(g, 16, 6, 32, 5);
        accentBar(g, 20, 53, 24, 4);
        cornerPlate(g, 6, 6);
        cornerPlate(g, 50, 6);
        cornerPlate(g, 6, 50);
        cornerPlate(g, 50, 50);
        g.dispose();
        return img;
    }

    private static BufferedImage createBottom() {
        BufferedImage img = blank();
        Graphics2D g = prepare(img);
        paintBase(g);
        panel(g, 8, 8, 48, 48, new Color(48, 53, 60));
        accentBar(g, 18, 9, 28, 4);
        accentBar(g, 20, 51, 24, 4);
        support(g, 14, 24, 36, 16);
        g.dispose();
        return img;
    }

    private static void paintBase(Graphics2D g) {
        g.setColor(METAL_DARK);
        g.fillRect(0, 0, SIZE, SIZE);
        g.setColor(METAL);
        g.fillRect(3, 3, SIZE - 6, SIZE - 6);
        g.setColor(METAL_LIGHT);
        g.drawRect(3, 3, SIZE - 7, SIZE - 7);
        g.setColor(new Color(21, 24, 28, 90));
        for (int i = 4; i < SIZE - 4; i += 6) {
            g.drawLine(i, 4, i, SIZE - 5);
        }
    }

    private static void rail(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(44, 49, 56));
        g.fillRect(x, y, w, h);
        g.setColor(METAL_LIGHT);
        g.drawRect(x, y, w - 1, h - 1);
        for (int yy = y + 4; yy < y + h - 2; yy += 10) {
            bolt(g, x + 2, yy);
            bolt(g, x + w - 4, yy);
        }
    }

    private static void panel(Graphics2D g, int x, int y, int w, int h, Color fill) {
        g.setColor(fill);
        g.fillRect(x, y, w, h);
        g.setColor(METAL_LIGHT);
        g.drawRect(x, y, w - 1, h - 1);
        g.setColor(new Color(22, 25, 29, 90));
        g.drawRect(x + 2, y + 2, w - 5, h - 5);
    }

    private static void accentBar(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(98, 70, 24));
        g.fillRect(x, y, w, h);
        g.setColor(ORANGE);
        g.fillRect(x + 1, y + 1, w - 2, h - 2);
        g.setColor(ORANGE_BRIGHT);
        g.drawLine(x + 1, y + 1, x + w - 2, y + 1);
    }

    private static void light(Graphics2D g, int x, int y) {
        g.setColor(new Color(80, 54, 17));
        g.fillRect(x, y, 8, 8);
        g.setColor(ORANGE_BRIGHT);
        g.fillRect(x + 1, y + 1, 6, 6);
        g.setColor(new Color(255, 231, 142, 140));
        g.drawRect(x + 1, y + 1, 5, 5);
    }

    private static void handle(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(32, 34, 39));
        g.fillRoundRect(x, y, w, h, 3, 3);
        g.setColor(METAL_LIGHT);
        g.drawRoundRect(x, y, w - 1, h - 1, 3, 3);
    }

    private static void hazardBar(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(HAZARD);
        g.fillRect(x, y, w, h);
        g.setColor(STRIPE_DARK);
        for (int i = -h; i < w; i += 10) {
            Polygon stripe = new Polygon();
            stripe.addPoint(x + i, y + h);
            stripe.addPoint(x + i + 5, y + h);
            stripe.addPoint(x + i + 10, y);
            stripe.addPoint(x + i + 5, y);
            g.fillPolygon(stripe);
        }
        g.setColor(new Color(255, 232, 143));
        g.drawLine(x, y, x + w - 1, y);
    }

    private static void brace(Graphics2D g, int x, int y, boolean left) {
        g.setColor(HAZARD);
        Polygon p = new Polygon();
        if (left) {
            p.addPoint(x, y + 8);
            p.addPoint(x + 8, y);
            p.addPoint(x + 10, y + 2);
            p.addPoint(x + 2, y + 10);
        } else {
            p.addPoint(x + 10, y + 8);
            p.addPoint(x + 2, y);
            p.addPoint(x, y + 2);
            p.addPoint(x + 8, y + 10);
        }
        g.fillPolygon(p);
    }

    private static void vent(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(40, 44, 50));
        g.fillRoundRect(x, y, w, h, 3, 3);
        g.setColor(METAL_LIGHT);
        g.drawRoundRect(x, y, w - 1, h - 1, 3, 3);
    }

    private static void support(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(34, 38, 43));
        g.fillRect(x, y, w, h);
        g.setColor(METAL_LIGHT);
        g.drawRect(x, y, w - 1, h - 1);
        for (int xx = x + 4; xx < x + w; xx += 8) {
            bolt(g, xx, y + 4);
            bolt(g, xx, y + h - 6);
        }
    }

    private static void cornerPlate(Graphics2D g, int x, int y) {
        g.setColor(new Color(53, 58, 64));
        g.fillRect(x, y, 8, 8);
        g.setColor(METAL_LIGHT);
        g.drawRect(x, y, 7, 7);
        bolt(g, x + 2, y + 2);
        bolt(g, x + 5, y + 5);
    }

    private static void bolt(Graphics2D g, int x, int y) {
        g.setColor(BOLT);
        g.fillRect(x, y, 2, 2);
    }

    private static void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }

    private static Graphics2D prepare(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        return g;
    }

    private static BufferedImage blank() {
        return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    private static void write(File file, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", file);
    }
}
