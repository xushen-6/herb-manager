# AI 开发接手手册

这份文件是新 AI 窗口的第一阅读入口。AI 接手本项目时，应先阅读本文件，再按需要阅读 `ARCHITECTURE.md`、`DATABASE.md`、`DEVELOPMENT.md` 和 `TASKS.md`。用户不需要再次提供提示词。

## 1. 项目身份

项目名称：本草 · 中药材智能管理平台。

项目类型：Java Swing 桌面应用。这里的“前端”是 Swing 视图，“后端”是同一个 Java 进程中的 controller、service、dao 和 JDBC 数据层。

硬性约束：

- 只使用 Java 17、Swing、JDBC 和 Maven。
- 不得加入 HTML、CSS、JavaScript、Vue、React、JavaFX、FXML 或浏览器运行时。
- 数据库正式模式使用 MySQL 8.0/8.4 兼容连接；本地预览使用 H2。
- 所有业务写操作必须经过 `cn.herb.service.Platform`，不能让 Swing 视图直接调用 DAO。
- 所有权限必须在 service 层再次校验，不能只依赖按钮隐藏。
- 用户要求课程项目可读、可演示、可解释；不要无理由引入复杂框架。

## 2. 当前状态

已完成：

- 登录、注册、验证码、三种角色权限和登录日志。
- 药材增删改查、名称/分类筛选、详情、库存预警。
- 事务化入库和出库、库存流水、负库存保护。
- 关键词问答、当前用户问答历史。
- 用户管理、个人资料和密码修改。
- Swing 登录页、工作台、药材管理、问答、用户、日志、个人资料界面。
- `start.bat`、`stop.bat`、`init.bat` 启动、结束和初始化脚本。
- `mvn test` 的 8 项业务测试。

当前可运行账户：

| 账号 | 密码 | 角色 | 权限 |
|---|---|---|---|
| `admin` | `Admin123!` | 管理员 | 全部功能 |
| `pharmacist` | `Pharm123!` | 药师 | 药材和库存 |
| `guest` | `Guest123!` | 访客 | 查看和问答 |

密码只在本地配置和数据库中使用，文档中不得新增真实数据库密码。

## 3. 新 AI 窗口的标准启动流程

1. 阅读本文件。
2. 阅读 `ARCHITECTURE.md` 和 `DEVELOPMENT.md`。
3. 用 `rg --files src docs` 查看文件，用 `rg` 搜索相关类和方法。
4. 明确本次任务涉及的文件和不涉及的文件；如果是并行开发，优先选择 `TASKS.md` 中没有文件重叠的任务。
5. 先写或更新能证明行为的测试，再改生产代码。UI 纯布局改动可以只做编译和人工启动验证。
6. 实现后运行 `mvn test`，需要交付 JAR 时再运行 `mvn package`。
7. 最终回复必须说明：改了什么、改了哪些文件、运行了什么验证、还有什么限制。

如果需求与本文件冲突，以用户最新要求为准；如果只是实现细节不确定，按本文件的默认设计自行决策，不要反复询问用户。

## 4. 重要入口和调用链

```text
App.main
  └─ Platform.initialize
       ├─ Database.initialize       创建表
       └─ seedHerbs                  首次写入演示用户和药材

LoginFrame
  └─ Platform.login / register

MainFrame
  ├─ MedicinalPanel ── Platform.medicinals / saveMedicinal / adjustStock
  ├─ ChatPanel      ── Platform.ask / history
  ├─ 用户管理       ── Platform.users / createUser / deleteUser
  ├─ 登录日志       ── Platform.loginLogs
  └─ 个人资料       ── Platform.updateProfile
```

`Tasks.run(...)` 用 `SwingWorker` 将数据库工作移出 EDT；新增数据库操作时应沿用它，避免界面卡死。

## 5. 新功能的完成标准

一个功能只有同时满足以下条件才算完成：

- 正常流程可从 Swing 界面触发。
- 业务规则在 `Platform` 中有明确校验。
- 角色越权调用会抛出 `SecurityException`。
- 输入错误会给出 `IllegalArgumentException` 或可理解的界面提示。
- 数据库写入使用参数化 SQL。
- 需要持久化的数据在重启后仍可读取。
- 关键规则有测试，且 `mvn test` 全部通过。
- 不破坏既有启动脚本和初始化脚本。

## 6. 不要做的事情

- 不要把 SQL、密码校验或权限判断塞进 Swing 事件监听器。
- 不要为简单页面引入 Spring、MyBatis、Lombok、前端工程或网络服务。
- 不要把 MySQL 密码写入 Java 源码、README 或提交记录。
- 不要让访客通过绕过按钮直接调用 service 就能写入数据。
- 不要静默吞掉数据库异常，也不要在 MySQL 失败时自动切换到另一个数据库。
- 不要在没有测试或编译验证的情况下声称功能完成。

## 7. 推荐的开发回复格式

```text
已阅读 docs/AI_HANDOFF.md。计划修改：...

实现结果：...
验证：mvn test（...）；mvn package（...）
未完成或风险：...
```
