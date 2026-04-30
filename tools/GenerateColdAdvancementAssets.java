import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class GenerateColdAdvancementAssets {

    // Historical/internal utility only. The release provenance for shipped UI
    // replacement assets is documented in ASSETS.md; final assets under
    // assets/minecraft are original Frozen Dawn compatibility textures.
    // Do not use this tool as release asset provenance or to copy/derive
    // Mojang/Microsoft texture assets for distribution.
    private static final Path INPUT_JAR = Path.of("build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar");
    private static final Path OUTPUT_ROOT = Path.of("src/main/resources");
    private static final String WINDOW = "assets/minecraft/textures/gui/advancements/window.png";
    private static final String BACKGROUND_PREFIX = "assets/minecraft/textures/gui/advancements/backgrounds/";
    private static final String SPRITE_PREFIX = "assets/minecraft/textures/gui/sprites/advancements/";

    private GenerateColdAdvancementAssets() {
    }

    public static void main(String[] args) throws Exception {
        if (!Files.exists(INPUT_JAR)) {
            throw new IllegalStateException("Missing input resources jar: " + INPUT_JAR);
        }

        int generated = 0;
        int copiedMeta = 0;

        try (ZipFile zip = new ZipFile(INPUT_JAR.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!shouldGenerate(name)) {
                    continue;
                }

                BufferedImage source;
                try (InputStream stream = zip.getInputStream(entry)) {
                    source = ImageIO.read(stream);
                }

                if (source == null) {
                    continue;
                }

                BufferedImage themed = recolor(source, modeFor(name));
                accent(themed, modeFor(name), name);

                Path output = OUTPUT_ROOT.resolve(name);
                Files.createDirectories(output.getParent());
                ImageIO.write(themed, "png", output.toFile());
                generated++;

                ZipEntry mcmeta = zip.getEntry(name + ".mcmeta");
                if (mcmeta != null) {
                    Path mcmetaOutput = OUTPUT_ROOT.resolve(mcmeta.getName());
                    Files.createDirectories(mcmetaOutput.getParent());
                    try (InputStream stream = zip.getInputStream(mcmeta);
                         OutputStream out = Files.newOutputStream(mcmetaOutput)) {
                        stream.transferTo(out);
                    }
                    copiedMeta++;
                }
            }
        }

        System.out.println("Generated cold advancement assets: " + generated);
        System.out.println("Copied mcmeta files: " + copiedMeta);
    }

    private static boolean shouldGenerate(String name) {
        return name.equals(WINDOW)
                || name.startsWith(BACKGROUND_PREFIX) && name.endsWith(".png")
                || name.startsWith(SPRITE_PREFIX) && name.endsWith(".png");
    }

    private static Mode modeFor(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (name.equals(WINDOW) || lower.contains("title_box")) {
            return Mode.PANEL;
        }
        if (name.startsWith(BACKGROUND_PREFIX)) {
            return Mode.BACKGROUND;
        }
        if (lower.contains("selected") || lower.contains("obtained")) {
            return Mode.BRIGHT;
        }
        return Mode.ACCENT;
    }

    private static BufferedImage recolor(BufferedImage source, Mode mode) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < source.getHeight(); y++) {
            float vertical = source.getHeight() <= 1 ? 0.0f : (float) y / (source.getHeight() - 1);
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }

                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                float luma = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255.0f;

                int[] palette = switch (mode) {
                    case PANEL -> gradient((float) Math.pow(luma, 0.86), 0x040A12, 0x15314B, 0xB8E4FF);
                    case BACKGROUND -> gradient((float) Math.pow(luma, 0.94), 0x0A1220, 0x294A68, 0xD9F5FF);
                    case ACCENT -> gradient((float) Math.pow(luma, 0.92), 0x0D1B2E, 0x4574A6, 0xDDF6FF);
                    case BRIGHT -> gradient((float) Math.pow(luma, 0.82), 0x18304D, 0x73B2E7, 0xF5FEFF);
                };

                float frost = switch (mode) {
                    case PANEL -> vertical < 0.18f ? 0.20f : 0.0f;
                    case BACKGROUND -> vertical < 0.12f ? 0.08f : 0.0f;
                    case ACCENT -> vertical < 0.24f ? 0.10f : 0.0f;
                    case BRIGHT -> vertical < 0.28f ? 0.14f : 0.0f;
                };

                int outRed = mix(palette[0], 237, frost);
                int outGreen = mix(palette[1], 247, frost);
                int outBlue = mix(palette[2], 255, frost);
                target.setRGB(x, y, alpha << 24 | outRed << 16 | outGreen << 8 | outBlue);
            }
        }

        return target;
    }

    private static void accent(BufferedImage image, Mode mode, String name) {
        if (mode == Mode.PANEL) {
            addFrostLine(image);
        }

        if (mode == Mode.BRIGHT || name.contains("selected")) {
            addGlow(image);
        }

        if (name.contains("title_box")) {
            addIcicles(image);
        }
    }

    private static void addFrostLine(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            int top = findTopOpaqueY(image, x);
            if (top < 0) {
                continue;
            }
            for (int depth = 0; depth < 2; depth++) {
                int y = top + depth;
                if (y >= image.getHeight() || alphaAt(image, x, y) == 0) {
                    break;
                }
                blendPixel(image, x, y, 236, 247, 255, 0.48f - depth * 0.16f);
            }
        }
    }

    private static void addGlow(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (alphaAt(image, x, y) == 0) {
                    continue;
                }
                if (isEdge(image, x, y)) {
                    blendPixel(image, x, y, 215, 242, 255, 0.18f);
                }
            }
        }
    }

    private static void addIcicles(BufferedImage image) {
        int[] anchors = {
                image.getWidth() / 5,
                image.getWidth() / 2,
                (image.getWidth() * 4) / 5
        };

        for (int i = 0; i < anchors.length; i++) {
            int x = anchors[i];
            int top = findTopOpaqueY(image, x);
            if (top < 0) {
                continue;
            }

            int length = 2 + i;
            for (int dy = 1; dy <= length; dy++) {
                int y = top + dy;
                if (y >= image.getHeight() || alphaAt(image, x, y) == 0) {
                    break;
                }
                blendPixel(image, x, y, 236, 248, 255, 0.52f - dy * 0.10f);
            }
        }
    }

    private static boolean isEdge(BufferedImage image, int x, int y) {
        if (alphaAt(image, x, y) == 0) {
            return false;
        }

        return alphaAt(image, x - 1, y) == 0
                || alphaAt(image, x + 1, y) == 0
                || alphaAt(image, x, y - 1) == 0
                || alphaAt(image, x, y + 1) == 0;
    }

    private static int findTopOpaqueY(BufferedImage image, int x) {
        for (int y = 0; y < image.getHeight(); y++) {
            if (alphaAt(image, x, y) > 12) {
                return y;
            }
        }
        return -1;
    }

    private static int alphaAt(BufferedImage image, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return 0;
        }
        return image.getRGB(x, y) >>> 24;
    }

    private static void blendPixel(BufferedImage image, int x, int y, int red, int green, int blue, float amount) {
        int argb = image.getRGB(x, y);
        int alpha = argb >>> 24;
        if (alpha == 0) {
            return;
        }

        int currentRed = (argb >>> 16) & 0xFF;
        int currentGreen = (argb >>> 8) & 0xFF;
        int currentBlue = argb & 0xFF;

        int outRed = mix(currentRed, red, amount);
        int outGreen = mix(currentGreen, green, amount);
        int outBlue = mix(currentBlue, blue, amount);
        image.setRGB(x, y, alpha << 24 | outRed << 16 | outGreen << 8 | outBlue);
    }

    private static int[] gradient(float amount, int dark, int mid, int light) {
        if (amount < 0.58f) {
            float local = amount / 0.58f;
            return new int[]{
                    mix((dark >>> 16) & 0xFF, (mid >>> 16) & 0xFF, local),
                    mix((dark >>> 8) & 0xFF, (mid >>> 8) & 0xFF, local),
                    mix(dark & 0xFF, mid & 0xFF, local)
            };
        }

        float local = (amount - 0.58f) / 0.42f;
        return new int[]{
                mix((mid >>> 16) & 0xFF, (light >>> 16) & 0xFF, local),
                mix((mid >>> 8) & 0xFF, (light >>> 8) & 0xFF, local),
                mix(mid & 0xFF, light & 0xFF, local)
        };
    }

    private static int mix(int from, int to, float amount) {
        return from + Math.round((to - from) * amount);
    }

    private enum Mode {
        PANEL,
        BACKGROUND,
        ACCENT,
        BRIGHT
    }
}
