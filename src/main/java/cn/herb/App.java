package cn.herb;

import cn.herb.dao.Database;
import cn.herb.service.Platform;
import cn.herb.view.LoginFrame;
import cn.herb.view.Theme;
import cn.herb.util.EmailCodeService;
import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public final class App {
    public static void main(String[] args) {
        Properties config=loadConfig();
        if(args.length>0 && args[0].equals("--check-connection")) {
            try { new cn.herb.service.RemotePlatform(config.getProperty("server.url")).initialize();System.out.println("CLOUD_CONNECTION_OK"); }
            catch(RuntimeException e){System.err.println(e.getMessage());System.exit(2);}
            return;
        }
        Theme.install();
        if(!config.getProperty("server.url","").isBlank()) {
            Platform remote=new Platform(new cn.herb.service.RemotePlatform(config.getProperty("server.url")));
            try {remote.initialize();}catch(RuntimeException e){JOptionPane.showMessageDialog(null,e.getMessage(),"服务器连接失败",JOptionPane.ERROR_MESSAGE);return;}
            SwingUtilities.invokeLater(()->new LoginFrame(remote,"云端 MySQL").setVisible(true));
            return;
        }
        Database db=new Database(config.getProperty("jdbc.url","jdbc:h2:file:./data/herb-manager;MODE=MySQL;DB_CLOSE_DELAY=-1"),config.getProperty("jdbc.username","sa"),config.getProperty("jdbc.password",""));
        EmailCodeService emailCodes=new EmailCodeService(config.getProperty("smtp.host","smtp.qq.com"),config.getProperty("smtp.port","465"),config.getProperty("smtp.username",""),config.getProperty("smtp.password",""));
        String deepSeekKey=System.getenv("DEEPSEEK_API_KEY");
        if(deepSeekKey==null || deepSeekKey.isBlank()) deepSeekKey=config.getProperty("deepseek.api-key","");
        String deepSeekEndpoint=config.getProperty("deepseek.endpoint",cn.herb.service.DeepSeekClient.DEFAULT_ENDPOINT);
        String deepSeekModel=config.getProperty("deepseek.model",cn.herb.service.DeepSeekClient.DEFAULT_MODEL);
        Platform platform=new Platform(db,emailCodes,config.getProperty("app.default-email",config.getProperty("smtp.username","test@example.com")),new cn.herb.service.DeepSeekClient(deepSeekKey,deepSeekEndpoint,deepSeekModel));
        try { platform.initialize(); } catch(RuntimeException e) { JOptionPane.showMessageDialog(null,"数据库初始化失败：\n"+e.getMessage()+"\n\n请检查 config.properties 中的 MySQL 地址、用户名和密码。","启动失败",JOptionPane.ERROR_MESSAGE); return; }
        SwingUtilities.invokeLater(() -> new LoginFrame(platform,db.mysql()?"MySQL":"本地演示数据库").setVisible(true));
    }
    private static Properties loadConfig() {
        Properties p=new Properties();Path path=Path.of("config.properties");
        if(Files.exists(path)) try(Reader in=Files.newBufferedReader(path)){p.load(in);}catch(IOException e){throw new IllegalStateException("无法读取 config.properties",e);}
        return p;
    }
}
