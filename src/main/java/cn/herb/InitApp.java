package cn.herb;

import cn.herb.dao.Database;
import cn.herb.service.Platform;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 命令行初始化入口：创建表、演示用户和基础药材数据后退出。 */
public final class InitApp {
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        Path file = Path.of(args.length == 0 ? "config.properties" : args[0]);
        try (InputStream in = Files.newInputStream(file)) { p.load(in); }
        Database db = new Database(
                p.getProperty("jdbc.url"),
                p.getProperty("jdbc.username"),
                p.getProperty("jdbc.password", ""));
        new Platform(db).initialize();
        System.out.println("DATABASE_INITIALIZED: " + (db.mysql() ? "MySQL" : "H2"));
        // This command is intentionally one-shot, so terminate after initialization.
        System.exit(0);
    }
}
