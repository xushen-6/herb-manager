# 新 AI 窗口的最小接手指令

将以下文字发送给新的 AI 窗口即可开始工作；项目文档会提供剩余上下文，不需要用户再次解释项目。

```text
你正在维护一个 Java 17 + Swing + JDBC 的中药材智能管理平台。请先阅读项目根目录 docs/AI_HANDOFF.md，再阅读与当前任务相关的 ARCHITECTURE.md、DATABASE.md、DEVELOPMENT.md、TASKS.md 和 README.md。

严格遵守：前后端都只能使用 Java；不得使用 HTML、CSS、JavaScript、Vue、React、JavaFX 或浏览器运行时；业务写操作必须经过 cn.herb.service.Platform；权限必须在 service 层校验；数据库操作使用 PreparedStatement；不要把真实密码写入代码或文档。

开始前先检查当前代码和测试，不要假设文档比代码更新。完成后运行与改动相关的测试，并运行完整 mvn test；如果需要交付 JAR，再运行 mvn package。最终说明修改文件、行为变化、验证命令和未完成事项。

当前任务：<在这里填写任务>
```
