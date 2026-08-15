# AI 智能助手 — 全端构建与打包完整记录

> 记录从环境准备到微信小程序、H5 网页版、Android APK 全流程的操作步骤、踩坑与解决方案。
> 项目根目录：`D:\1\ai-app\app`

---

## 目录

- [1. 项目概览](#1-项目概览)
- [2. 环境准备](#2-环境准备)
  - [2.1 Node.js 与 npm](#21-nodejs-与-npm)
  - [2.2 JDK 17](#22-jdk-17)
  - [2.3 Android SDK](#23-android-sdk)
  - [2.4 Gradle 8.7](#24-gradle-87)
- [3. 编译微信小程序](#3-编译微信小程序)
- [4. 编译 H5 网页版](#4-编译-h5-网页版)
- [5. 编译 Android APP 并打包 APK](#5-编译-android-app-并打包-apk)
  - [5.1 uni-app 编译原生源码](#51-uni-app-编译原生源码)
  - [5.2 本地构建流程（HBuilderX 不可用时的备选）](#52-本地构建流程hbuilderx-不可用时的备选)
  - [5.3 创建 Android 壳工程](#53-创建-android-壳工程)
  - [5.4 集成 dist 与构建 APK](#54-集成-dist-与构建-apk)
- [6. iOS 打包说明](#6-ios-打包说明)
- [7. 常见问题与修复](#7-常见问题与修复)
- [8. 产物清单](#8-产物清单)

---

## 1. 项目概览

| 模块 | 路径 | 技术栈 |
| --- | --- | --- |
| 用户端 | `uni-app/` | uni-app 3.x、Vue 3、Pinia、Vue-i18n、Tailwind |
| 管理后台（精简版） | `admin-lite/` | React 18、TypeScript、Vite |
| 后端 API | `backend/` | Spring Boot 3.2.5、Maven 多模块（common / platform-api / admin-api）、PostgreSQL、Redis、MinIO |
| 部署 | `deploy/` | docker-compose |

构建工具版本（最终落地）：

| 工具 | 版本 | 路径 |
| --- | --- | --- |
| Node.js | 24.17.0 | `C:\Program Files\nodejs` |
| JDK | 17.0.2 (Eclipse Temurin) | `D:\1\ai-app\app\build-tools\jdk-17.0.2` |
| Android SDK | 34.0.0（cmdline-tools 12.0、build-tools 34.0.0、platform-tools 35.x、platforms;android-34） | `D:\1\ai-app\app\build-tools\android-sdk` |
| Gradle | 8.7 | `D:\1\ai-app\app\build-tools\gradle-8.7` |
| Android Gradle Plugin | 8.4.0 | 工程内声明 |
| 编译器 | uni-app CLI 4.84 (vue3) | `node_modules/@dcloudio/vite-plugin-uni` |

---

## 2. 环境准备

> 为避免破坏系统 PATH 与已有软件，所有构建相关工具统一安装在 `D:\1\ai-app\app\build-tools\` 下。每次新终端都需要重新设置一次环境变量（见下文章节）。

### 2.1 Node.js 与 npm

- 安装包：`D:\1\ai-app\node-v24.17.0-x64.msi`
- 实际安装路径：`C:\Program Files\nodejs`
- 验证：

```powershell
node --version    # v24.17.0
npm --version     # 10.x
```

> 注意：PowerShell 默认禁止运行 `.ps1` 脚本，因此 `npm` 命令需改用 `npm.cmd`，否则会报“无法加载文件…运行脚本已禁用”。

将 Node 写入 PATH（PowerShell）：

```powershell
$env:Path = "C:\Program Files\nodejs;$env:Path"
```

### 2.2 JDK 17

- 路径：`D:\1\ai-app\app\build-tools\jdk-17.0.2`
- 验证：

```powershell
& "D:\1\ai-app\app\build-tools\jdk-17.0.2\bin\java.exe" -version
# openjdk version "17.0.2" 2022-01-18
```

- Gradle 强依赖 JDK 17；JDK 8 / 11 会导致 `Unsupported class file major version` 错误。

### 2.3 Android SDK

> **目标**：安装 `cmdline-tools`（含 `sdkmanager`）、`platform-tools`（含 `adb`）、`build-tools;34.0.0`、`platforms;android-34`。

#### 步骤 1：解压 cmdline-tools

1. 下载 `commandlinetools-win-*.zip`（官方或镜像）。
2. 解压到 `D:\1\ai-app\app\build-tools\android-sdk\cmdline-tools\`
3. **目录结构必须正确**（`sdkmanager.bat` 依赖路径识别）：

```
D:\1\ai-app\app\build-tools\android-sdk\
└── cmdline-tools\
    └── latest\
        ├── bin\
        │   ├── sdkmanager.bat
        │   └── …
        ├── lib\
        └── source.properties
```

> 常见错误：解压后得到 `cmdline-tools/bin/sdkmanager.bat`（少一层 `latest`）— 必须重组目录。

#### 步骤 2：接受许可 + 安装组件

```powershell
$jdk = "D:\1\ai-app\app\build-tools\jdk-17.0.2"
$sdk = "D:\1\ai-app\app\build-tools\android-sdk"
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$sdk\cmdline-tools\latest\bin;$env:Path"

# 接受所有 license（按 y 自动 20 次）
("y`n" * 20) -join "" | & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses

# 安装必要组件
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

实际耗时约 1-3 分钟。完成后检查目录：

```text
android-sdk/
├── build-tools/34.0.0/aapt2.exe
├── cmdline-tools/latest/bin/sdkmanager.bat
├── licenses/
├── platform-tools/adb.exe
└── platforms/android-34/android.jar
```

### 2.4 Gradle 8.7

由于腾讯 / 阿里镜像在国内可达性较好，下载 aliyun 镜像：

```powershell
$d = "D:\1\ai-app\app\build-tools"
Invoke-WebRequest -Uri "https://mirrors.aliyun.com/macports/distfiles/gradle/gradle-8.7-bin.zip" -OutFile "$d\gradle-8.7-bin.zip" -UseBasicParsing
Expand-Archive -Path "$d\gradle-8.7-bin.zip" -DestinationPath $d -Force
# 验证
& "$d\gradle-8.7\bin\gradle.bat" --version
```

最终路径：`D:\1\ai-app\app\build-tools\gradle-8.7\bin\gradle.bat`

---

## 3. 编译微信小程序

> 入口脚本：`uni-app/package.json` → `build:mp-weixin`

### 命令

```powershell
$env:Path = "C:\Program Files\nodejs;$env:Path"
Set-Location "D:\1\ai-app\app\uni-app"
npm.cmd run build:mp-weixin
```

### 等价步骤拆解

脚本 `build:mp-weixin` 实际执行：

1. `npm run verify:vue-stack` — 校验 `@vue/*` 版本统一
2. `npm run tailwind:build` — 用 Tailwind CLI 预编译 CSS
3. `uni build -p mp-weixin` — uni-app 编译器输出微信小程序产物
4. `node ./scripts/sanitize-mp-weixin-wxss.mjs` — 清洗 WXSS

### 产物路径

`D:\1\ai-app\app\uni-app\dist\build\mp-weixin\`

关键文件：
- `app.js` / `app.json` / `app.wxss` — 小程序入口三件套
- `pages/` — 各页面 `.wxml` / `.js` / `.json` / `.wxss`
- `components/` — 公共组件
- `common/vendor.js` — 公共 vendor
- `project.config.json` — 微信开发者工具项目配置

### 导入微信开发者工具

1. 打开「微信开发者工具」
2. 选择「导入项目」
3. 项目目录：`D:\1\ai-app\app\uni-app\dist\build\mp-weixin`
4. AppID 选择「测试号」或填入自有 AppID
5. 编译预览

---

## 4. 编译 H5 网页版

> 入口脚本：`uni-app/package.json` → `build:h5`

### 4.1 遇到的问题：terser worker OOM

首次执行 `npm run build:h5` 时报：

```text
[vite:terser] Worker terminated due to reaching memory limit: JS heap out of memory
```

排查：
- 设置 `NODE_OPTIONS=--max-old-space-size=8192` 父进程有效，但 Vite 内部 spawn 出来的 terser worker 不继承该选项。
- 默认 Vite 走 terser，复杂依赖（mermaid / katex / highlight.js）体积大时 worker 进程内存爆炸。

### 4.2 修复：切换到 esbuild minifier

修改 [`uni-app/vite.config.mjs`](file:///D:/1/ai-app/app/uni-app/vite.config.mjs) 在 `defineConfig({...})` 中追加：

```js
build: {
  minify: 'esbuild',
  chunkSizeWarningLimit: 2000,
  terserOptions: undefined,
},
```

> esbuild 仍可输出生产级 minify 后的 JS，但不需要独立 worker 进程，内存友好。

### 4.3 编译命令

由于 PowerShell 脚本策略限制且 worker 需继承大堆内存，分两步：

```powershell
$env:Path = "C:\Program Files\nodejs;$env:Path"
$env:NODE_OPTIONS = "--max-old-space-size=8192"
Set-Location "D:\1\ai-app\app\uni-app"

# 1) 校验
npm.cmd run verify:vue-stack
# 2) Tailwind 预编译
npm.cmd run tailwind:build
# 3) uni-app H5 编译（直接走 node，不通过 npm script）
node --max-old-space-size=8192 ./node_modules/@dcloudio/vite-plugin-uni/bin/uni.js build -p h5
```

> 等价于 `npm run build:h5`，但可以确保使用我们设置的内存参数。

### 4.4 产物

`D:\1\ai-app\app\uni-app\dist\build\h5\`

```
h5/
├── index.html           # 入口，855 B
├── assets/
│   ├── index-*.js       # 业务主包 ~325 KB
│   ├── uni.*.css        # 全局 CSS
│   ├── *.css / *.js     # 页面级 code-split
│   └── mermaid.core.*.js  # mermaid 核心
└── static/images/       # 模型 logo 等
```

### 4.5 本地预览

> 项目自带 [`uni-app/scripts/serve-h5-dist.mjs`](file:///D:/1/ai-app/app/uni-app/scripts/serve-h5-dist.mjs) 用于 e2e（端口 41880），此处用更轻量 Node http server：

```powershell
$h5 = "D:\1\ai-app\app\uni-app\dist\build\h5"
Set-Location $h5

# 写一个 30 行的 server.js（此处省略，直接通过 node -e 即可）
node -e "const http=require('http'),fs=require('fs'),path=require('path');const mime={'.html':'text/html','.js':'application/javascript','.css':'text/css','.json':'application/json','.png':'image/png','.svg':'image/svg+xml'};http.createServer((req,res)=>{let p=req.url.split('?')[0];if(p==='/')p='/index.html';const f=path.join(process.cwd(),decodeURIComponent(p));fs.readFile(f,(e,d)=>{if(e){res.writeHead(404);res.end()}else{res.writeHead(200,{'Content-Type':mime[path.extname(f)]||'application/octet-stream'});res.end(d)}}) }).listen(5180,()=>console.log('Listening on http://localhost:5180'))"
```

浏览器打开 [http://localhost:5180/](http://localhost:5180/)

### 4.6 部署到静态托管

将整个 `dist/build/h5/` 上传至 Nginx / Vercel / Netlify / 阿里云 OSS / 腾讯云 COS。

如果 uni-app `manifest.json` 配置的是 `history` 路由模式，Nginx 需加 SPA fallback：

```nginx
location / {
  try_files $uri $uri/ /index.html;
}
```

---

## 5. 编译 Android APP 并打包 APK

> uni-app CLI 编译 Android 平台只会输出「WebView 壳原生源码」（HTML/JS/CSS），要真正生成 APK 必须用 HBuilderX 云打包 或 本地 Android SDK + Gradle 打包。  
> 本流程采用 **本地构建**（不依赖 GUI 登录）。

### 5.1 uni-app 编译原生源码

```powershell
$env:Path = "C:\Program Files\nodejs;$env:Path"
Set-Location "D:\1\ai-app\app\uni-app"
node ./node_modules/@dcloudio/vite-plugin-uni/bin/uni.js build -p app
```

产物：`D:\1\ai-app\app\uni-app\dist\build\app\`

| 文件 | 含义 |
| --- | --- |
| `__uniappview.html` | Android WebView 入口 HTML |
| `app-service.js` | 业务 JS（运行时按需加载） |
| `app-config.js` / `app-config-service.js` | 应用配置 |
| `app.css` / `app-wxs.js` | 全局样式与 WXS |
| `manifest.json` | uni-app 应用清单 |
| `pages/` / `chat/` / `market/` / `research/` | 各页面静态资源 |

> 这部分内容会被复制到 Android 工程的 `assets/www/`，由原生 WebView 直接加载。

### 5.2 本地构建流程（HBuilderX 不可用时的备选）

思路：自己写一个最小的 Android 工程，WebView 直接加载 `file:///android_asset/www/__uniappview.html`，再用 Gradle 打包。

**为什么走本地**：
- HBuilderX 必须在 GUI 模式登录账号才能「云打包」，CLI / 自动化受限。
- 本地构建可重入、可调试、可在 CI 中运行。

### 5.3 创建 Android 壳工程

#### 目录结构

```
D:\1\ai-app\app\build-tools\android-app\
├── settings.gradle
├── build.gradle
├── gradle.properties
├── local.properties
└── app\
    ├── build.gradle
    └── src\main\
        ├── AndroidManifest.xml
        ├── java\com\webchat\aiapp\MainActivity.java
        ├── res\
        │   ├── values\colors.xml
        │   ├── values\strings.xml
        │   ├── values\styles.xml
        │   ├── mipmap-anydpi-v26\ic_launcher.xml
        │   ├── mipmap-{m,h,xh,xxh,xxxh}dpi\ic_launcher{,_round}.png
        │   └── drawable\ic_launcher_foreground.xml
        └── assets\www\                ← 来自 dist\build\app
            ├── __uniappview.html
            ├── app-service.js
            ├── app-config.js
            ├── manifest.json
            └── …
```

#### [`settings.gradle`](file:///D:/1/ai-app/app/build-tools/android-app/settings.gradle)

```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "WebChatAIApp"
include ':app'
```

#### 根 [`build.gradle`](file:///D:/1/ai-app/app/build-tools/android-app/build.gradle)

```groovy
plugins {
    id 'com.android.application' version '8.4.0' apply false
}
```

#### [`gradle.properties`](file:///D:/1/ai-app/app/build-tools/android-app/gradle.properties)

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

#### [`local.properties`](file:///D:/1/ai-app/app/build-tools/android-app/local.properties)

```properties
sdk.dir=D\:\\1\\ai-app\\app\\build-tools\\android-sdk
```

#### [`app/build.gradle`](file:///D:/1/ai-app/app/build-tools/android-app/app/build.gradle)

```groovy
plugins { id 'com.android.application' }

android {
    namespace 'com.webchat.aiapp'
    compileSdk 34

    defaultConfig {
        applicationId 'com.webchat.aiapp'
        minSdk 21
        targetSdk 34
        versionCode 100
        versionName '1.0.0'
    }
    buildTypes {
        debug   { minifyEnabled false }
        release { minifyEnabled false; signingConfig signingConfigs.debug }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.webkit:webkit:1.9.0'
}
```

#### [AndroidManifest.xml](file:///D:/1/ai-app/app/build-tools/android-app/app/src/main/AndroidManifest.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:label="AI 智能助手"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="true"
        android:allowBackup="true"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|locale|smallestScreenSize|screenLayout|uiMode"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

#### [MainActivity.java](file:///D:/1/ai-app/app/build-tools/android-app/app/src/main/java/com/webchat/aiapp/MainActivity.java)

```java
package com.webchat.aiapp;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setSupportZoom(false);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/www/__uniappview.html");
        setContentView(webView);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
```

#### 资源文件

- [`app/src/main/res/values/colors.xml`](file:///D:/1/ai-app/app/build-tools/android-app/app/src/main/res/values/colors.xml) — `ic_launcher_background = #1E88E5`
- [`app/src/main/res/values/strings.xml`](file:///D:/1/ai-app/app/build-tools/android-app/app/src/main/res/values/strings.xml) — 应用名
- [`app/src/main/res/values/styles.xml`](file:///D:/1/ai-app/app/build-tools/android-app/app/src/main/res/values/styles.xml) — 主题
- [`mipmap-anydpi-v26/ic_launcher.xml`](file:///D:/1/ai-app/app/build-tools/android-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) — 自适应图标（API 26+）
- [`drawable/ic_launcher_foreground.xml`](file:///D:/1/ai-app/app/build-tools/android-app/app/src/main/res/drawable/ic_launcher_foreground.xml) — 前景矢量
- 5 个密度档 PNG（48/72/96/144/192）— 用 PowerShell + System.Drawing 现场生成「AI」文字图标

### 5.4 集成 dist 与构建 APK

#### 1) 复制 uni-app 资源到 `assets/www/`

```powershell
$src = "D:\1\ai-app\app\uni-app\dist\build\app"
$dst = "D:\1\ai-app\app\build-tools\android-app\app\src\main\assets\www"
if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
New-Item -ItemType Directory -Path $dst -Force | Out-Null
Get-ChildItem $src | ForEach-Object { Copy-Item $_.FullName $dst -Recurse -Force }
```

#### 2) 启动图标 PNG 生成（可选 PowerShell 片段）

```powershell
Add-Type -AssemblyName System.Drawing
$sizes = @{ mdpi=48; hdpi=72; xhdpi=96; xxhdpi=144; xxxhdpi=192 }
$res = "D:\1\ai-app\app\build-tools\android-app\app\src\main\res"
foreach ($k in $sizes.Keys) {
  $d = Join-Path $res "mipmap-$k"
  New-Item -ItemType Directory -Path $d -Force | Out-Null
  $bmp = New-Object System.Drawing.Bitmap $sizes[$k], $sizes[$k]
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.Clear([System.Drawing.Color]::FromArgb(255,30,136,229))
  $g.DrawString("AI", (New-Object System.Drawing.Font("Arial", $sizes[$k]*0.45, [System.Drawing.FontStyle]::Bold)),
                 [System.Drawing.Brushes]::White,
                 (New-Object System.Drawing.RectangleF 0, 0, $sizes[$k], $sizes[$k]),
                 (New-Object System.Drawing.StringFormat -Property @{ Alignment="Center"; LineAlignment="Center" }))
  $bmp.Save((Join-Path $d "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Save((Join-Path $d "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)
}
```

#### 3) Gradle 构建

```powershell
$bt = "D:\1\ai-app\app\build-tools"
$jdk = "$bt\jdk-17.0.2"
$sdk = "$bt\android-sdk"
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:Path = "$jdk\bin;$bt\gradle-8.7\bin;$env:Path"

Set-Location "$bt\android-app"
& "$bt\gradle-8.7\bin\gradle.bat" assembleDebug --no-daemon --stacktrace
```

构建日志结尾：

```text
> Task :app:packageDebug
> Task :app:createDebugApkListingFileRedirect
> Task :app:assembleDebug

BUILD SUCCESSFUL in 2m 13s
33 actionable tasks: 33 executed
```

#### 4) 拷贝 APK 到稳定路径

```powershell
Copy-Item "D:\1\ai-app\app\build-tools\android-app\app\build\outputs\apk\debug\app-debug.apk" `
          "D:\1\ai-app\app\build-tools\webchat-ai-app-debug.apk" -Force
```

#### 5) 验证

```powershell
$aapt = "D:\1\ai-app\app\build-tools\android-sdk\build-tools\34.0.0\aapt.exe"
& $aapt dump badging D:\1\ai-app\app\build-tools\webchat-ai-app-debug.apk | Select-Object -First 8
```

输出：

```text
package: name='com.webchat.aiapp' versionCode='100' versionName='1.0.0' ...
sdkVersion:'21'
targetSdkVersion:'34'
uses-permission: name='android.permission.INTERNET'
...
application-label:'AI 智能助手'
```

#### 6) 安装到设备

```powershell
$env:Path = "D:\1\ai-app\app\build-tools\android-sdk\platform-tools;$env:Path"
adb install D:\1\ai-app\app\build-tools\webchat-ai-app-debug.apk
```

> 注：debug APK 用 Android 默认 debug keystore 签名，仅用于调试。要上架应用市场需生成自有 release keystore 并构建 `assembleRelease`。

---

## 6. iOS 打包说明

iOS 打包在 Windows 端**无法直接完成**，必须使用 macOS + Xcode + Apple Developer 账号。流程：

1. **注册 Apple Developer 账号**（个人 $99/年）
   - 入口：https://developer.apple.com/programs/enroll/
2. **在 macOS 上准备证书**
   - App ID（在 Apple Developer 后台创建）
   - iOS Development / Distribution 证书
   - Provisioning Profile（开发 / 发布 / Ad Hoc）
   - 推荐使用 Xcode → Settings → Accounts 自动签名，或使用 `fastlane match`
3. **安装 HBuilderX（macOS 版）** 并登录账号
4. **打开 `uni-app` 项目** → 发行 → 原生 APP-云打包
5. **选择 iOS 平台** → 填入 Bundle ID / 版本号 → 上传证书 / Profile → 提交
6. **下载 ipa** → 用 Xcode → Window → Devices and Simulators 安装到真机

> 没有 Apple 账号时，HBuilderX 也支持「标准基座」运行，但无法发布到 App Store。

---

## 7. 常见问题与修复

### Q1: `npm` 报“无法加载文件…运行脚本已禁用”

**原因**：PowerShell 默认 ExecutionPolicy 不允许运行 `.ps1` 脚本。

**修复**：
- 改用 `npm.cmd`（不是 `npm`）
- 或管理员 PowerShell 临时放宽：`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`

### Q2: `vue-tsc` 找不到

**原因**：`devDependencies` 没装全。

**修复**：`npm install` 重新安装；如果不需要 type-check，跳过 `npm run type-check`。

### Q3: 编译时 Node 内存溢出

**症状**：`FATAL ERROR: Zone Allocation failed - process out of memory`

**原因**：uni-app 编译时 Vite 会 spawn 出多个 worker 进程，复杂依赖（mermaid / katex / highlight.js）体积大。

**修复**：
- 父进程：`NODE_OPTIONS=--max-old-space-size=8192`
- 把 Vite minifier 改成 esbuild（`vite.config.mjs` 加 `build: { minify: 'esbuild' }`）
- 直接用 `node --max-old-space-size=8192 ./node_modules/@dcloudio/vite-plugin-uni/bin/uni.js build -p h5` 而不是 `npm run build:h5`

### Q4: `sdkmanager` 找不到 / 目录结构错误

**症状**：`Warning: Could not determine SDK root`

**修复**：目录必须是 `cmdline-tools/latest/bin/sdkmanager.bat`，不能少 `latest` 这一层。

### Q5: Gradle 报 `Unsupported class file major version 65`

**原因**：用了 JDK 21+ 或过老的 JDK。AGP 8.4 要求 JDK 17。

**修复**：设置 `JAVA_HOME` 指向 JDK 17：

```powershell
$env:JAVA_HOME = "D:\1\ai-app\app\build-tools\jdk-17.0.2"
```

### Q6: Gradle daemon 占内存

**修复**：构建时加 `--no-daemon`，或调小 `org.gradle.jvmargs=-Xmx2048m`。

### Q7: 构建产物缺启动图标

**症状**：APK 编译能过，但设备上不显示图标。

**修复**：检查 `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` 是否齐全；`mipmap-anydpi-v26/ic_launcher.xml` 引用 `@drawable/ic_launcher_foreground` 是否存在。

### Q8: 安装到设备白屏

**排查**：
- `adb logcat | grep -i webview` — 看 WebView JS 错误
- `adb shell` 进入 `data/data/com.webchat.aiapp/files/` 看缓存
- 检查 `assets/www/__uniappview.html` 是否存在，路径大小写是否一致（Linux/Unix 文件系统大小写敏感）

---

## 8. 产物清单

| 端 | 路径 | 大小 |
| --- | --- | --- |
| 微信小程序 | `D:\1\ai-app\app\uni-app\dist\build\mp-weixin\` | 数十 MB（含 vendor） |
| H5 网页版 | `D:\1\ai-app\app\uni-app\dist\build\h5\` | ~10 MB |
| Android 原生源码 | `D:\11\ai-app\app\uni-app\dist\build\app\` | 数 MB |
| Android Debug APK | `D:\1\ai-app\app\build-tools\webchat-ai-app-debug.apk` | 4.54 MB |
| Android 工程 | `D:\1\ai-app\app\build-tools\android-app\` | — |
| iOS | 需 macOS + Xcode + Apple Developer 账号 | — |

---

## 附录 A：每次新终端的初始化脚本

```powershell
# 1. Node + npm（PowerShell 用 npm.cmd 绕过 ps1 限制）
$env:Path = "C:\Program Files\nodejs;$env:Path"

# 2. JDK / Android SDK / Gradle
$bt = "D:\1\ai-app\app\build-tools"
$jdk = "$bt\jdk-17.0.2"
$sdk = "$bt\android-sdk"
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:Path = "$jdk\bin;$bt\gradle-8.7\bin;$sdk\cmdline-tools\latest\bin;$sdk\platform-tools;$env:Path"

# 3. Node 大堆内存（uni-app H5 编译）
$env:NODE_OPTIONS = "--max-old-space-size=8192"

# 4. 验证
node --version
& "$jdk\bin\java.exe" -version
& "$bt\gradle-8.7\bin\gradle.bat" --version
& "$sdk\platform-tools\adb.exe" --version
```

---

## 附录 B：常用命令速查

```powershell
# 微信小程序
Set-Location D:\1\ai-app\app\uni-app
npm.cmd run build:mp-weixin

# H5
node --max-old-space-size=8192 ./node_modules/@dcloudio/vite-plugin-uni/bin/uni.js build -p h5

# Android 原生源码
node ./node_modules/@dcloudio/vite-plugin-uni/bin/uni.js build -p app

# Android APK
Set-Location D:\1\ai-app\app\build-tools\android-app
& D:\1\ai-app\app\build-tools\gradle-8.7\bin\gradle.bat assembleDebug --no-daemon

# 安装到手机
adb install D:\1\ai-app\app\build-tools\webchat-ai-app-debug.apk
```

---

> 文档版本：v1.0  
> 适用编译器：uni-app CLI 4.84 (vue3)  
> 适用 AGP：8.4.0，Gradle：8.7，JDK：17
