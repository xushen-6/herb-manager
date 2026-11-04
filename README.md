# 本草 · 中药材智能管理平台

Java 17 + Swing + JDBC + MySQL 8.0/8.4（无 HTML、JavaScript、Vue 或其他前端语言）。

## 云端免安装版本

发布文件位于 `java期末/发布/本草中药材管理平台.exe`，内置 Java 21，适用于 Windows 64 位，双击即可运行，需要联网。首次启动会将运行文件解压到当前用户目录，以后无需重复解压。

数据库、注册邮箱验证码、DeepSeek 问答及历史记录均在服务器处理，客户端通过 `https://lzhan.xyz/herb-api` 连接。客户端配置只有 `server.url`，不包含数据库密码、邮箱授权码和 AI Key。图片验证码在本地生成。演示账号密码保持不变。

服务端运维及重新打包说明见 `docs/DEPLOYMENT.md`。

## 本地开发与独立数据库模式

1. 双击根目录的 `start.bat` 即可一键启动；如果尚未生成 JAR，脚本会自动调用 Maven 构建。
2. 双击根目录的 `stop.bat` 结束本项目；脚本会询问是否同时停止 MySQL80 服务，以免影响其他项目。
3. 在新设备上先双击根目录的 `init.bat`，选择 MySQL 或本地 H2；脚本会生成配置、创建表并写入演示数据。
4. 也可以直接运行 `mvn test` 验证业务层。
5. 复制 `config.properties.example` 为 `config.properties`，填入 MySQL 账户和 QQ SMTP 配置；注册邮箱验证码依赖 SMTP 配置。如果不复制，程序使用本地 H2 演示数据库，但不能发送注册邮件。
6. 使用 `mvn package` 打包，运行 `java -jar target/herb-manager.jar`。
7. 演示账户：`admin / 123456`、`yaoshi / 123456`、`123456 / 123456`。

## 功能

- 管理员：药材与用户全部权限、登录日志、库存流水。
- 药师：药材增删改查、入库出库、库存流水，不能管理用户。
- 访客：查看药材、药材详情、关键词问答和自己的问答历史。
- 药材：名称/分类筛选，详情，低库存弹窗，事务化库存变更。
- 资料：昵称、密码修改；密码按作业要求使用 MD5 存库。
- 登录：账号、密码和本地生成的 4 位图片验证码，不区分大小写，点击图片刷新，5 分钟有效且每次提交后更换。
- 注册：使用 QQ SMTP 邮箱验证码。

## 项目结构

`model` 实体，`dao` JDBC 数据访问，`service` 业务与权限，`controller` Swing 后台任务，`view` Java Swing 界面，`util` 工具。

作业要求和基础设计见 `docs/DESIGN.md`；实施计划见 `docs/PLAN.md`。

## 给 AI 开发者的文档

新 AI 窗口请先阅读 [`docs/AI_HANDOFF.md`](docs/AI_HANDOFF.md)，它包含项目现状、架构、调用链、硬性约束和完成标准。数据库说明见 [`docs/DATABASE.md`](docs/DATABASE.md)，开发与测试规范见 [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)，可并行任务见 [`docs/TASKS.md`](docs/TASKS.md)。如果需要最短接手文本，可直接使用 [`docs/AI_PROMPT.md`](docs/AI_PROMPT.md)。
