# 校园二手平台

`fronted/` 是 Vue 3、Vite 与 Vue Router 前端。`backend/` 是 Java 17、Spring Boot 与 MyBatis 后端。`sql/` 保存精简版 10 张表及角色初始化脚本。

## 本地启动

先在 MySQL 8 中依次执行 `sql/01_create_schema.sql` 和 `sql/02_init_data.sql`，再使用有权访问 `campus_market` 的应用数据库账号。

第一个 PowerShell 终端启动后端。将下面的数据库账号和密码改为本机配置。

```powershell
cd backend
$env:DB_USER = '你的数据库账号'
$env:DB_PASSWORD = '你的数据库密码'
mvn spring-boot:run
```

后端默认监听 8080。可设置 `DB_URL`、`SERVER_PORT`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`。Redis 用于后续登录和拍卖功能，当前公开商品接口只访问 MySQL。

第二个终端启动前端。使用 Node.js 20.19+ 的 20.x 或 Node.js 22.12+。

```powershell
cd fronted
npm ci
npm run dev
```

打开 Vite 输出的本地地址。开发代理将 `/api` 转到后端，后端端口有改动时在启动前端前设置 `BACKEND_URL`。

## 当前入口

- `GET /api/health` 检查后端进程是否可访问。
- `GET /api/items` 通过 MyBatis 读取已审核的一口价商品和进行中的拍卖。新库没有商品时返回空数组。
- 登录、RBAC、发布、聊天、拍卖操作与审核的业务接口后续实现。当前 Spring Security 仅开放以上两个公开入口。
