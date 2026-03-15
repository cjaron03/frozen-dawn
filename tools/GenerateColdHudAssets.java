import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

public final class GenerateColdHudAssets {

    private static final Path INPUT_JAR = Path.of("build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar");
    private static final Path OUTPUT_ROOT = Path.of("src/main/resources");

    private static final List<HudAsset> ASSETS = List.of(
            new HudAsset("assets/minecraft/textures/gui/sprites/hud/hotbar.png", Mode.HOTBAR,
                    new int[]{14, 38, 63, 87, 111, 136, 160}),
            new HudAsset("assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png", Mode.SELECTION,
                    new int[]{7, 17}),
            new HudAsset("assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_left.png", Mode.OFFHAND,
                    new int[]{8, 19}),
            new HudAsset("assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_right.png", Mode.OFFHAND,
                    new int[]{9, 20})
    );

    private GenerateColdHudAssets() {
    }

    public static void main(String[] args) throws Exception {
        if (!Files.exists(INPUT_JAR)) {
            throw new IllegalStateException("Missing input resources jar: " + INPUT_JAR);
        }

        try (ZipFile zip = new ZipFile(INPUT_JAR.toFile())) {
            for (HudAsset asset : ASSETS) {
                BufferedImage source;
                try (InputStream stream = zip.getInputStream(zip.getEntry(asset.path))) {
                    source = ImageIO.read(stream);
                }

                if (source == null) {
                    throw new IllegalStateException("Unable to read source image: " + asset.path);
                }

                BufferedImage themed = recolor(source, asset.mode);
                addFrostCap(themed, asset.mode);
                addIcicles(themed, asset.anchors, asset.mode);

                Path output = OUTPUT_ROOT.resolve(asset.path);
                Files.createDirectories(output.getParent());
                ImageIO.write(themed, "png", output.toFile());
            }
        }
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
                    case HOTBAR -> gradient((float) Math.pow(luma, 0.88), 0x061019, 0x214A67, 0xCDEFFF);
                    case SELECTION -> gradient((float) Math.pow(luma, 0.80), 0x18324A, 0x5D9AD0, 0xF4FEFF);
                    case OFFHAND -> gradient((float) Math.pow(luma, 0.86), 0x08121C, 0x2A5877, 0xD8F5FF);
                };

                float frost = switch (mode) {
                    case HOTBAR -> vertical < 0.22f ? 0.14f : 0.0f;
                    case SELECTION -> vertical < 0.30f ? 0.18f : 0.0f;
                    case OFFHAND -> vertical < 0.26f ? 0.15f : 0.0f;
                };

                int outRed = mix(palette[0], 240, frost);
                int outGreen = mix(palette[1], 249, frost);
                int outBlue = mix(palette[2], 255, frost);
                target.setRGB(x, y, alpha << 24 | outRed << 16 | outGreen << 8 | outBlue);
            }
        }

        return target;
    }

    private static void addFrostCap(BufferedImage image, Mode mode) {
        for (int x = 0; x < image.getWidth(); x++) {
            int top = findTopOpaqueY(image, x);
            if (top < 0 || top > image.getHeight() / 2) {
                continue;
            }

            int depth = switch (mode) {
                case HOTBAR -> 2 + ((x / 9) % 2);
                case SELECTION -> 3;
                case OFFHAND -> 2 + ((x / 5) % 2);
            };

            for (int d = 0; d < depth; d++) {
                int y = top + d;
                if (y >= image.getHeight() || alphaAt(image, x, y) == 0) {
                    break;
                }
                blendPixel(image, x, y, 236, 248, 255, 0.45f - d * 0.12f);
            }
        }
    }

    private static void addIcicles(BufferedImage image, int[] anchors, Mode mode) {
        for (int i = 0; i < anchors.length; i++) {
            int anchor = anchors[i];
            int top = findTopOpaqueY(image, anchor);
            if (top < 0) {
                continue;
            }

            int length = switch (mode) {
                case HOTBAR -> 3 + (i % 3);
                case SELECTION -> 4 + (i % 2);
                case OFFHAND -> 3 + (i % 2);
            };

            for (int dy = 1; dy <= length; dy++) {
                int halfWidth = Math.max(0, (length - dy) / 2);
                int y = top + dy;
                if (y >= image.getHeight()) {
                    break;
                }

                for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                    int x = anchor + dx;
                    if (x < 0 || x >= image.getWidth()) {
                        continue;
                    }
                    if (alphaAt(image, x, y) == 0) {
                        continue;
                    }

                    float blend = mode == Mode.SELECTION ? 0.65f : 0.55f;
                    blend -= dy * 0.08f;
                    if (blend <= 0.05f) {
                        continue;
                    }
                    blendPixel(image, x, y, 235, 248, 255, blend);
                }
            }
        }
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
            return new int[] {
                    mix((dark >>> 16) & 0xFF, (mid >>> 16) & 0xFF, local),
                    mix((dark >>> 8) & 0xFF, (mid >>> 8) & 0xFF, local),
                    mix(dark & 0xFF, mid & 0xFF, local)
            };
        }

        float local = (amount - 0.58f) / 0.42f;
        return new int[] {
                mix((mid >>> 16) & 0xFF, (light >>> 16) & 0xFF, local),
                mix((mid >>> 8) & 0xFF, (light >>> 8) & 0xFF, local),
                mix(mid & 0xFF, light & 0xFF, local)
        };
    }

    private static int mix(int from, int to, float amount) {
        return from + Math.round((to - from) * amount);
    }

    private record HudAsset(String path, Mode mode, int[] anchors) {
    }

    private enum Mode {
        HOTBAR,
        SELECTION,
        OFFHAND
    }
}
