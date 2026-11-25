# 校园二手平台

`fronted/` 是 Vue 3、Vite 与 Vue Router 前端，`backend/` 是 Java 17、Spring Boot 与 MyBatis 后端。`sql/` 保存 10 张业务表及角色初始化脚本。

## 本地启动

1. 在 MySQL 8 中依次执行 `sql/01_create_schema.sql` 和 `sql/02_init_data.sql`，准备有权读写 `campus_market` 的应用数据库账号。
2. 启动 Redis。默认地址为 `127.0.0.1:6379`。使用 Docker 时可在独立终端运行：

   ```powershell
   docker run --rm --name campus-redis -p 127.0.0.1:6379:6379 redis:7
   ```

3. 在第一个 PowerShell 终端配置并启动后端。将示例账号和密码替换成本机配置。首次生成签名密钥后可在后续启动中复用。

   ```powershell
   cd backend
   $env:DB_USER = '你的数据库账号'
   $env:DB_PASSWORD = '你的数据库密码'
   $env:APP_JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
   mvn spring-boot:run
   ```

4. 在第二个终端启动前端。使用 Node.js 20.19+ 的 20.x 或 Node.js 22.12+。

   ```powershell
   cd fronted
   npm ci
   npm run dev
   ```

打开 Vite 输出的地址。开发代理将 `/api` 和 `/ws` 转发至后端，默认后端端口为 8080。课设演示启动一个后端实例处理聊天与结算推送。可通过 `BACKEND_URL` 调整代理目标。后端可配置 `DB_URL`、`SERVER_PORT`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 和 `ITEM_IMAGE_DIR`。图片默认存于后端启动目录的 `uploads`。

## 导入演示数据

从项目根目录运行以下 PowerShell 命令。它将建表、角色和样例数据导入独立的 `campus_market_demo` 数据库。

```powershell
./scripts/Initialize-Demo.ps1 -User root -PromptForPassword
```

MySQL 使用其他本地端口时传入 `-Port`。启动后端前，把连接地址指向演示库。应用数据库账号需要对该演示库有读写权限。

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/campus_market_demo?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
```

以下账号共用演示密码 `CampusDemo2026!`。

| 身份 | 手机号 | 演示入口 |
| --- | --- | --- |
| 卖家 | `+12025550101` | 我的发布、面交预约 |
| 买家 | `+12025550102` | 消息、面交预约、拍卖出价 |
| 管理员 | `+12025550103` | 商品审核 |
| 第二位买家 | `+12025550104` | 竞价和预约冲突演示 |

首次导入后，集市有在售教材和进行中的球拍拍卖。管理员可审核台灯，卖家可查看鼠标的驳回原因。卖家和买家可在「消息」和「面交预约」中查看打气筒的交易记录。用不同浏览器配置登录不同账号，便于同时打开买卖双方页面。

重复执行导入命令可保留已经审批、出价或完成的交易。演示到期结算时，可另发一件时长为 1 分钟的拍卖商品，审核通过并出价后等待结算。

## 手动准备测试账号

从注册页创建卖家、买家和管理员候选账号。所有注册用户默认获得 `STUDENT` 角色。由数据库管理者将管理员角色赋予指定账号，替换下例中的完整手机号后执行：

```sql
USE campus_market;
SET @admin_phone = '+8613800000000';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u JOIN roles r ON r.code = 'ADMIN'
WHERE u.phone = @admin_phone
ON DUPLICATE KEY UPDATE role_id = user_roles.role_id;
```

使用不同浏览器配置或无痕窗口登录多个账号。同一账号的 JWT 固定有效 24 小时，再次登录会使旧会话失效。

## 核心功能

- **发布审核**。卖家保存草稿、上传图片并提交。管理员可审批或填写驳回原因，审核通过的商品才进入公开列表。
- **一口价交易**。买家预约后商品锁定，双方都确认才标记已售。取消一口价预约后可再次预约。
- **私聊**。买家从详情页联系卖家。消息由 MySQL 保存，WebSocket 推送更新，重连后可补拉历史。
- **拍卖**。审核通过开始计时，买家按起拍价及加价幅度出价。短时 Redis 缓存用于行情展示，到期结算由 MySQL 确定买家并创建面交预约。定时结算默认启用，可通过 `APP_AUCTION_SETTLEMENT_ENABLED=false` 暂停。

## 启用 Kafka 结果推送

Kafka 默认关闭。启用后，拍卖结算事务提交时发送 `auction.settled` 事件，消费者通过 WebSocket 将结果发给在线的交易双方。用户可在面交预约中查看已经持久化的中标结果。

```powershell
docker run --rm --name campus-kafka -p 127.0.0.1:9092:9092 apache/kafka:4.1.2
```

在启动后端前设置：

```powershell
$env:APP_KAFKA_ENABLED = 'true'
$env:KAFKA_BOOTSTRAP_SERVERS = '127.0.0.1:9092'
```

日志出现 `Received auction settlement` 代表消费者已收到事件。Kafka 投递采用尽力发送，发布失败时已提交的成交结果仍保存在 MySQL。
