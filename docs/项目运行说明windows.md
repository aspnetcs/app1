# AI App 项目运行说明（Windows 本机版）

这份文档是给没有后端经验的同学看的，目标是在 Windows 电脑上不用 Docker，把后端跑起来，并用微信开发者工具打开编译好的微信小程序前端。

下面所有路径都只是示例。你把项目解压到哪里，就把命令里的 `D:\Projects\ai-app` 换成你自己的项目目录。

## 1. 先看一眼要做什么

整个项目分成三块：

1. 后端接口：`backend`
2. 微信小程序前端：`uni-app`
3. 管理端前端：`admin-lite`，可选，用来配置渠道、模型等后台内容

本机运行顺序：

1. 安装 Java、Maven、Node.js、PostgreSQL、Redis、MinIO、微信开发者工具
2. 在 PostgreSQL 里创建数据库 `webchat`
3. 启动 Redis 和 MinIO
4. 先启动用户端后端 `platform-api`，端口 `8080`
5. 再启动管理端后端 `admin-api`，端口 `8081`
6. 编译微信小程序前端
7. 用微信开发者工具导入 `uni-app\dist\build\mp-weixin`

## 2. 需要安装的软件

请先在 Windows 上安装这些软件：

| 软件 | 建议版本 | 用途 |
| --- | --- | --- |
| Java JDK | 17 | 运行后端 |
| Maven | 3.8 或更高 | 编译和启动后端 |
| Node.js | 18 或 20 | 安装和编译前端 |
| PostgreSQL | 16 推荐 | 数据库 |
| pgvector | 匹配 PostgreSQL 版本 | 知识库向量字段会用到 |
| Redis 兼容服务 | Redis / Memurai 均可 | 缓存、登录态、限流等 |
| MinIO | Windows 版 `minio.exe` | 文件和图片对象存储 |
| 微信开发者工具 | 稳定版即可 | 打开微信小程序编译产物 |

安装完成后，打开 PowerShell，执行下面命令检查：

```powershell
java -version
mvn -v
node -v
npm -v
psql --version
```

能看到版本号，就说明这些命令已经可以用了。

## 3. 解压项目

例如把项目放到：

```text
D:\Projects\ai-app
```

后面命令都用这个路径举例。如果你的项目在桌面、E 盘或其他目录，把命令里的路径换成你的实际路径。

## 4. 初始化 PostgreSQL 数据库

后端默认会连接：

| 配置 | 默认值 |
| --- | --- |
| 数据库地址 | `localhost:5432` |
| 数据库名 | `webchat` |
| 用户名 | `webchat` |
| 密码 | `webchat_dev_password` |

打开 PowerShell，进入 PostgreSQL 管理命令行：

```powershell
psql -U postgres
```

然后依次执行：

```sql
CREATE USER webchat WITH PASSWORD 'webchat_dev_password';
CREATE DATABASE webchat OWNER webchat;
\c webchat
CREATE EXTENSION IF NOT EXISTS vector;
\q
```

如果提示 `extension "vector" is not available`，说明 PostgreSQL 没装 `pgvector` 插件。请先安装和你的 PostgreSQL 版本匹配的 `pgvector`，再重新执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

后端第一次启动时会自动执行数据库迁移脚本并建表，不需要手动导入 SQL。

## 5. 启动 Redis

后端默认连接：

```text
localhost:6379
```

如果你安装的是 Redis Windows 版，启动 Redis 服务即可。

如果你安装的是 Memurai，安装后一般会自动作为 Windows 服务运行。只要它兼容 Redis 协议，并监听 `6379` 端口即可。

可以用下面命令验证：

```powershell
redis-cli ping
```

返回：

```text
PONG
```

就表示 Redis 可用。

## 6. 启动 MinIO

后端默认连接：

| 配置 | 默认值 |
| --- | --- |
| MinIO API | `http://localhost:9000` |
| MinIO 控制台 | `http://localhost:9001` |
| 用户名 | `minioadmin` |
| 密码 | `minioadmin123` |
| bucket | `webchat` |

假设你把 `minio.exe` 放到了：

```text
D:\Tools\minio\minio.exe
```

先创建一个数据目录，例如：

```powershell
mkdir D:\minio-data
```

然后启动 MinIO：

```powershell
$env:MINIO_ROOT_USER="minioadmin"
$env:MINIO_ROOT_PASSWORD="minioadmin123"
D:\Tools\minio\minio.exe server D:\minio-data --console-address ":9001"
```

看到 MinIO 输出 `API:` 和 `WebUI:` 地址，就表示启动成功。

浏览器打开：

```text
http://localhost:9001
```

用 `minioadmin` / `minioadmin123` 登录即可。`webchat` bucket 后端会在需要时自动创建。

## 7. 启动用户端后端 platform-api

先启动 `platform-api`。它会自动建表和升级数据库结构。

打开一个新的 PowerShell：

```powershell
cd D:\Projects\ai-app\backend
mvn -pl platform-api -am spring-boot:run
```

等待终端出现类似内容：

```text
Started PlatformApiApplication
```

并且没有红色报错，就表示用户端接口启动成功。

默认地址：

```text
http://localhost:8080
```

这个终端不要关，后端服务需要一直运行。

## 8. 启动管理端后端 admin-api

确认 `platform-api` 已经启动成功后，再开第二个 PowerShell：

```powershell
cd D:\Projects\ai-app\backend
mvn -pl admin-api -am spring-boot:run
```

等待终端出现类似内容：

```text
Started AdminApiApplication
```

默认地址：

```text
http://localhost:8081
```

这个终端也不要关。

## 9. 编译微信小程序前端

打开第三个 PowerShell：

```powershell
cd D:\Projects\ai-app\uni-app
npm install
npm run build:mp-weixin
```

编译完成后，会生成这个目录：

```text
D:\Projects\ai-app\uni-app\dist\build\mp-weixin
```

这个目录就是微信开发者工具要导入的目录。

注意：不要导入 `uni-app` 源码目录，也不要导入 `uni-app\src`。一定要导入编译后的：

```text
uni-app\dist\build\mp-weixin
```

## 10. 微信开发者工具打开前端

打开微信开发者工具，按下面操作：

1. 点击“导入项目”
2. 项目目录选择：`D:\Projects\ai-app\uni-app\dist\build\mp-weixin`
3. AppID 有真实小程序就填真实 AppID，没有就选择测试号或按工具提示使用测试方式
4. 项目名称随便填，例如 `AI App`
5. 点击“导入”
6. 进入后点击“编译”

开发阶段如果提示接口域名不合法，进入微信开发者工具：

```text
详情 -> 本地设置
```

勾选：

```text
不校验合法域名、web-view、TLS 版本以及 HTTPS 证书
```

本机调试时，前端默认会请求：

```text
http://localhost:8080/api
ws://localhost:8080/ws
```

所以必须保证 `platform-api` 正在运行。

## 11. 真机预览时要注意

微信开发者工具里的模拟器访问 `localhost` 通常没问题。

但如果扫码到手机上真机预览，手机上的 `localhost` 指的是手机自己，不是电脑。所以真机预览需要用电脑的局域网 IP。

例如电脑 IP 是：

```text
192.168.1.20
```

那么编译前端前，在 PowerShell 里执行：

```powershell
cd D:\Projects\ai-app\uni-app
$env:VITE_API_BASE="http://192.168.1.20:8080/api"
$env:VITE_WS_BASE="ws://192.168.1.20:8080/ws"
npm run build:mp-weixin
```

然后重新用微信开发者工具导入或重新编译 `dist\build\mp-weixin`。

同时确认：

1. 手机和电脑在同一个 Wi-Fi
2. Windows 防火墙允许访问 `8080`
3. `platform-api` 后端没有关闭

## 12. 可选：启动管理端前端

如果需要进入后台配置模型、渠道、功能开关，可以启动 `admin-lite`。

打开新的 PowerShell：

```powershell
cd D:\Projects\ai-app\admin-lite
npm install
npm run dev
```

终端会显示一个访问地址，通常类似：

```text
http://localhost:5173
```

管理端默认连接：

```text
http://localhost:8081
```

也就是前面启动的 `admin-api`。

## 13. 修改默认连接配置

如果你不想使用默认数据库、Redis 或 MinIO，可以改后端配置文件：

```text
backend\platform-api\src\main\resources\application.yml
backend\admin-api\src\main\resources\application.yml
```

常见配置项：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/webchat
    username: webchat
    password: webchat_dev_password
  data:
    redis:
      host: localhost
      port: 6379

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin123
  bucket: webchat
```

不熟悉配置时，建议先按默认值运行，跑通后再改。

## 14. 常见问题

### 14.1 Maven 下载很慢

第一次运行后端时，Maven 会下载依赖，可能需要几分钟。网络慢时可以配置 Maven 国内镜像。

### 14.2 端口被占用

如果提示 `8080`、`8081`、`5432`、`6379`、`9000` 或 `9001` 被占用，说明这些端口已经有程序在用。

查看占用：

```powershell
netstat -ano | findstr :8080
netstat -ano | findstr :8081
netstat -ano | findstr :5432
netstat -ano | findstr :6379
netstat -ano | findstr :9000
```

可以关闭占用程序，或者修改项目配置里的端口。

### 14.3 数据库连接失败

检查这几项：

1. PostgreSQL 是否正在运行
2. 数据库 `webchat` 是否已经创建
3. 用户名是不是 `webchat`
4. 密码是不是 `webchat_dev_password`
5. 端口是不是 `5432`

### 14.4 `CREATE EXTENSION vector` 失败

说明 PostgreSQL 没装 `pgvector` 插件，或者插件版本和 PostgreSQL 版本不匹配。

解决办法：

1. 安装与 PostgreSQL 版本匹配的 `pgvector`
2. 重新进入数据库执行 `CREATE EXTENSION IF NOT EXISTS vector;`
3. 再重新启动 `platform-api`

### 14.5 Redis 连接失败

检查 Redis 或 Memurai 是否启动，并确认监听端口是 `6379`。

验证命令：

```powershell
redis-cli ping
```

返回 `PONG` 才算正常。

### 14.6 MinIO 上传失败

检查：

1. MinIO 是否启动
2. `http://localhost:9001` 是否能打开
3. 用户名和密码是否是 `minioadmin` / `minioadmin123`
4. `9000` 端口是否被占用

### 14.7 微信开发者工具导入后空白或接口失败

重点检查：

1. 导入目录是不是 `uni-app\dist\build\mp-weixin`
2. `platform-api` 是否正在运行
3. 微信开发者工具是否勾选“不校验合法域名”
4. 如果是真机预览，前端接口地址是否换成电脑局域网 IP

## 15. 默认地址汇总

| 服务 | 地址 |
| --- | --- |
| 用户端后端 platform-api | `http://localhost:8080` |
| 管理端后端 admin-api | `http://localhost:8081` |
| 用户端 API 前缀 | `http://localhost:8080/api` |
| 用户端 WebSocket | `ws://localhost:8080/ws` |
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| MinIO API | `http://localhost:9000` |
| MinIO 控制台 | `http://localhost:9001` |
| 微信小程序编译产物 | `uni-app\dist\build\mp-weixin` |

## 16. 最短运行命令汇总

前提是 PostgreSQL、Redis、MinIO 都已经启动。

终端 1：

```powershell
cd D:\Projects\ai-app\backend
mvn -pl platform-api -am spring-boot:run
```

终端 2：

```powershell
cd D:\Projects\ai-app\backend
mvn -pl admin-api -am spring-boot:run
```

终端 3：

```powershell
cd D:\Projects\ai-app\uni-app
npm install
npm run build:mp-weixin
```

微信开发者工具导入：

```text
D:\Projects\ai-app\uni-app\dist\build\mp-weixin
```
