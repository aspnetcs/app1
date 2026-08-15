# 管理后台启动说明（Windows 交接用）

> 项目根目录假设为 `D:\work\ai-chat`（下称 `<ROOT>`）。

## 前置环境
- Docker Desktop
- Node.js 18+、JDK 17、Maven 3.9+

> PowerShell 里 npm 用 `npm.cmd`、mvn 用 `mvn.cmd`。

## 启动步骤（按顺序）

### 1. 启动数据库（PostgreSQL / Redis / MinIO）
```powershell
cd <ROOT>\deploy
docker compose up -d
docker compose ps   # 确认三个容器都 healthy
```

### 2. 启动后端 admin-api（端口 8081）
```powershell
cd <ROOT>\backend
mvn.cmd -pl admin-api -am spring-boot:run
```

需要登录接口时，另开一个终端启动 platform-api（端口 8080）：
```powershell
cd <ROOT>\backend
mvn.cmd -pl platform-api -am spring-boot:run
```

### 3. 启动前端（端口 5175）
```powershell
cd <ROOT>\admin-lite
npm.cmd install
npm.cmd run dev
```

## 访问
- 管理后台：`http://localhost:5175`（当前为免鉴权模式，无需登录直接进入）
- admin-api：`http://localhost:8081`

## 管理员账号（若需登录）
- 账号：`admin@webchat.com`
- 密码：`admin123456`

## 常见问题
- **接口不可用**：检查 8081 是否被其他程序占用（`netstat -ano | findstr 8081`），结束占用进程后重启 admin-api
- **数据库连不上**：确认 `docker compose ps` 中容器在运行
- **端口 5175 被占用**：`netstat -ano | findstr 5175` 找到 PID → `taskkill /PID <PID> /F`
