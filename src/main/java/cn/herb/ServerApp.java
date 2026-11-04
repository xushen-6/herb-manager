package cn.herb;

import cn.herb.dao.Database;
import cn.herb.service.*;
import cn.herb.server.BackendServer;
import cn.herb.util.EmailCodeService;
import java.nio.file.*;
import java.io.*;
import java.util.Properties;

/** 无图形界面的服务端入口；只在服务器读取数据库、SMTP 和 AI 配置。 */
public final class ServerApp {
    public static void main(String[] args) throws Exception {
        Properties p=new Properties();try(Reader r=Files.newBufferedReader(Path.of(args.length==0?"server.properties":args[0]))){p.load(r);}
        Database db=new Database(p.getProperty("jdbc.url"),p.getProperty("jdbc.username"),p.getProperty("jdbc.password"));
        Platform platform=new Platform(db,new EmailCodeService(p.getProperty("smtp.host"),p.getProperty("smtp.port","465"),p.getProperty("smtp.username"),p.getProperty("smtp.password")),p.getProperty("app.default-email"),new DeepSeekClient(p.getProperty("deepseek.api-key"),p.getProperty("deepseek.endpoint",DeepSeekClient.DEFAULT_ENDPOINT),p.getProperty("deepseek.model",DeepSeekClient.DEFAULT_MODEL)));
        platform.initialize();
        BackendServer server=new BackendServer(platform,Integer.parseInt(p.getProperty("server.port","8811")));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));server.start();System.out.println("HERB_SERVER_READY port="+server.port());
    }
}
