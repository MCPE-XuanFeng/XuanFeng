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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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

    /**
     * Whether the app can read/write the shared external-storage root
     * (/storage/emulated/0/PocketMine).
     * - API < 23: always allowed.
     * - API 23..29: WRITE_EXTERNAL_STORAGE (legacy storage, targetSdk 28).
     * - API >= 30: WRITE_EXTERNAL_STORAGE is scoped and cannot reach an
     *   arbitrary /PocketMine folder, so MANAGE_EXTERNAL_STORAGE is required.
     */
    private static boolean canWriteSharedStorage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
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

    public static boolean isRunning() {
        if (serverProcess == null) return false;
        try {
            serverProcess.exitValue();
            return false; // exitValue() succeeded => the process has exited
        } catch (IllegalThreadStateException e) {
            return true; // exitValue() threw => the process is still alive
        }
    }

    final public static void runServer() {
        // Guard against double-start. If a server is already alive we must not
        // spawn a second process, otherwise the first one is orphaned and
        // stdin ends up pointing at the wrong (dying) process, which makes the
        // console appear to accept commands that never reach the server.
        // stopNotifyService()/killServer() reset the process handle.
        if (isRunning()) {
            ConsoleActivity.log("[PE Server] Server is already running; ignoring duplicate start request.");
            return;
        }
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
        // ---- command line --------------------------------------------------
        // Android hands the child process a PIPE, never a terminal, and
        // MengFang's CommandReader only starts reading stdin when
        // stream_isatty() is true - so over a plain pipe the console silently
        // swallows every command. Wrapping the launch in "busybox script"
        // gives PHP a real PTY and the console works again.
        //   * --disable-readline stops readline from installing its own
        //     "Genisys> " prompt, which would pollute this app's console view.
        //   * stty -echo keeps the PTY from echoing our own writes back at us,
        //     and -onlcr avoids doubling every newline.
        String iniPath = getDataDirectory() + "/php.ini";
        String serverPath = getDataDirectory() + file;
        String ansiArg = MainActivity.ansiMode ? "--enable-ansi" : "--disable-ansi";

        List<String> args = new ArrayList<>();
        File busybox = null;
        boolean pty = false;
        if (AppSettings.ptyConsole(mContext)) {
            busybox = PhpManager.ensureBusybox(mContext);
            if (busybox == null) {
                ConsoleActivity.log("[PE Server] PTY console: busybox is unavailable - using pipe mode"
                        + " (typed commands will NOT reach the server).");
            } else if (ptySelfTest(busybox)) {
                pty = true;
            } else {
                ConsoleActivity.log("[PE Server] PTY console: self-test failed - using pipe mode"
                        + " (typed commands will NOT reach the server). This device may forbid PTYs"
                        + " for apps, or this busybox may not support 'script -c'.");
            }
        }

        if (pty) {
            String inner = "stty -echo -onlcr 2>/dev/null; exec "
                    + shellQuote(execPath) + " -c " + shellQuote(iniPath)
                    + " " + shellQuote(serverPath) + " " + ansiArg + " --disable-readline";
            args.add(busybox.getAbsolutePath());
            args.add("script");
            args.add("-q");
            args.add("-c");
            args.add(inner);
            args.add("/dev/null");
            ConsoleActivity.log("[PE Server] PTY console: ON - console input should now work.");
        } else {
            args.add(execPath);
            args.add("-c");
            args.add(iniPath);
            args.add(serverPath);
            args.add(ansiArg);
        }

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

    /**
     * Verifies that "busybox script" can hand a child a PTY on this device and
     * that this busybox build understands "-c COMMAND".
     *
     * This is what makes the PTY mode safe to ship blind: if either is missing
     * we fall back to the plain pipe launch instead of starting a server whose
     * console would look alive but silently discard every command.
     *
     * Runs in well under a second; hard-capped at 4s so it can never hang.
     */
    private static boolean ptySelfTest(File busybox) {
        Process p = null;
        try {
            p = new ProcessBuilder(busybox.getAbsolutePath(), "script", "-q", "-c",
                    "echo PTY_OK", "/dev/null")
                    .redirectErrorStream(true)
                    .start();
            InputStream in = p.getInputStream();
            StringBuilder sb = new StringBuilder();
            long deadline = System.currentTimeMillis() + 4000L;
            byte[] buf = new byte[256];
            while (System.currentTimeMillis() < deadline) {
                int avail = in.available();
                if (avail > 0) {
                    int n = in.read(buf, 0, Math.min(avail, buf.length));
                    if (n > 0) {
                        sb.append(new String(buf, 0, n, "UTF-8"));
                    }
                    if (sb.indexOf("PTY_OK") >= 0) {
                        break;
                    }
                } else if (!isAlive(p)) {
                    break;
                }
                Thread.sleep(50L);
            }
            String out = sb.toString().replace('\r', ' ').replace('\n', ' ').trim();
            ConsoleActivity.log("[PE Server] pty self-test says: " + (out.isEmpty() ? "(no output)" : out));
            return out.contains("PTY_OK");
        } catch (Exception e) {
            ConsoleActivity.log("[PE Server] pty self-test error: " + e);
            return false;
        } finally {
            if (p != null) {
                try {
                    p.destroyForcibly();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    private static boolean isAlive(Process p) {
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    /** Single-quotes a path for the shell that busybox script spawns. */
    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
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
        if (serverProcess == null || stdin == null) {
            ConsoleActivity.log("[Console] Cannot send command: server is not running. Start the server first.");
            return;
        }
        if (!isRunning()) {
            ConsoleActivity.log("[Console] Cannot send command: server process has already exited. Check the log for a crash.");
            return;
        }
        try {
            stdin.write((Cmd + "\r\n").getBytes());
            stdin.flush();
        } catch (Exception e) {
            ConsoleActivity.log("[Console] Failed to send command to server: " + e.getMessage());
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
