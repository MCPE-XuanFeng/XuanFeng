# ****占位****

> 在您的 Android 手机上运行 **PocketMine-MP**（Minecraft Bedrock 版）服务器。

 ****占位**** 是一款 Material Design 3 Android 应用，让您可以在设备上安装 PHP 运行环境、部署 PocketMine-MP 服务器，并通过内置控制台进行管理 —— 一切尽在掌中。

---

## ✨ 功能特性

- **多种 PHP 来源** —— 从 [pmmp/PHP-Binaries](https://github.com/pmmp/PHP-Binaries) 安装 PHP 8.x，使用内置 PHP 7.x，从自定义 URL 下载，或从设备存储中选择 `php` 可执行文件 / `.tar.gz` 包。
- **服务器管理** —— 启动 / 停止服务器，切换 ANSI 颜色，调整日志字体大小。
- **实时控制台** —— 实时日志面板 + 命令输入框（例如 `/stop`）。
- **主题与语言** —— 浅色 / 深色 / 自定义强调色主题（5 种强调色）；界面支持英文和中文。
- **可自定义背景** —— 毛玻璃效果背景，默认图片或从设备任意选择图片。
- **横屏优化 UI** —— 专为宽屏设计的双栏布局，并正确处理状态栏 / 导航栏内边距。
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
| JDK | **21**（Eclipse Adoptium JDK 21）—— JDK 25 与 Kotlin Gradle 插件不兼容 |
| Gradle | 8.13（包含 wrapper） |
| Android Gradle Plugin | 8.11.1 |
| Android SDK | platform-36，build-tools 36.x |

> ⚠️ 内置的 PHP 7.x 是 32 位版本。在 arm64 设备上可能无法启动 —— 请改用 **PHP 8.x（arm64）**。

---

## 🛠 从源代码构建

```bash
# 使用 JDK 21（必需）
export JAVA_HOME="/path/to/jdk-21"
cd BlueLightAPP-master

# 调试构建（若依赖已缓存，可离线执行）
./gradlew assembleDebug --offline
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

---

## 🚀 快速上手

1. **首次启动时，授予“存储”权限（WRITE_EXTERNAL_STORAGE）**。
   （targetSdk 28 使用传统存储：授予后即可直接使用 `/storage/emulated/0/PocketMine`；未授予则回退到应用私有目录 —— 不会崩溃，但并非预期路径。）
2. 打开应用 → 右上角菜单 → **安装 PHP**。
3. 选择来源：
   - **pmmp/PHP-Binaries** —— 选择版本 → 安装（仅 arm64）。
   - **内置二进制包** —— 使用自带的 PHP 7.x。
   - **自定义 URL** —— 粘贴 `.tar.gz` 直链。
   - **本地文件** —— 从设备存储中选择 `php` 可执行文件或包含 `php` 的 `.tar.gz`。
4. 返回主界面，点击 **启动服务器**。

### PHP 下载格式（pmmp/PHP-Binaries）

```
pm{PM}-php-{ver}-latest/PHP-{ver}-Android-arm64-PM{PM}.tar.gz
```

压缩包通过 **Apache Commons Compress** 解压（无需外部 `busybox` 依赖）。
`php` 二进制文件通过 `chmod 755` 设置为可执行（若失败则回退到 `File.setExecutable`）。

---

## 📁 项目结构

```
src/main/
├── java/com/haniokasai/app/pmmp_srv/
│   ├── MainActivity.java
│   ├── ConsoleActivity.java
│   ├── InstallPhpActivity.java
│   ├── PhpManager.java        # PHP 安装 / 解压 / 权限处理
│   ├── ServerUtils.java       # 数据目录，服务器生命周期
│   ├── AppSettings.java       # 语言 / 主题 / 强调色 / 背景
│   └── UiUtils.java           # 边缘到边缘内边距，毛玻璃背景
└── res/
    ├── layout/                 # activity_main / console / install_php / dialog_settings
    ├── values/, values-zh/     # 双语字符串资源
    ├── values/, values-night/  # 主题 / 颜色
    └── drawable[-night]/       # bg_glass / default_bg.jpg
```

---

## ⚠️ 已知限制

- 真实背景模糊效果需要 **Android 12+**（`RenderEffect.createBlurEffect`）；旧版本会降级为半透明着色。
- 内置 PHP 7.x 为旧版 arm64 构建（非 PIE）；arm64 设备可用，建议使用功能更全的 PHP 8.x。
- 需授予“存储”权限（`WRITE_EXTERNAL_STORAGE`）才能使用 `/storage/emulated/0/PocketMine`。
- 部分 OEM ROM 即使 targetSdk 28 也会禁止执行应用目录二进制（同 Termux 的兼容性问题），届时请使用其它设备。

---

## 📄 许可证

> GPL-3.0


>基于BlueLight-PMMP二次开发