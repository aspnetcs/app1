用户想了解如何运行 uni-app H5 前端。让我先探索目录结构和配置：
        
            
toolName: Shell
            
status: success
          
          


​            
toolName: view_files
​            
status: success
​          
​            
filePath: E:\26-27-1\wyc\app1\uni-app\package.json
​          
这是一个 uni-app (Vue 3 + Vite) 多端项目。让我查看开发配置、构建产物和静态服务脚本：
​        
​            
toolName: view_files
​            
status: success
​          
​            
filePath: E:\26-27-1\wyc\app1\uni-app\vite.config.mjs
​          


​            
toolName: view_files
​            
status: success
​          
​            
filePath: E:\26-27-1\wyc\app1\uni-app\scripts\serve-h5-dist.mjs
​          


​            
toolName: Shell
​            
status: success
​          
​          


​            
toolName: view_files
​            
status: success
​          
​            
filePath: E:\26-27-1\wyc\app1\uni-app\nginx.conf
​          


​            
toolName: view_files
​            
status: success
​          
​            
filePath: E:\26-27-1\wyc\app1\uni-app\dist\build\h5\index.html
​          


​            
toolName: view_files
​            
status: success
​          
​            
filePath: E:\26-27-1\wyc\app1\uni-app\h5-serve.log
​          


​            
toolName: Shell
​            
status: success
​          
​          


​            
toolName: view_files
​            
status: success
​          
​            
filePath: E:\26-27-1\wyc\app1\uni-app\src\config\index.ts
​          
现在我理解了配置结构。关键发现：前端默认直接请求 `http://localhost:8080/api`。让我检查是否有 `.env` 文件配置和 CORS 设置：
​        
​            
toolName: Shell
​            
status: success
​          
​          
没有 `.env` 文件——前端默认直接请求 `http://localhost:8080/api`。让我检查后端 CORS 配置和请求方式：
​        
​            
toolName: Shell
​            
status: success
​          
​          


​            
toolName: view_files
​            
status: success
​          
​            
filePath: E:\26-27-1\wyc\app1\backend\platform-api\src\main\java\com\webchat\platformapi\infra\common\CorsConfig.java
​          
后端 CORS 允许所有 `http://localhost:*` 来源——所以从任意本地端口服务 H5 都能正常访问 API。现在让我验证预构建的 H5 能正常运行：
​        
​            
toolName: Shell
​            
status: success
​          
​          


​            
toolName: Shell
​            
status: success
​          
​          
H5 服务和后端 API 都正常运行。让我在浏览器中验证 H5 前端加载和 API 通信：
​        
​            
toolName: Exec
​            
status: success
​          
​          


​            
toolName: browser_navigate
​            
status: success
​          
toolName: browser_navigate
params: {"url":"http://127.0.0.1:41880/"}
​          


​            
toolName: browser_wait_for
​            
status: success
​          
toolName: browser_wait_for
params: {"time":3}
​          


​            
toolName: browser_snapshot
​            
status: success
​          
toolName: browser_snapshot
params: {}
​          
H5 前端加载成功！显示了 AI App 引导页面。让我检查构建日志确认构建方式：
​        
​            
toolName: view_files
​            
status: success
​          
​            
filePath: E:\26-27-1\wyc\app1\uni-app\build-h5.log
​          


​            
toolName: view_files
​            
status: success
​          
​            
filePath: E:\26-27-1\wyc\app1\uni-app\Dockerfile
​          
H5 前端正常运行。让我验证 API 跨域请求是否正常（从 41880 端口访问 8080）：
​        
​            
toolName: Exec
​            
status: success
​          
​          


​            
toolName: browser_evaluate
​            
status: success
​          
toolName: browser_evaluate
params: {"script":"(async function() {\n    try {\n      const resp = await fetch('http://localhost:8080/api/v1/risk/captcha/generate', { method: 'GET' });\n      const data = await resp.json();\n      return JSON.stringify({ status: resp.status, code: data.code, message: data.message, hasData: !!data.data });\n    } catch (e) {\n      return JSON.stringify({ error: e.message, name: e.name });\n    }\n  })()"}
​          


​            
toolName: Exec
​            
status: success
​          
​          


​            
toolName: browser_evaluate
​            
status: success
​          
toolName: browser_evaluate
params: {"script":"(async () => { try { const r = await fetch('http://localhost:8080/api/v1/risk/captcha/generate'); const d = await r.json(); return 'status=' + r.status + ' code=' + d.code; } catch(e) { return 'ERR:' + e.message; } })()"}
​          


​            
toolName: Exec
​            
status: success
​          
​          


​            
toolName: browser_evaluate
​            
status: success
​          
toolName: browser_evaluate
params: {"script":"fetch('http://localhost:8080/api/v1/risk/captcha/generate').then(r=>r.json()).then(d=>'ok code='+d.code).catch(e=>'err:'+e.message)"}
​          
CORS 跨域请求验证通过（`code=0` 成功）！H5 前端已完整可用。以下是完整的运行文档。

---

## uni-app H5 前端运行文档

### 项目概况

| 项目           | 说明                                                         |
| -------------- | ------------------------------------------------------------ |
| **前端目录**   | `E:\26-27-1\wyc\app1\uni-app`                                |
| **预构建产物** | `E:\26-27-1\wyc\app1\uni-app\dist\build\h5`（97 个文件，约 6MB） |
| **框架**       | uni-app (Vue 3 + Vite 5)                                     |
| **API 地址**   | 默认 `http://localhost:8080/api`（platform-api）             |
| **WebSocket**  | 默认 `ws://localhost:8080/ws`                                |
| **CORS**       | 后端允许 `http://localhost:*` 和 `http://127.0.0.1:*` 所有端口 |

### 前置条件

运行 H5 前端前，**必须先启动后端服务**：

```
PostgreSQL (5432) → Redis (6379) → platform-api (8080) → admin-api (8081)
```

可通过 `E:\26-27-1\wyc\Tools\start_all.bat` 一键启动后端。

---

### 方式一：运行预构建产物（推荐，用户所需）

使用项目自带的静态文件服务器，直接服务 `dist/build/h5` 目录：

```bash
cd E:\26-27-1\wyc\app1\uni-app
node ./scripts/serve-h5-dist.mjs 41880
```

- 访问地址：**http://127.0.0.1:41880/**
- 端口 `41880` 可改为任意空闲端口
- 前端直接请求 `http://localhost:8080/api`，CORS 已配置无需额外处理

> 当前已在运行中，可直接访问 http://127.0.0.1:41880/

### 方式二：开发模式（热更新）

适合二次开发，修改代码后自动刷新：

```bash
cd E:\26-27-1\wyc\app1\uni-app
npm install
npm run dev:h5
```

- 访问地址：**http://localhost:5173/**
- Vite 开发服务器，支持热模块替换（HMR）
- `dev:h5` 脚本同时运行 Tailwind CSS 编译和 uni-app H5 开发服务器

### 方式三：重新构建并运行

修改源码后重新打包：

```bash
cd E:\26-27-1\wyc\app1\uni-app
npm install
npm run build:h5
node ./scripts/serve-h5-dist.mjs 41880
```

- `build:h5` 会先验证 Vue 栈、编译 Tailwind CSS，再执行 uni-app H5 构建
- 构建产物输出到 `dist/build/h5/`

### 方式四：任意静态文件服务器

`dist/build/h5` 是标准 SPA 静态文件，可用任何工具服务：

```bash
# 使用 npx serve
cd E:\26-27-1\wyc\app1\uni-app\dist\build\h5
npx serve -p 41880

# 或使用 Python
python -m http.server 41880
```

### 方式五：Docker / Nginx（生产部署）

```bash
cd E:\26-27-1\wyc\app1\uni-app
docker build -t webchat-h5 .
docker run -d -p 80:80 webchat-h5
```

Nginx 配置（[nginx.conf](file:///E:/26-27-1/wyc/app1/uni-app/nginx.conf)）已包含：
- SPA 路由回退（`try_files $uri $uri/ /index.html`）
- `/api/` 反向代理到 `platform-api:8080`
- `/ws` WebSocket 代理
- 静态资源缓存（30天）
- gzip 压缩

---

### 关键配置说明

**API 地址配置**（[src/config/index.ts](file:///E:/26-27-1/wyc/app1/uni-app/src/config/index.ts#L50-L63)）：

```typescript
const envApiBase = import.meta.env.VITE_API_BASE || ''
apiBaseUrl: envApiBase || 'http://localhost:8080/api'
```

- 默认直接请求 `http://localhost:8080/api`
- 可通过环境变量 `VITE_API_BASE` 覆盖（构建时注入）
- 例如部署到生产服务器：`VITE_API_BASE=https://api.example.com/api npm run build:h5`

**CORS 配置**（[platform-api CorsConfig.java](file:///E:/26-27-1/wyc/app1/backend/platform-api/src/main/java/com/webchat/platformapi/infra/common/CorsConfig.java#L16)）：

```java
@Value("${cors.api-allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
```

允许所有 `localhost` 和 `127.0.0.1` 端口跨域访问，无需额外配置。

---

### 当前运行状态

| 服务           | 端口      | 状态         |
| -------------- | --------- | ------------ |
| PostgreSQL     | 5432      | ✅ 运行中     |
| Redis          | 6379      | ✅ 运行中     |
| platform-api   | 8080      | ✅ 运行中     |
| admin-api      | 8081      | ✅ 运行中     |
| admin-lite     | 5175      | ✅ 运行中     |
| **uni-app H5** | **41880** | ✅ **运行中** |

现在可以直接访问 **http://127.0.0.1:41880/** 体验 H5 前端。