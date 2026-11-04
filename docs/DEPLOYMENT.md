# 云端部署与免安装客户端

本地保留 Swing 界面和图片验证码。服务器运行 MySQL、注册邮箱验证码、AI 问答和数据库访问接口，桌面端通过 HTTPS 访问 `https://lzhan.xyz/herb-api`。客户端不需要安装 Java、MySQL，也不携带数据库密码、SMTP 授权码和 AI Key。

## 服务器

- 服务目录：`/opt/herb-manager`。
- 配置文件：`/etc/herb-manager/server.properties`，仅 root 和服务账号可读。
- Java 入口：`cn.herb.ServerApp`。
- 系统服务：`herb-manager.service`，开机启动，异常自动重启。
- HTTP 服务仅监听 `127.0.0.1:8811`，由现有 Nginx 的 `/herb-api/` 路径代理。
- MySQL 仅监听服务器本机，项目使用专用账号 `herb_service`，权限限定在 `herb_manager` 数据库。
- 发信沿用 QQ SMTP，无需另建 Postfix 邮箱服务器。
- 默认模型为通过实际模型列表确认的 `deepseek-flash`。在线调用失败时在服务器回退到药材知识库，并保存问答历史。
- 登录会话有效期 12 小时，退出后撤销；权限以服务器会话为准。
- 登录、注册和发验证码接口具备请求频率限制；邮箱验证码 5 分钟有效，发送间隔 60 秒，单次使用。

查看状态：`systemctl status herb-manager`。

查看日志：`journalctl -u herb-manager`。更新服务端 JAR 后执行 `systemctl restart herb-manager`。

## 客户端

项目根目录的 `config.properties` 只需：

```properties
server.url=https://lzhan.xyz/herb-api
```

未配置 `server.url` 时仍支持原来的本地数据库模式，便于课程实验和自动测试。

发布的 EXE 内含裁剪后的 Java 21 运行环境，适用于 Windows 64 位。第一次启动将运行文件解压到当前用户的 `%LOCALAPPDATA%\BencaoManager\versions`，无需管理员权限，不安装系统服务、不修改系统 Java。以后双击同一 EXE 直接运行。日志位于 `%LOCALAPPDATA%\BencaoManager\logs`。

## 重新打包

先编译并测试项目，将最新业务类和资源打包到带依赖的 JAR，再执行：

```powershell
./packaging/build-portable.ps1 -PreparedJar ./target/herb-manager.jar
```

构建需要 JDK 21 的 `jlink` 和 Windows .NET Framework C# 编译器。`packaging/herb.ico` 是 EXE 图标，`src/main/resources/assets/herb-icon.png` 是窗口图标。EXE 的 `--self-test` 参数可检查内置 Java 运行环境及 HTTPS 连接，结果写入用户目录的 `logs/self-test.log`。
