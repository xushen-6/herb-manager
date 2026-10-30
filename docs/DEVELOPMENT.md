# 开发规范与验证手册

## 环境要求

- JDK 17 或更高版本。
- Maven 3.9 或更高版本。
- MySQL 8.0/8.4（正式数据库模式）或 H2（本地模式）。
- Windows 下可使用 `start.bat`、`stop.bat`、`init.bat`。

## 常用命令

```powershell
# 业务测试
mvn test

# 构建可执行 JAR
mvn package

# 启动
java -jar target\herb-manager.jar

# 直接使用脚本
.\init.bat
.\start.bat
.\stop.bat
```

## 测试规则

现有测试位于 `src/test/java/cn/herb/PlatformTest.java`，使用随机 H2 内存数据库。测试已经覆盖：

- 密码错误和注册邮箱验证码由 service 统一校验。
- 注册、重复账号和角色默认值。
- 访客写入、药师用户管理越权。
- 药材增删改查、筛选、重复编号和非法库存。
- 并发出库、负库存保护和流水。
- 问答匹配、未匹配问题和历史隔离。
- 个人资料和密码修改。
- 初始化幂等性。

新增业务行为时，优先在 `PlatformTest` 中添加一个失败测试，再实现最小代码使其通过。完成前必须运行完整 `mvn test`，不能只运行单个测试类。

## Swing 规则

- 数据库查询和写入必须通过 `Tasks.run`。
- 修改组件必须回到 EDT；`SwingWorker.done()` 已经在 EDT 中执行。
- 按钮文字、提示和字段标签保持中文，错误提示说明用户下一步怎么处理。
- 公共样式使用 `Theme`，不要在每个面板复制颜色和字体常量。
- 表格显示不可编辑；编辑通过详情/编辑窗口完成。
- 新增页面要在 `MainFrame` 增加侧栏按钮和菜单入口，并设置组件 `name` 以便 UI 检查。

## SQL 和异常规则

- 所有用户输入都必须使用 `PreparedStatement` 参数，不得字符串拼 SQL 值。
- 唯一键冲突转换成用户可理解的提示。
- 权限失败使用 `SecurityException`。
- 输入失败使用 `IllegalArgumentException`。
- 数据库连接或 SQL 失败使用 `IllegalStateException` 并保留 cause。
- 不要捕获异常后静默忽略。

## 完成前检查清单

- [ ] 需求涉及的角色都验证过。
- [ ] 正常流程、空输入、重复数据和错误数据库连接都有合理行为。
- [ ] `mvn test` 通过。
- [ ] 需要交付时 `mvn package` 通过。
- [ ] README 和相关设计文档已同步。
- [ ] 没有把 `config.properties`、密码、`target` 或 `data` 加入提交。
- [ ] 新窗口可从菜单或侧栏进入，退出和返回流程正常。
