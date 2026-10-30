# 数据库说明

## 连接配置

程序从项目根目录读取 `config.properties`。该文件已被 `.gitignore` 忽略，不能提交真实密码。

MySQL 示例：

```properties
jdbc.url=jdbc:mysql://127.0.0.1:3306/herb_manager?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
jdbc.username=root
jdbc.password=填写本机密码
```

H2 示例：

```properties
jdbc.url=jdbc:h2:file:./data/herb-manager;MODE=MySQL;DB_CLOSE_DELAY=-1
jdbc.username=sa
jdbc.password=
```

也可以直接运行根目录 `init.bat`。脚本会创建数据库、写入配置并调用 `InitApp` 建表和写入演示数据。

## 表结构

| 表 | 用途 | 关键字段 |
|---|---|---|
| `app_user` | 用户和角色 | `username` 唯一，`password_hash`，`role` |
| `medicinal` | 药材档案 | `code` 唯一，名称、分类、库存、单价、功效、用法、储存、阈值 |
| `login_log` | 登录日志 | 账号、时间 |
| `chat_history` | 用户问答历史 | `user_id` 外键、问题、回答、时间 |
| `stock_log` | 入出库记录 | 药材、变动数量、结余、操作人、备注、时间 |
| `app_meta` | 初始化标记 | `seeded=true` 防止重复写入演示药材 |

表由 `Database.initialize()` 使用 `CREATE TABLE IF NOT EXISTS` 创建。应用首次启动时 `Platform.initialize()` 创建默认用户和药材；重复启动不会重复插入种子。

## 数据规则

- 用户名：3～32 位字母、数字或下划线。
- 密码：8～64 位；课程要求使用 MD5 保存，后续如果修改算法必须同时处理旧数据迁移。
- 药材编号唯一，名称、分类、产地和文本字段不能为空。
- 库存和阈值必须是非负整数。
- 单价使用 `BigDecimal`，最多两位小数。
- 入出库数量不能为 0；库存更新和流水插入在同一事务中完成。
- 出库后库存不能小于 0；更新失败时不得留下流水。
- 删除用户会级联删除其问答历史；不能删除当前登录账户。

## 修改数据库的规则

新增字段时必须同时修改：

1. `Database.initialize()` 的建表语句。
2. `Platform` 的查询、插入、更新和实体映射。
3. 相关测试。
4. 本文件的表说明。
5. 初始化脚本或兼容旧库的迁移逻辑。

当前项目没有独立迁移框架。需要改已有数据库结构时，必须提供幂等 SQL 或明确的升级步骤，不能只修改 Java 查询。
