import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Package the approved atlas tiles at Minecraft's native 16x16 resolution. */
class ExportAirlockTextures {
    public static void main(String[] args) throws Exception {
        var root=Path.of(args[0]);var source=root.resolve("tools/texture_sources/airlock/approved-atlas.png");
        var input=ImageIO.read(source.toFile());
        if(input.getWidth()!=1235||input.getHeight()!=1274)throw new IllegalArgumentException("Unexpected atlas dimensions");
        String[] names={"airlock_door_top","airlock_door_bottom","airlock_controller_red","airlock_controller_amber","airlock_controller_green","airlock_casing","manual_vent_valve"};
        int[][] boxes={{215,0,617,379},{620,0,1019,379},{215,381,617,689},{619,381,1018,689},{215,692,617,992},{619,692,1018,992},{215,995,617,1273}};
        var dest=root.resolve("src/main/resources/assets/frozendawn/textures/block");Files.createDirectories(dest);
        for(int n=0;n<names.length;n++) {
            var out=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);var b=boxes[n];
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)
                out.setRGB(x,y,input.getRGB(b[0]+(2*x+1)*(b[2]-b[0])/32,b[1]+(2*y+1)*(b[3]-b[1])/32));
            ImageIO.write(out,"png",dest.resolve(names[n]+".png").toFile());
        }
    }
}
