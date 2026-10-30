# 系统架构与代码地图

## 分层规则

```text
view  ──> controller / service ──> dao ──> JDBC ──> MySQL 或 H2
  │              │
  └──────────────┴──> model
```

### `cn.herb.model`

只放数据模型和简单派生行为，不放 SQL、Swing 组件和数据库连接。

- `Role`：`ADMIN`、`PHARMACIST`、`GUEST`。
- `User`：脱敏会话对象，不包含密码摘要。
- `Medicinal`：药材档案，库存为 `int`，单价为 `BigDecimal`。
- `MedicinalDetails`：药材详情值对象。
- `ChatRecord`：问答历史。

### `cn.herb.dao`

- `Database`：保存 JDBC URL、账号、密码；创建表。
- `JdbcDao`：参数化查询、更新和库存事务。

DAO 只负责数据访问。业务错误统一转换为 `IllegalArgumentException` 或 `IllegalStateException`，不要返回原始 SQL 异常给用户界面。

### `cn.herb.service`

`Platform` 是当前唯一业务门面。它负责：输入校验、权限校验、事务调用、实体组装和问答匹配。新增功能优先扩展这个类，并同步增加测试；只有类明显变大时才拆分 service。

### `cn.herb.controller`

`Tasks.run` 负责在 `SwingWorker` 中执行耗时操作，并在 EDT 中回调结果和显示错误。任何数据库查询或更新都不能直接在 Swing 事件线程中执行。

### `cn.herb.view`

- `Theme`：颜色、字体、按钮、表格和布局工具。
- `LoginFrame`：登录和访客注册。
- `MainFrame`：侧栏、菜单、工作台、用户、日志、资料页面。
- `MedicinalPanel`：药材查询、维护、库存、详情。
- `ChatPanel`：问答输入、显示和历史。

视图只负责收集输入、调用 `Platform`、刷新组件；禁止在视图中拼 SQL、计算权限或直接访问 `Database`。

### 程序入口

- `App`：正常启动 Swing 应用，读取根目录 `config.properties`。
- `InitApp`：命令行初始化表、演示用户和药材数据后退出。

## 编译与打包

`pom.xml` 使用 Java 17、MySQL Connector/J、H2、JUnit 5 和 Shade Plugin。最终 JAR 的入口是 `cn.herb.App`。

```powershell
mvn test
mvn package
java -jar target\herb-manager.jar
```

## 新增模块的推荐顺序

1. 在 `model` 中定义记录或枚举。
2. 在 `Database` 增加表结构，必要时添加初始化种子。
3. 在 `Platform` 增加业务方法和权限校验。
4. 在 `src/test/java/cn/herb/PlatformTest.java` 增加 H2 测试。
5. 在 `view` 增加界面，并使用 `Tasks.run`。
6. 更新 `DATABASE.md`、`TASKS.md` 和 README 的功能列表。
