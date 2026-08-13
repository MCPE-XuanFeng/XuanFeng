package com.haniokasai.app.pmmp_srv;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ServerUtils {
    public static Context mContext;

    private static Process serverProcess;
    private static OutputStream stdin;
    private static InputStream stdout;

    public static void setContext(Context mContext) {
        ServerUtils.mContext = mContext;
    }

    /** App-private directory; PHP binary lives here. */
    public static String getAppDirectory() {
        return mContext.getApplicationInfo().dataDir;
    }

    /**
     * Server working directory.
     *
     * By default we use /storage/emulated/0/PocketMine so users can manage their
     * server files directly from a file manager. Because targetSdk is 28 (legacy
     * storage), a granted WRITE_EXTERNAL_STORAGE is enough — no
     * MANAGE_EXTERNAL_STORAGE needed. If the permission is missing we fall back
     * to the app-private external-files directory so the app does not crash.
     */
    public static String getDataDirectory() {
        File dir;
        if (canWriteSharedStorage()) {
            dir = new File(Environment.getExternalStorageDirectory(), "PocketMine");
        } else {
            dir = new File(mContext.getExternalFilesDir(null), "PocketMine");
        }
        dir.mkdirs();
        return dir.getPath();
    }

    /** Legacy storage (targetSdk<30): WRITE_EXTERNAL_STORAGE grants /storage/emulated/0 access. */
    private static boolean canWriteSharedStorage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return mContext.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public static String getPhpBinaryPath() {
        return getAppDirectory() + "/php";
    }

    public static boolean isPhpInstalled() {
        return new File(getPhpBinaryPath()).exists();
    }

    public static void killServer() {
        try {
            if (serverProcess != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    serverProcess.destroyForcibly();
                } else {
                    serverProcess.destroy();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        // Fallback in case the direct handle is unavailable.
        try {
            Runtime.getRuntime().exec("killall -9 php").waitFor();
        } catch (Exception e) {
            // ignore
        }
    }

    public static Boolean isRunning() {
        try {
            serverProcess.exitValue();
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    final public static void runServer() {
        File f = new File(getDataDirectory(), "/tmp");
        if (!f.exists()) {
            f.mkdir();
        } else if (!f.isDirectory()) {
            f.delete();
            f.mkdir();
        }
        setPermission();

        String execPath = PhpManager.prepareExecutable(mContext);
        if (execPath == null) {
            ConsoleActivity.log("[PE Server] php binary missing. Install it via Install PHP.");
            MainActivity.stopNotifyService();
            return;
        }
        ConsoleActivity.log("[PE Server] Using php: " + execPath);

        String file;

        if (new File(getDataDirectory() + "/PocketMine-MP.phar").exists()) {
            file = "/PocketMine-MP.phar";
        } else {
            file = "/src/pocketmine/PocketMine.php";
        }

        File ini = new File(getDataDirectory() + "/php.ini");
        if (!ini.exists()) {
            try {
                ini.createNewFile();
                FileOutputStream os = new FileOutputStream(ini);
                os.write(("phar.readonly=0\nphar.require_hash=1\ndate.timezone=Asia/Shanghai\n"
                        + "short_open_tag=0\nasp_tags=0\nopcache.enable=1\nopcache.enable_cli=1\n"
                        + "opcache.save_comments=1\nopcache.fast_shutdown=0\nopcache.max_accelerated_files=4096\n"
                        + "opcache.interned_strings_buffer=8\nopcache.memory_consumption=128\n"
                        + "opcache.optimization_level=0xffffffff").getBytes("UTF8"));
                os.close();
            } catch (Exception e) {
                // ignore
            }
        }
        String[] args = new String[]{
                execPath,
                "-c",
                getDataDirectory() + "/php.ini",
                getDataDirectory() + file,
                MainActivity.ansiMode ? "--enable-ansi" : "--disable-ansi"
        };

        ProcessBuilder builder = new ProcessBuilder(args);
        builder.redirectErrorStream(true);
        File cwd = new File(getDataDirectory());
        if (!cwd.isDirectory()) cwd = new File(getAppDirectory());
        builder.directory(cwd);
        Map<String, String> env = builder.environment();
        env.put("TMPDIR", getDataDirectory() + "/tmp");

        serverProcess = null;
        if (!launchAndMonitor(builder) && !cwd.getAbsolutePath().equals(getAppDirectory())) {
            // Some devices refuse chdir into external storage (EACCES) even with
            // MANAGE_EXTERNAL_STORAGE. All paths in args are absolute, so the
            // server still finds php.ini and the phar; only its data dir changes.
            ConsoleActivity.log("[PE Server] Launch failed from " + cwd.getAbsolutePath()
                    + "; retrying with cwd=" + getAppDirectory());
            builder.directory(new File(getAppDirectory()));
            launchAndMonitor(builder);
        }

        if (serverProcess == null) {
            File php = new File(getPhpBinaryPath());
            ConsoleActivity.log("[PE Server] Unable to start PHP.");
            if (php.exists()) {
                String arch = PhpManager.getBinaryArch(php);
                ConsoleActivity.log("[PE Server] php: size=" + php.length()
                        + " canExecute=" + php.canExecute()
                        + " arch=" + arch
                        + " device=" + PhpManager.getAbiSummary());
                if (!php.canExecute()) {
                    ConsoleActivity.log("[PE Server] php binary is NOT executable. Try reinstalling PHP via the in-app installer (Settings > Install PHP).");
                } else if (!PhpManager.isBinaryCompatibleWithDevice(php)) {
                    ConsoleActivity.log("[PE Server] php binary architecture (" + arch
                            + ") is incompatible with this device (" + PhpManager.getAbiSummary()
                            + "). Install a matching build: on an arm64 device use PHP 8.x (arm64) from Install PHP, not the 32-bit bundled PHP 7.x.");
                } else {
                    ConsoleActivity.log("[PE Server] php is executable and arch-compatible but failed to launch. See the IOException(s) above for the exact reason (errno / SELinux / noexec). The binary may be corrupted/incomplete, or the device blocks execution from app storage — a fresh arm64 build via Install PHP often helps.");
                }
            } else {
                ConsoleActivity.log("[PE Server] php binary missing at " + getPhpBinaryPath());
            }
            MainActivity.stopNotifyService();
            killServer();
        }
        return;
    }

    /** Starts the process and wires up the console log monitor. Returns false on failure. */
    private static boolean launchAndMonitor(ProcessBuilder builder) {
        try {
            serverProcess = builder.start();
        } catch (Exception e) {
            ConsoleActivity.log("[PE Server] Unable to start PHP: " + e.getMessage());
            return false;
        }
        stdout = serverProcess.getInputStream();
        stdin = serverProcess.getOutputStream();
        Thread tMonitor = new Thread() {
            public void run() {
                InputStreamReader reader = new InputStreamReader(stdout, Charset.forName("UTF-8"));
                BufferedReader br = new BufferedReader(reader);
                while (isRunning()) {
                    try {
                        char[] buffer = new char[8192];
                        int size = 0;
                        while ((size = br.read(buffer, 0, buffer.length)) != -1) {
                            StringBuilder s = new StringBuilder();
                            for (int i = 0; i < size; i++) {
                                char c = buffer[i];
                                if (c == '\r') {
                                    continue;
                                }
                                if (c == '\n' || c == '\u0007') {
                                    String line = s.toString();
                                    if (c == '\u0007' || line.startsWith("\u001B]0;")) {
                                        // Do nothing.
                                    } else {
                                        ConsoleActivity.log(line);
                                    }
                                    s = new StringBuilder();
                                } else {
                                    s.append(buffer[i]);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            br.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                ConsoleActivity.log("[PE Server] Server was stopped.");
                MainActivity.stopNotifyService();
            }
        };
        tMonitor.start();
        return true;
    }

    final static public void setPermission() {
        try {
            PhpManager.makeExecutable(new File(getAppDirectory() + "/php"));
        } catch (Exception e) {
            // ignore
        }
    }

    public static void writeCommand(String Cmd) {
        try {
            stdin.write((Cmd + "\r\n").getBytes());
            stdin.flush();
        } catch (Exception e) {
            // ignore
        }
    }

    public static String getBackupDirectory() {
        File dir;
        if (canWriteSharedStorage()) {
            dir = new File(Environment.getExternalStorageDirectory(), "PocketMine-Backups");
        } else {
            dir = new File(mContext.getExternalFilesDir(null), "PocketMine-Backups");
        }
        dir.mkdirs();
        return dir.getPath();
    }

    public static Boolean RemoveSrvDirectory() {
        File dir = new File(getDataDirectory());
        try {
            FileUtils.forceDelete(dir);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean BackupDir() {
        File file = new File(getDataDirectory());
        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
        File baseFile = new File(getBackupDirectory() + "/" + sdf.format(c.getTime()) + ".zip");
        ZipOutputStream outZip = null;
        try {
            outZip = new ZipOutputStream(new FileOutputStream(baseFile));
            archive(outZip, baseFile, file);
        } catch (Exception e) {
            return false;
        } finally {
            if (outZip != null) {
                try {
                    outZip.closeEntry();
                } catch (Exception e) {
                }
                try {
                    outZip.flush();
                } catch (Exception e) {
                }
                try {
                    outZip.close();
                } catch (Exception e) {
                }
            }
        }
        return true;
    }

    private static void archive(ZipOutputStream outZip, File baseFile, File targetFile) {
        if (targetFile.isDirectory()) {
            File[] files = targetFile.listFiles();
            for (File f : files) {
                if (f.isDirectory()) {
                    archive(outZip, baseFile, f);
                } else {
                    if (!f.getAbsoluteFile().equals(baseFile)) {
                        archive(outZip, baseFile, f, f.getAbsolutePath().replace(baseFile.getParent(), "").substring(1));
                    }
                }
            }
        }
    }

    private static boolean archive(ZipOutputStream outZip, File baseFile, File targetFile, String entryName) {
        outZip.setLevel(5);
        try {
            outZip.putNextEntry(new ZipEntry(entryName));
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(targetFile));
            int readSize = 0;
            byte buffer[] = new byte[1024];
            while ((readSize = in.read(buffer, 0, buffer.length)) != -1) {
                outZip.write(buffer, 0, readSize);
            }
            in.close();
            outZip.closeEntry();
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
