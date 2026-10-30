package cn.herb.dao;

import java.sql.*;

/** 每次操作独立连接，避免后台 SwingWorker 共享 Connection。 */
public final class Database {
    private final String url, username, password;
    public Database(String url, String username, String password) {
        this.url = url; this.username = username; this.password = password;
    }
    public Connection connect() throws SQLException { return DriverManager.getConnection(url,username,password); }
    public boolean mysql() { return url.startsWith("jdbc:mysql:"); }
    public void initialize() {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS app_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(32) NOT NULL UNIQUE, password_hash VARCHAR(32) NOT NULL, display_name VARCHAR(50) NOT NULL, role VARCHAR(20) NOT NULL)",
            "CREATE TABLE IF NOT EXISTS medicinal (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(32) NOT NULL UNIQUE, name VARCHAR(50) NOT NULL, category VARCHAR(30) NOT NULL, origin VARCHAR(80) NOT NULL, stock INT NOT NULL, price DECIMAL(12,2) NOT NULL, efficacy VARCHAR(1000) NOT NULL, usage_text VARCHAR(1000) NOT NULL, storage VARCHAR(200) NOT NULL, threshold_qty INT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS login_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(32) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS chat_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, question VARCHAR(500) NOT NULL, answer VARCHAR(4000) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS stock_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, medicinal_code VARCHAR(32) NOT NULL, medicinal_name VARCHAR(50) NOT NULL, quantity_change INT NOT NULL, remaining INT NOT NULL, operator_name VARCHAR(32) NOT NULL, note VARCHAR(200) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
        };
        try (Connection c = connect(); Statement s = c.createStatement()) { for(String sql : ddl) s.execute(sql); }
        catch (SQLException e) { throw JdbcDao.failure(e); }
    }
}
