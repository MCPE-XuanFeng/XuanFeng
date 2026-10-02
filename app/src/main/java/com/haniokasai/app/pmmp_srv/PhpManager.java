package com.haniokasai.app.pmmp_srv;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for installing PHP on Android.
 *
 * - The bundled builds (assets/phpbin) come from the
 *   MCPE-XuanFeng/Android-AARCH64-PMMP-PHP repository: 14 Android AArch64 PHP
 *   binaries from 5.5.6 up to 8.0.28, all ZTS. They work fully offline.
 * - pmmp/PHP-Binaries (https://github.com/pmmp/PHP-Binaries) ships rolling
 *   "latest" release tags and can supply newer PHP (8.1-8.4) on demand.
 *   Only Android arm64 is published, so those require an arm64 device.
 * - A "custom URL" lets you install a self-built PHP tarball (e.g. produced
 *   with Veha0001/pmmp-droid or pmmp/PHP-Binaries compile.sh).
 *
 * Downloads can optionally go through an HTTP proxy (see AppSettings#proxyEnabled)
 * because GitHub release assets are unreachable from some networks.
 *
 * Extraction is done in Java with Apache Commons Compress, so we no longer
 * depend on a busybox binary (which often fails with "Permission denied" on
 * modern Android because the executable bit cannot be set reliably).
 */
public class PhpManager {

    public static final String TAG = "PhpManager";

    /** Asset sub-directory holding the bundled Android AArch64 PHP builds. */
    public static final String BUNDLED_DIR = "phpbin";

    /**
     * One bundled PHP build shipped inside the APK under {@code assets/phpbin}.
     *
     * Not all of them are 64-bit: the 5.x and early 7.0 builds are ELF32
     * (armeabi-v7a) and therefore only run on devices that still support 32-bit
     * ABIs. {@link #isBinaryCompatibleWithDevice(File)} is the authority on
     * whether the selected build can actually run.
     */
    public static class BundledBuild {
        public final String assetName;
        public final String label;
        public final boolean arm64;

        BundledBuild(String assetName, String label, boolean arm64) {
            this.assetName = assetName;
            this.label = label;
            this.arm64 = arm64;
        }
    }

    /** Every PHP build bundled in the APK, newest first. */
    public static List<BundledBuild> bundledBuilds() {
        List<BundledBuild> l = new ArrayList<>();
        l.add(new BundledBuild("php8.0.28", "PHP 8.0.28 (aarch64)", true));
        l.add(new BundledBuild("php7.3.16", "PHP 7.3.16 (aarch64)", true));
        l.add(new BundledBuild("php7.3.7", "PHP 7.3.7 (aarch64)", true));
        l.add(new BundledBuild("php7.2.8", "PHP 7.2.8 (aarch64)", true));
        l.add(new BundledBuild("php7.2.4", "PHP 7.2.4 (aarch64)", true));
        l.add(new BundledBuild("php7.2", "PHP 7.2 (aarch64)", true));
        l.add(new BundledBuild("php7.0.4", "PHP 7.0.4 (aarch64)", true));
        l.add(new BundledBuild("php7.0.14", "PHP 7.0.14 (arm32)", false));
        l.add(new BundledBuild("php7.0.9", "PHP 7.0.9 (arm32)", false));
        l.add(new BundledBuild("php7.0.2", "PHP 7.0.2 (arm32)", false));
        l.add(new BundledBuild("php7.0.0", "PHP 7.0.0 (arm32)", false));
        l.add(new BundledBuild("php5.6.10", "PHP 5.6.10 (arm32)", false));
        l.add(new BundledBuild("php5.6.2", "PHP 5.6.2 (arm32)", false));
        l.add(new BundledBuild("php5.5.6", "PHP 5.5.6 (arm32)", false));
        return l;
    }

    public static class PhpRelease {
        public final String label;
        public final String pmMajor;
        public final String phpVersion;

        public PhpRelease(String label, String pmMajor, String phpVersion) {
            this.label = label;
            this.pmMajor = pmMajor;
            this.phpVersion = phpVersion;
        }

        /** Builds the download URL for the pmmp/PHP-Binaries Android arm64 build. */
        public String buildUrl() {
            return "https://github.com/pmmp/PHP-Binaries/releases/download/"
                    + "pm" + pmMajor + "-php-" + phpVersion + "-latest/"
                    + "PHP-" + phpVersion + "-Android-arm64-PM" + pmMajor + ".tar.gz";
        }
    }

    public static List<PhpRelease> pmmpReleases() {
        List<PhpRelease> list = new ArrayList<>();
        list.add(new PhpRelease("PHP 8.4 (PM5)", "5", "8.4"));
        list.add(new PhpRelease("PHP 8.3 (PM5)", "5", "8.3"));
        list.add(new PhpRelease("PHP 8.2 (PM5)", "5", "8.2"));
        list.add(new PhpRelease("PHP 8.1 (PM5)", "5", "8.1"));
        list.add(new PhpRelease("PHP 8.0 (PM5)", "5", "8.0"));
        list.add(new PhpRelease("PHP 8.2 (PM4)", "4", "8.2"));
        list.add(new PhpRelease("PHP 8.1 (PM4)", "4", "8.1"));
        list.add(new PhpRelease("PHP 8.0 (PM4)", "4", "8.0"));
        return list;
    }

    /** Human-readable labels for the version spinner, in {@link #bundledBuilds()} order. */
    public static List<String> bundledBuildsLabels() {
        List<String> l = new ArrayList<>();
        for (BundledBuild b : bundledBuilds()) l.add(b.label);
        return l;
    }

    /** Human-readable labels for the pmmp version spinner. */
    public static List<String> pmmpReleasesLabels() {
        List<String> l = new ArrayList<>();
        for (PhpRelease r : pmmpReleases()) l.add(r.label);
        return l;
    }

    public static boolean isArm64() {        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return true;
        }
        return false;
    }

    /** Whether the device can run 32-bit (armeabi-v7a) native code. */
    public static boolean deviceSupports32Bit() {
        for (String abi : Build.SUPPORTED_32_BIT_ABIS) {
            if (abi != null && abi.startsWith("armeabi")) return true;
        }
        return false;
    }

    public static String getAbiSummary() {
        StringBuilder sb = new StringBuilder();
        for (String abi : Build.SUPPORTED_ABIS) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(abi);
        }
        return sb.toString();
    }

    /**
     * Parses the ELF header of a binary to determine its real architecture.
     * Unlike Build.SUPPORTED_ABIS (which describes the DEVICE), this reads the
     * actual file, so it correctly reports a 32-bit php on a 64-bit device.
     *
     * @return "arm64", "arm32", or "unknown".
     */
    public static String getBinaryArch(File f) {
        if (f == null || !f.exists() || f.length() < 20) return "unknown";
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
            byte[] ident = new byte[20];
            int n = raf.read(ident);
            if (n < 20) return "unknown";
            if (ident[0] != 0x7f || ident[1] != 'E' || ident[2] != 'L' || ident[3] != 'F') {
                return "unknown";
            }
            int cls = ident[4] & 0xff;   // 1 = ELFCLASS32, 2 = ELFCLASS64
            boolean be = (ident[5] & 0xff) == 2;
            int machine = be
                    ? (((ident[18] & 0xff) << 8) | (ident[19] & 0xff))
                    : ((ident[18] & 0xff) | ((ident[19] & 0xff) << 8));
            if (cls == 2) {                       // 64-bit
                return (machine == 183) ? "arm64" : "unknown";   // EM_AARCH64
            } else if (cls == 1) {                // 32-bit
                return (machine == 40) ? "arm32" : "unknown";    // EM_ARM
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** True if the binary's architecture can run on this device. */
    public static boolean isBinaryCompatibleWithDevice(File php) {
        String arch = getBinaryArch(php);
        if ("arm64".equals(arch)) {
            return isArm64();
        } else if ("arm32".equals(arch)) {
            return deviceSupports32Bit();
        }
        return true; // unknown -> let the launch attempt surface a clearer error
    }

    public interface InstallListener {
        void onProgress(String message);

        void onSuccess(String versionInfo);

        void onFailure(String error);
    }

    /**
     * Installs one of the bundled PHP builds from assets/phpbin.
     *
     * @param build the build to install (see {@link #bundledBuilds()}).
     */
    public static void installBundled(Context context, BundledBuild build, InstallListener listener) {
        File appDir = new File(context.getApplicationInfo().dataDir);
        try {
            listener.onProgress(context.getString(R.string.php_extracting));
            File php = new File(appDir, "php");
            copyAsset(context, BUNDLED_DIR + "/" + build.assetName, php);
            if (!makeExecutable(php)) {
                throw new Exception("Cannot make PHP executable (permission denied)");
            }
            String ver = testPhp(php);
            if (ver.startsWith("ERROR:")) {
                throw new Exception(ver);
            }
            listener.onSuccess(ver);
        } catch (Exception e) {
            listener.onFailure(e.getMessage());
        }
    }

    /**
     * Downloads a PHP build and installs the php binary.
     *
     * The downloaded artifact may be either a .tar.gz archive (pmmp/PHP-Binaries,
     * custom builds) or a single raw ELF binary (the "custom URL" source can point
     * at a raw binary). We auto-detect by inspecting the gzip magic bytes and
     * handle both transparently, so a single code path serves every source.
     */
    public static void installFromUrl(Context context, String url, InstallListener listener) {
        File appDir = new File(context.getApplicationInfo().dataDir);
        File cache = new File(appDir, "php_download.bin");
        try {
            listener.onProgress(context.getString(R.string.php_downloading, url));
            downloadFile(url, cache);

            File dest = new File(appDir, "php");
            if (isGzipFile(cache)) {
                File extractDir = new File(appDir, "php_extract");
                deleteRecursively(extractDir);
                extractDir.mkdirs();
                listener.onProgress(context.getString(R.string.php_extracting));
                extractTarGz(cache, extractDir);
                File phpBin = findFile(extractDir, "php");
                if (phpBin == null) {
                    throw new Exception("php binary not found in archive");
                }
                copyFile(phpBin, dest);
                deleteRecursively(extractDir);
            } else {
                // Raw binary (e.g. XuanFeng "php"). Copy directly.
                copyFile(cache, dest);
            }

            if (!makeExecutable(dest)) {
                throw new Exception("Cannot make PHP executable (permission denied)");
            }
            cache.delete();
            listener.onProgress(context.getString(R.string.php_installed, dest.getAbsolutePath()));
            String ver = testPhp(dest);
            if (ver.startsWith("ERROR:")) {
                throw new Exception(ver);
            }
            listener.onSuccess(ver);
        } catch (Exception e) {
            listener.onFailure(e.getMessage());
        }
    }

    /** True if the file begins with the gzip magic bytes (0x1f 0x8b). */
    private static boolean isGzipFile(File f) {
        if (f == null || !f.exists() || f.length() < 2) return false;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
            byte[] b = new byte[2];
            if (raf.read(b) != 2) return false;
            return (b[0] & 0xff) == 0x1f && (b[1] & 0xff) == 0x8b;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Runs `php -v` and returns the first line (e.g. "PHP 8.4.0 (cli) ..."),
     * or null if the binary is missing or cannot be executed.
     */
    public static String getPhpVersion(File php) {
        if (php == null || !php.exists()) return null;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{php.getAbsolutePath(), "-v"});
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            p.waitFor();
            return line;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tests whether the php binary can actually be launched. Returns the
     * version line on success, or an "ERROR: ..." string describing the
     * failure (including architecture mismatches) on failure.
     */
    public static String testPhp(File php) {
        if (php == null || !php.exists()) return "ERROR: php binary missing at expected path";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{php.getAbsolutePath(), "-v"});
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            p.waitFor();
            if (line != null && !line.isEmpty()) return line;
            return "ERROR: php -v produced no output (exit=" + p.exitValue() + ")";
        } catch (Exception e) {
            String arch = getBinaryArch(php);
            String msg = "ERROR: " + e.getMessage();
            if (!isBinaryCompatibleWithDevice(php)) {
                msg += " | binary arch=" + arch + " is incompatible with device "
                        + getAbiSummary() + " (likely a 32-bit PHP on a 64-bit-only device)";
            }
            return msg;
        }
    }

    /**
     * Extract a .tar.gz archive into the destination directory using
     * Apache Commons Compress (no external busybox required).
     */
    public static void extractTarGz(File archive, File destDir) throws Exception {
        try (InputStream fi = new java.io.FileInputStream(archive);
             BufferedInputStream bi = new BufferedInputStream(fi);
             GzipCompressorInputStream gzi = new GzipCompressorInputStream(bi)) {
            extractTarStream(gzi, destDir);
        }
    }

    /** Core tar extraction from an already-open tar stream. */
    private static void extractTarStream(InputStream tarStream, File destDir) throws Exception {
        try (TarArchiveInputStream tis = new TarArchiveInputStream(tarStream)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                File out = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = tis.read(buf)) >= 0) {
                        os.write(buf, 0, n);
                    }
                }
                int mode = entry.getMode();
                // Preserve executable bit from the archive if present.
                if ((mode & 0100) != 0) {
                    makeExecutable(out);
                }
            }
        }
    }

    /**
     * Installs PHP from a file the user picked on the device (SAF / OpenDocument).
     * Accepts either a raw php binary or a .tar / .tar.gz / .tgz archive that
     * contains a php binary.
     */
    public static void installLocalFile(Context context, Uri uri, InstallListener listener) {
        File appDir = new File(context.getApplicationInfo().dataDir);
        File dest = new File(appDir, "php");
        try {
            String name = localName(context, uri);
            boolean isArchive = name != null
                    && (name.endsWith(".tar.gz") || name.endsWith(".tgz") || name.endsWith(".tar"));
            listener.onProgress(context.getString(R.string.php_extracting));

            if (isArchive) {
                File extractDir = new File(appDir, "php_extract");
                deleteRecursively(extractDir);
                extractDir.mkdirs();
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new Exception("cannot open selected file");
                    InputStream tar;
                    if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
                        tar = new GzipCompressorInputStream(new BufferedInputStream(in));
                    } else {
                        tar = new BufferedInputStream(in);
                    }
                    extractTarStream(tar, extractDir);
                }
                File phpBin = findFile(extractDir, "php");
                if (phpBin == null) {
                    throw new Exception("php binary not found in archive");
                }
                copyFile(phpBin, dest);
                deleteRecursively(extractDir);
            } else {
                try (InputStream in = context.getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(dest)) {
                    if (in == null) throw new Exception("cannot open selected file");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                }
            }

            if (!makeExecutable(dest)) {
                throw new Exception("Cannot make PHP executable (permission denied)");
            }
            listener.onProgress(context.getString(R.string.php_installed, dest.getAbsolutePath()));
            String ver = testPhp(dest);
            if (ver.startsWith("ERROR:")) {
                throw new Exception(ver);
            }
            listener.onSuccess(ver);
        } catch (Exception e) {
            listener.onFailure(e.getMessage());
        }
    }

    private static String localName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = c.getString(idx);
                }
            } catch (Exception ignored) {
            }
        }
        if (result == null) result = uri.getLastPathSegment();
        return result;
    }

    /**
     * Mark a file executable in the most reliable way on Android.
     *
     * The preferred method is the libcore syscall {@code Os.chmod(path, 0755)},
     * which talks to the kernel directly and is not subject to SELinux rules
     * that may block an app from exec'ing {@code /system/bin/chmod}. We then
     * verify with canExecute() and fall back to the chmod binary if needed.
     *
     * @return true if the file ends up executable.
     */
    public static boolean makeExecutable(File f) {
        if (f == null || !f.exists()) return false;

        // Best-effort Java API.
        try { f.setExecutable(true, false); } catch (Exception ignored) {}

        // Most reliable: direct syscall via libcore.
        try {
            Os.chmod(f.getAbsolutePath(), 0755);
        } catch (ErrnoException ignored) {
            // fall through to chmod binary
        } catch (Throwable ignored) {
            // Os may be unavailable on some unusual runtimes
        }
        if (f.canExecute()) return true;

        // Fallback: platform chmod binary.
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"/system/bin/chmod", "755", f.getAbsolutePath()});
            p.waitFor();
        } catch (Exception ignored) {
        }
        if (f.canExecute()) return true;

        // Last resort: generic PATH chmod (some devices have it under /system/xbin).
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"chmod", "755", f.getAbsolutePath()});
            p.waitFor();
        } catch (Exception ignored) {
        }
        return f.canExecute();
    }

    /** chmod 0755 on a directory (search/execute bit needed to exec files inside). */
    public static void makeExecutableDir(File dir) {
        if (dir == null) return;
        try { Os.chmod(dir.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
        try {
            Runtime.getRuntime().exec(
                    new String[]{"/system/bin/chmod", "755", dir.getAbsolutePath()}).waitFor();
        } catch (Exception ignored) {
        }
    }

    /**
     * Resolves the path from which php can actually be launched.
     *
     * Some devices (and some OEM ROMs) refuse to exec a binary placed in the
     * app-private directory even when the +x bit is set, failing with
     * "error=13, Permission denied". We ensure the binary (and its parent
     * directory) is executable, then test it and log every probe result to the
     * console so failures are diagnosable. If app storage cannot exec, we try
     * the files/ subdirectory and then /data/local/tmp. The path that actually
     * runs is returned; if none works, the app-private path is returned so the
     * launch still surfaces the real error.
     */
    public static String prepareExecutable(Context context) {
        File appPhp = new File(context.getApplicationInfo().dataDir, "php");
        if (!appPhp.exists()) return null;

        makeExecutable(appPhp);
        makeExecutableDir(appPhp.getParentFile());

        String r1 = testPhp(appPhp);
        ConsoleActivity.log("[PE Server] php probe (app dir): "
                + (r1.startsWith("ERROR:") ? r1 : "OK - " + r1));
        if (!r1.startsWith("ERROR:")) {
            return appPhp.getAbsolutePath();
        }

        // Fallback 1: files/ subdirectory (fresh path context, still app-private).
        try {
            File filesPhp = new File(context.getFilesDir(), "php");
            copyFile(appPhp, filesPhp);
            makeExecutable(filesPhp);
            makeExecutableDir(filesPhp.getParentFile());
            String r2 = testPhp(filesPhp);
            ConsoleActivity.log("[PE Server] php probe (files dir): "
                    + (r2.startsWith("ERROR:") ? r2 : "OK - " + r2));
            if (!r2.startsWith("ERROR:")) {
                return filesPhp.getAbsolutePath();
            }
        } catch (Exception ignored) {
        }

        // Fallback 2: /data/local/tmp (world-writable + exec-capable on many devices).
        try {
            File fallback = new File("/data/local/tmp", "pmmp_php");
            File parent = fallback.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            copyFile(appPhp, fallback);
            makeExecutable(fallback);
            String r3 = testPhp(fallback);
            ConsoleActivity.log("[PE Server] php probe (/data/local/tmp): "
                    + (r3.startsWith("ERROR:") ? r3 : "OK - " + r3));
            if (!r3.startsWith("ERROR:")) {
                return fallback.getAbsolutePath();
            }
        } catch (Exception ignored) {
            // /data/local/tmp not writable on this device; keep app path
        }
        return appPhp.getAbsolutePath();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private static void downloadFile(String url, File saveTo) throws Exception {
        if (saveTo.exists()) saveTo.delete();
        URLConnection connection = openConnection(url);
        connection.connect();
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             OutputStream output = new FileOutputStream(saveTo)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
        }
    }

    /**
     * Opens a URL, optionally routed through the user-configured HTTP proxy.
     *
     * GitHub release assets live on objects.githubusercontent.com, which is
     * unreachable from some networks (notably mainland China without a proxy).
     * When the proxy is enabled we use {@link java.net.Proxy} so plain
     * {@code HttpURLConnection} honours it without any extra dependency.
     */
    private static URLConnection openConnection(String url) throws Exception {
        URL u = new URL(url);
        if (AppSettings.proxyEnabled()) {
            int port = AppSettings.proxyPort();
            if (port > 0 && port <= 65535) {
                java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                        new java.net.InetSocketAddress(AppSettings.proxyHost(), port));
                return u.openConnection(proxy);
            }
        }
        return u.openConnection();
    }

    private static File findFile(File root, String name) {
        if (root == null || !root.isDirectory()) return null;
        File[] files = root.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File found = findFile(f, name);
                if (found != null) return found;
            } else if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    /** Path of the bundled busybox once extracted into the app data dir. */
    public static File getBusyboxPath(Context context) {
        return new File(context.getApplicationInfo().dataDir, "busybox");
    }

    /**
     * Extracts the bundled busybox into the app data dir and makes it executable.
     *
     * Only used by the PTY console mode: {@code busybox script} is what hands the
     * server process a real terminal, which MengFang's CommandReader requires
     * before it will read stdin (see AppSettings#ptyConsole).
     *
     * @return the executable, or null when it could not be installed.
     */
    public static File ensureBusybox(Context context) {
        File dst = getBusyboxPath(context);
        try {
            if (!dst.exists() || dst.length() == 0) {
                copyAsset(context, "busybox", dst);
            }
            if (!makeExecutable(dst)) {
                return null;
            }
            return dst;
        } catch (Exception e) {
            return null;
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }
    }

    private static void copyAsset(Context context, String asset, File dst) throws Exception {
        if (dst.exists()) dst.delete();
        try (InputStream in = context.getAssets().open(asset);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }
    }

    private static void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteRecursively(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
