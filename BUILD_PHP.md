# Building / obtaining PHP for BlueLight-PMMP (Android)

This app runs a PocketMine-MP server, which is written in **PHP**, on Android.
The app no longer bundles a single fixed binary — it can install PHP at runtime
through the in-app **Install PHP** screen. This document explains where the PHP
binaries come from and how to build your own (especially PHP 7.x, which is no
longer published prebuilt by pmmp).

## 1. PHP 8.x — download prebuilt (recommended, automatic)

Source: **https://github.com/pmmp/PHP-Binaries**

pmmp publishes rolling "latest" release tags. The in-app installer downloads the
**Android arm64** build for you. The URL pattern is:

```
https://github.com/pmmp/PHP-Binaries/releases/download/pm{PM}-php-{PHPVER}-latest/PHP-{PHPVER}-Android-arm64-PM{PM}.tar.gz
```

Examples that the installer can fetch:

| PHP     | PocketMine | URL |
|---------|-----------|-----|
| 8.4     | PM5       | `…/pm5-php-8.4-latest/PHP-8.4-Android-arm64-PM5.tar.gz` |
| 8.3     | PM5       | `…/pm5-php-8.3-latest/PHP-8.3-Android-arm64-PM5.tar.gz` |
| 8.2     | PM5       | `…/pm5-php-8.2-latest/PHP-8.2-Android-arm64-PM5.tar.gz` |
| 8.1     | PM5       | `…/pm5-php-8.1-latest/PHP-8.1-Android-arm64-PM5.tar.gz` |
| 8.0     | PM5       | `…/pm5-php-8.0-latest/PHP-8.0-Android-arm64-PM5.tar.gz` |
| 8.2     | PM4       | `…/pm4-php-8.2-latest/PHP-8.2-Android-arm64-PM4.tar.gz` |
| 8.1     | PM4       | `…/pm4-php-8.1-latest/PHP-8.1-Android-arm64-PM4.tar.gz` |
| 8.0     | PM4       | `…/pm4-php-8.0-latest/PHP-8.0-Android-arm64-PM4.tar.gz` |

> **Note:** pmmp only ships **Android arm64** (`arm64-v8a`) binaries. A device
> that is not arm64 cannot use the downloaded PHP 8.x — use the bundled PHP 7.x
> binary instead (see below).

## 2. PHP 7.x — bundled binary or self-built

pmmp stopped publishing PHP 7.x prebuilt binaries. You have two options:

### Option A — Bundled legacy binary (easiest)
The repository still ships the original `app/src/main/assets/php` (a PHP 7.x ARM
build). In the **Install PHP** screen, choose **"Bundled binary (PHP 7.x)"** and
tap Install. This is also what the app auto-installs on first launch.

### Option B — Build PHP 7.x yourself, then install via "Custom URL"

Use the **pmmp/PHP-Binaries** build script (Linux/macOS host required) to
cross-compile PHP 7.x for Android aarch64:

```bash
# clone the build scripts (use the stable branch)
git clone https://github.com/pmmp/PHP-Binaries.git
cd PHP-Binaries

# build PHP 7.4 for Android aarch64 (PocketMine-MP 3 used PHP 7.x -> -P3)
# requires the aarch64-linux-musl toolchain:
#   https://github.com/pmmp/musl-cross-make
./compile.sh -t android-aarch64 -x -j4 -P3 -z 7.4
```

The resulting tarball is extracted the same way as the prebuilt ones; host it
somewhere reachable from the device (or copy it to the device and serve it
locally) and paste the URL into the **"Custom URL (self-built)"** option in the
Install PHP screen.

Alternatively, **https://github.com/Veha0001/pmmp-droid** provides a
`pchan.sh` installer and GitHub Actions workflows that build PM-PHP binaries for
Android (Termux). The script resolves the exact
`PHP-{ver}-Android-arm64-PM{x}.tar.gz` asset name from the pmmp update API, so
you can adapt it / its CI to produce a PHP 7.x build and host the artifact for
the Custom URL flow.

```bash
# Veha0001/pmmp-droid one-liner (Termux on an arm64 Android device):
bash -c "$(curl -fsSL https://raw.githubusercontent.com/Veha0001/pmmp-droid/main/pchan.sh)"
```

## 3. How the app installs PHP

`PhpManager` (app/src/main/java/.../PhpManager.java) does the work:

1. Ensures `busybox` is present in the app's private directory (extracted from
   `assets/busybox`).
2. Downloads the chosen `.tar.gz` (pmmp rolling tag, bundled asset, or custom
   URL).
3. Extracts it with `busybox tar -xzf`.
4. Locates the `php` executable, copies it to the app's private dir as `php`,
   and `chmod +x`.
5. The server (`ServerUtils.runServer`) then launches `php -c <php.ini> <phar>`.

Server data lives in the app-specific external directory
`Android/data/com.haniokasai.app.pmmp_srv/files/PocketMine` (no storage
permission required, works on Android 16 scoped storage).
