# XuanFeng

> 在您的 Android 手机上运行 **PocketMine-MP**（Minecraft Bedrock 版）服务器。

XuanFeng 是一款 Material Design 3 Android 应用，让您可以在设备上安装 PHP 运行环境、部署 PocketMine-MP 服务器，并通过内置控制台进行管理 —— 一切尽在掌中。

当前版本：**1.0.7.4**（versionCode 1074）

---

## ✨ 功能特性

### PHP 运行环境

- **14 个 PHP 版本内置离线可用** —— PHP **5.5.6 → 8.0.28** 全部打进 APK（`assets/phpbin/`），无需联网、无需下载。均来自 [MCPE-XuanFeng/Android-AARCH64-PMMP-PHP](https://github.com/MCPE-XuanFeng/Android-AARCH64-PMMP-PHP)，全部为 **ZTS** 构建。
- **按设备架构区分** —— 内置清单混合了 aarch64 与 arm32 构建，安装器会**按你选中的那个版本**判断能否在当前设备运行，而不是一刀切禁用（详见下方「内置 PHP 版本」）。
- **从 pmmp/PHP-Binaries 在线安装** —— 可取更新的 PHP **8.1 / 8.2 / 8.3 / 8.4**（PM5 / ZTS，仅 arm64）。
- **自定义 URL** 或 **从设备存储选择** `php` 可执行文件 / `.tar.gz` 包（自动识别 gzip 与裸 ELF 两种格式）。
- **下载代理** —— 可在安装器中启用 HTTP 代理（默认 `127.0.0.1:7897`）。GitHub Release 资产托管在 `objects.githubusercontent.com`，部分网络下不可达。

### 服务器与网络

- **后台运行** —— 服务器进程由前台服务（`ServerService`）持有，**切到后台或锁屏后继续运行**，常驻通知可随时查看状态。
- **frp 内网穿透** —— 内置 frp 客户端（默认 `0.71.0`），可把 MCPE 服务器（udp 19132）经由你自己的 frps 服务器暴露到公网。
- **服务器管理** —— 启动 / 停止 / 强制结束，切换 ANSI 颜色，调整日志字体大小。
- **实时控制台** —— 实时日志面板 + 命令输入框（例如 `/stop`）。

### 界面

- **主题与语言** —— 浅色 / 深色 / 自定义强调色主题（5 种强调色）；界面支持英文和中文。
- **毛玻璃背景** —— 内置横屏与竖屏两张默认壁纸，或从设备任意选择图片。
- **屏幕方向可选** —— 横屏 / 竖屏 / 跟随系统，四个页面统一生效。
- **控制台可交互** —— 通过 `busybox script` 分配 **PTY**，让 PHP 拿到真正的终端（否则 MengFang 的 `CommandReader` 因 `stream_isatty()` 为假而完全不读 stdin，控制台会静默吞掉所有命令）。
- **滚动行为可选** —— 「新内容时回滚到顶部」开关（关闭时保持你正在看的位置）。
- **可在 Android 16 上运行**（已在 API 36 设备验证），最低支持 Android 5.0（API 21）。
- **targetSdk 28（借鉴 Termux）** —— Android 10+ 的 SELinux W^X 限制禁止 targetSdk≥29 的应用执行自身目录下的二进制；targetSdk 28 可继续执行，因此「下载 / 本机文件 / 内置」三种 PHP 来源都能用。

---

## 📸 截图

<!-- TODO -->

---

## 📋 系统要求

| 组件 | 版本 |
| --- | --- |
| Android | 5.0+（minSdk 21），targetSdk 28（Termux 方式），已在 Android 16 设备上验证运行 |
| 架构 | arm64 设备可运行全部内置版本；arm32 设备仅能运行标注 `arm32` 的版本 |
| JDK | **21**（Eclipse Adoptium JDK 21）—— JDK 25 与 Kotlin Gradle 插件不兼容 |
| Gradle | 8.13（包含 wrapper） |
| Android Gradle Plugin | 8.11.1 |
| Android SDK | platform-36，build-tools 36.x |

> ⚠️ 跑 **MengFang / PocketMine-MP** 建议用 **PHP 8.x**。内置清单最高为 8.0.28；若需 8.1+ 请用 pmmp 源安装 **PHP 8.2 (PM5)**（ZTS，含 pmmpthread / leveldb 等扩展）。
>
> 内置版本均为**非 PIE** 构建。

---

## 🛠 从源代码构建

```bash
# 使用 JDK 21（必需）
export JAVA_HOME="/path/to/jdk-21"
cd BlueLightAPP-master

# 调试构建
./gradlew assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`（约 **88 MB**，因内置 14 个 PHP 二进制）

Windows 下另有三个脚本：

| 脚本 | 作用 |
| --- | --- |
| `build.cmd` | 自动定位 JDK 21 → 编译 → 复制 APK 到 `apks\`（带时间戳 + `XuanFeng-latest.apk`） |
| `upload.cmd` | 一键提交并推送到 GitHub（含 `--follow-tags`） |
| `release.cmd` | 从 `app/build.gradle` 读取 versionName，自动打 tag 并推送以触发 Release |

> **构建卡在 `AccessDeniedException`？** 这是残留 Gradle daemon 锁定 `app/build` 中间产物的句柄所致，先执行 `./gradlew --stop` 再重试即可。

---

## 🚀 快速上手

1. **授予存储权限**。Android 11+ 需要「所有文件访问」（`MANAGE_EXTERNAL_STORAGE`）才能写入 `/storage/emulated/0/PocketMine`；应用会引导你前往系统设置开启。
2. 打开应用 → 右上角菜单 → **安装 PHP**。
3. 选择来源：
   - **内置版本（离线）** —— 选择版本（5.5.6 – 8.0.28）→ 安装。**完全离线，推荐首选。**
   - **pmmp/PHP-Binaries** —— 选择 8.x 版本 → 从网络安装（仅 arm64）。
   - **自定义 URL** —— 粘贴 `.tar.gz` 直链或裸二进制直链。
   - **本地文件** —— 从设备存储中选择 `php` 可执行文件或包含 `php` 的 `.tar.gz`。
4. 返回主界面，点击 **启动服务器**。
5. 需要公网访问时：菜单 → **frp 穿透**，填入你的 frps 地址 / 端口 / token，启用后随服务器一并启动。

### 内置 PHP 版本

清单按版本从新到旧排列，每项标注架构：

| 架构 | 版本 |
| --- | --- |
| **aarch64**（ELF64） | 8.0.28、7.3.16、7.3.7、7.2.8、7.2.4、7.2、7.0.4 |
| **arm32**（ELF32） | 7.0.14、7.0.9、7.0.2、7.0.0、5.6.10、5.6.2、5.5.6 |

arm32 版本在**只支持 64 位 ABI** 的设备上无法运行 —— 选中这类版本时安装按钮会禁用并给出提示。

### PHP 下载格式（pmmp/PHP-Binaries）

```
https://github.com/pmmp/PHP-Binaries/releases/download/
  pm{PM}-php-{ver}-latest/PHP-{ver}-Android-arm64-PM{PM}.tar.gz
```

压缩包通过 **Apache Commons Compress** 解压（无需外部 `busybox` 依赖）。
`php` 二进制文件通过 `Os.chmod(path, 0755)` 设为可执行，并回退到 `/system/bin/chmod` 与 `File.setExecutable`。

---

## 📁 项目结构

```
app/src/main/
├── java/com/haniokasai/app/pmmp_srv/
│   ├── App.java                 # Application 子类，发布全局 Context
│   ├── MainActivity.java        # 主界面，设置对话框，服务器控制
│   ├── ConsoleActivity.java     # 实时日志与命令输入
│   ├── InstallPhpActivity.java  # PHP 安装器（来源 / 版本 / 代理）
│   ├── FrpActivity.java         # frp 穿透配置界面
│   ├── PhpManager.java          # PHP 安装 / 解压 / 架构判定 / 代理下载
│   ├── FrpManager.java          # frpc 下载 / 配置生成 / 启停监控
│   ├── ServerService.java       # 前台服务，持有服务器与隧道生命周期
│   ├── ServerUtils.java         # 数据目录，服务器进程与控制台
│   ├── AppSettings.java         # 语言 / 主题 / 强调色 / 背景 / 屏幕方向 / 代理
│   └── UiUtils.java             # 边缘到边缘内边距，毛玻璃背景
├── assets/
│   ├── busybox                  # PTY 支持（script applet）
│   └── phpbin/                  # 14 个内置 PHP 二进制（约 176 MB）
└── res/
    ├── layout/                  # activity_main / console / install_php / frp / dialog_settings
    ├── values/, values-zh/      # 双语字符串资源
    ├── values/, values-night/   # 主题 / 颜色
    └── drawable/                # bg_glass、default_bg.jpg、default_bg_portrait.jpg
        drawable-night/          # 仅 bg_glass（夜间毛玻璃）
```

---

## 🔨 自动构建

`.github/workflows/release.yml`：

| 触发条件 | 行为 |
| --- | --- |
| 推送 tag `v*` | 编译并发布 **Release**，APK 作为附件 |
| 推送 `main` | 仅编译，上传 artifact |
| 手动触发 | 可填 tag 名决定是否发 Release |

工作流使用 `actions/checkout@v5` + `setup-java@v5`（JDK 21）并显式安装 SDK 组件 —— 未使用 `android-actions/setup-android`，因其被迫升级到 Node 24 后会崩溃。

---

## ⚠️ 已知限制

- 真实背景模糊效果需要 **Android 12+**（`RenderEffect.createBlurEffect`）；旧版本会降级为半透明着色。
- 内置 PHP **最高仅 8.0.28**。MengFang 实测运行在 PHP 8.2 ZTS 上，用 8.0 能否兼容需实测；不兼容请改用 pmmp 源装 8.2。
- APK 体积 **约 88 MB**（14 个 PHP 二进制约 176 MB，deflate 后 79 MB），主因即内置 PHP。
- 部分 OEM ROM 即使 targetSdk 28 也会禁止执行应用目录二进制（同 Termux 的兼容性问题），届时请使用其它设备。
- frpc 为 linux/arm64 静态二进制，**仅 arm64 设备可用**；且需要你自备一台公网 frps 服务器。
- `busybox script` 分配的 PTY 若在某些 ROM 上不可用，控制台输入会失效（此时可关闭「PTY 控制台」开关回退到管道模式，但 MengFang 侧会因 `stream_isatty()` 为假而不读 stdin）。

---

## 📄 许可证

> GPL-3.0

> 基于 BlueLight-PMMP 二次开发
