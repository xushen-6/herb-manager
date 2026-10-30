package cn.herb;

import cn.herb.dao.Database;
import cn.herb.service.Platform;
import cn.herb.view.LoginFrame;
import cn.herb.view.Theme;
import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public final class App {
    public static void main(String[] args) {
        Theme.install();
        Properties config=loadConfig();
        Database db=new Database(config.getProperty("jdbc.url","jdbc:h2:file:./data/herb-manager;MODE=MySQL;DB_CLOSE_DELAY=-1"),config.getProperty("jdbc.username","sa"),config.getProperty("jdbc.password",""));
        Platform platform=new Platform(db);
        try { platform.initialize(); } catch(RuntimeException e) { JOptionPane.showMessageDialog(null,"数据库初始化失败：\n"+e.getMessage()+"\n\n请检查 config.properties 中的 MySQL 地址、用户名和密码。","启动失败",JOptionPane.ERROR_MESSAGE); return; }
        SwingUtilities.invokeLater(() -> new LoginFrame(platform,db.mysql()?"MySQL":"本地演示数据库").setVisible(true));
    }
    private static Properties loadConfig() {
        Properties p=new Properties();Path path=Path.of("config.properties");
        if(Files.exists(path)) try(InputStream in=Files.newInputStream(path)){p.load(in);}catch(IOException e){throw new IllegalStateException("无法读取 config.properties",e);}
        return p;
    }
}
