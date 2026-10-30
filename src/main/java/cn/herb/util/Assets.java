package cn.herb.util;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
public final class Assets {
    private Assets() {}
    public static BufferedImage image(String name) {
        try { return ImageIO.read(Assets.class.getResourceAsStream("/assets/" + name)); }
        catch (IOException | RuntimeException ex) { return null; }
    }
    public static ImageIcon icon(String name, int w, int h) {
        BufferedImage image=image(name); return image==null?null:new ImageIcon(image.getScaledInstance(w,h,Image.SCALE_SMOOTH));
    }
}
