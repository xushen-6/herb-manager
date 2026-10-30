package cn.herb;

import cn.herb.dao.Database;
import cn.herb.service.Platform;
import cn.herb.view.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** 真实桌面端到端检查；手动运行，不在无图形环境的 mvn test 中执行。 */
public final class DesktopSmoke {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length==0?"work/screenshots":args[0]); Files.createDirectories(out);
        Platform platform=new Platform(new Database("jdbc:h2:mem:desktop;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","")); platform.initialize();
        SwingUtilities.invokeAndWait(() -> { Theme.install(); new LoginFrame(platform,"界面检查数据库").setVisible(true); });
        Thread.sleep(700);
        capture(out.resolve("01-login.png"));
        SwingUtilities.invokeAndWait(() -> {
            ((JTextField)find("login.username")).setText("admin");
            ((JPasswordField)find("login.password")).setText("Admin123!");
            ((JTextField)find("login.captcha")).setText(((JLabel)find("login.captcha.code")).getText());
            ((JButton)find("login.submit")).doClick();
        });
        waitFor("nav.药材管理");
        Thread.sleep(800); capture(out.resolve("02-dashboard.png"));
        click("nav.药材管理"); Thread.sleep(700); capture(out.resolve("03-medicinals.png"));
        click("nav.智能问答"); Thread.sleep(500);
        SwingUtilities.invokeAndWait(() -> { ((JTextField)find("chat.question")).setText("黄芪的功效"); ((JButton)find("chat.send")).doClick(); });
        Thread.sleep(1000);
        SwingUtilities.invokeAndWait(() -> { if(!((JTextArea)find("chat.transcript")).getText().contains("补气升阳")) throw new AssertionError("问答未返回正确内容"); });
        capture(out.resolve("04-chat.png"));
        click("nav.用户管理"); Thread.sleep(500); capture(out.resolve("05-users.png"));
        click("nav.个人资料"); Thread.sleep(300); capture(out.resolve("06-profile.png"));
        SwingUtilities.invokeAndWait(() -> { for(Window w:Window.getWindows()) w.dispose(); });
        System.out.println("DESKTOP SMOKE PASS: login, dashboard, herbs, chat, users, profile; screenshots="+out.toAbsolutePath());
        System.exit(0);
    }
    private static void click(String name) throws Exception { SwingUtilities.invokeAndWait(() -> ((JButton)find(name)).doClick()); }
    private static void waitFor(String name) throws Exception {
        for(int i=0;i<100;i++) { final boolean[] found={false}; SwingUtilities.invokeAndWait(() -> found[0]=findOrNull(name)!=null); if(found[0]) return; Thread.sleep(100); }
        throw new AssertionError("等待界面超时："+name);
    }
    private static Component find(String name) { Component c=findOrNull(name); if(c==null) throw new AssertionError("找不到组件："+name); return c; }
    private static Component findOrNull(String name) { for(Window w:Window.getWindows()) if(w.isShowing()) { Component c=search(w,name); if(c!=null) return c; } return null; }
    private static Component search(Component c,String name) { if(name.equals(c.getName())) return c; if(c instanceof Container ct) for(Component child:ct.getComponents()){ Component r=search(child,name); if(r!=null) return r; } return null; }
    private static void capture(Path path) throws Exception {
        final Rectangle[] bounds={null}; SwingUtilities.invokeAndWait(() -> { for(Window w:Window.getWindows()) if(w.isShowing() && w instanceof JFrame) bounds[0]=w.getBounds(); });
        if(bounds[0]==null) throw new AssertionError("窗口未显示");
        BufferedImage img=new Robot().createScreenCapture(bounds[0]); ImageIO.write(img,"png",path.toFile());
    }
}
