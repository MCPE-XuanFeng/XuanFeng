package com.haniokasai.app.pmmp_srv;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.Locale;

/**
 * Central place for user-facing preferences:
 *  - Language  : "" (system) | "zh" | "en"
 *  - Theme     : "light" | "dark" | "custom"
 *  - Accent    : "blue" | "green" | "purple" | "amber" | "teal"  (used when theme == custom)
 *
 * Each Activity applies these in onCreate() (theme) and attachBaseContext() (locale).
 */
public final class AppSettings {

    public static final String PREF = "config";
    public static final String KEY_LANG = "lang";
    public static final String KEY_THEME = "theme";
    public static final String KEY_ACCENT = "accent";
    public static final String KEY_BG_IMAGE = "bg_image_uri";
    public static final String KEY_PTY_CONSOLE = "pty_console";
    public static final String KEY_CONSOLE_AUTOSCROLL = "console_autoscroll";
    public static final String KEY_ORIENTATION = "orientation";

    // frp tunnel (expose the MCPE server through a public frp server).
    public static final String KEY_FRP_ENABLED = "frp_enabled";
    public static final String KEY_FRP_SERVER_ADDR = "frp_server_addr";
    public static final String KEY_FRP_SERVER_PORT = "frp_server_port";
    public static final String KEY_FRP_TOKEN = "frp_token";
    public static final String KEY_FRP_LOCAL_PORT = "frp_local_port";
    public static final String KEY_FRP_REMOTE_PORT = "frp_remote_port";
    public static final String KEY_FRP_VERSION = "frp_version";

    // Download proxy (GitHub release assets are unreachable without one in some networks).
    public static final String KEY_PROXY_ENABLED = "proxy_enabled";
    public static final String KEY_PROXY_HOST = "proxy_host";
    public static final String KEY_PROXY_PORT = "proxy_port";

    private AppSettings() {
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String lang(Context c) {
        return prefs(c).getString(KEY_LANG, "");
    }

    public static String theme(Context c) {
        String v = prefs(c).getString(KEY_THEME, "gray");
        if ("dark".equals(v) || "light".equals(v) || "deepblue".equals(v)) return v;
        return "gray"; // "gray" is the default; old "custom" falls back to gray
    }

    public static String accent(Context c) {
        String v = prefs(c).getString(KEY_ACCENT, "");
        return v == null ? "" : v;
    }

    /** Custom background image URI; empty means use the built-in default. */
    public static String backgroundUri(Context c) {
        return prefs(c).getString(KEY_BG_IMAGE, "");
    }

    public static void setBackgroundUri(Context c, String uri) {
        prefs(c).edit().putString(KEY_BG_IMAGE, uri).apply();
    }

    public static void clearBackgroundUri(Context c) {
        prefs(c).edit().remove(KEY_BG_IMAGE).apply();
    }

    /**
     * Launch the server through "busybox script" so PHP gets a real PTY.
     *
     * Android's ProcessBuilder hands the child a pipe, never a terminal, and
     * MengFang's CommandReader only starts reading stdin when stream_isatty()
     * is true - so with a plain pipe the console silently swallows every
     * command. Default on; turn it off to go back to the plain pipe launch.
     */
    public static boolean ptyConsole(Context c) {
        return prefs(c).getBoolean(KEY_PTY_CONSOLE, true);
    }

    public static void setPtyConsole(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_PTY_CONSOLE, enabled).apply();
    }

    /**
     * Whether new console output should scroll the log to the newest line.
     *
     * The log TextView is selectable, so appending text makes it reset its
     * selection/scroll anchor (that is what dragged the view to the top);
     * when this is off we actively restore the previous scroll position
     * instead of just doing nothing.
     */
    public static boolean consoleAutoScroll(Context c) {
        return prefs(c).getBoolean(KEY_CONSOLE_AUTOSCROLL, true);
    }

    public static void setConsoleAutoScroll(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_CONSOLE_AUTOSCROLL, enabled).apply();
    }

    // ---- screen orientation --------------------------------------------

    /**
     * Preferred screen orientation: {@code "auto"} (default, follow the sensor),
     * {@code "portrait"} or {@code "landscape"} (force that direction).
     *
     * "auto" lets the app rotate freely with the device, so both portrait and
     * landscape work without forcing the user into one; the two explicit values
     * lock the app when the sensor behaviour is unwanted.
     */
    public static String orientation(Context c) {
        String v = prefs(c).getString(KEY_ORIENTATION, "auto");
        if ("portrait".equals(v) || "auto".equals(v) || "landscape".equals(v)) return v;
        return "auto";
    }

    public static void setOrientation(Context c, String value) {
        prefs(c).edit().putString(KEY_ORIENTATION, value).apply();
    }

    /**
     * Resolves the stored orientation preference to an ActivityInfo constant.
     *
     * This is what actually controls the screen direction; the manifest does
     * not hardcode one, so applyOrientation() is the single source of truth.
     * "auto" follows the sensor (free rotation), the other two lock the app.
     */
    public static int orientationConstant(Context c) {
        switch (orientation(c)) {
            case "portrait":
                return android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case "landscape":
                return android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case "auto":
            default:
                return android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
        }
    }

    /**
     * Applies the stored orientation to an Activity.
     *
     * Must be called after super.onCreate(): setRequestedOrientation() during
     * Activity construction is unreliable, and the manifest's hardcoded
     * android:screenOrientation="landscape" is only the initial value - this
     * call is what actually honours the user preference.
     */
    public static void applyOrientation(android.app.Activity a) {
        a.setRequestedOrientation(orientationConstant(a));
    }

    // ---- frp tunnel ----------------------------------------------------

    public static boolean frpEnabled(Context c) {
        return prefs(c).getBoolean(KEY_FRP_ENABLED, false);
    }

    public static void setFrpEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_FRP_ENABLED, enabled).apply();
    }

    public static String frpServerAddr(Context c) {
        return prefs(c).getString(KEY_FRP_SERVER_ADDR, "");
    }

    public static void setFrpServerAddr(Context c, String addr) {
        prefs(c).edit().putString(KEY_FRP_SERVER_ADDR, addr).apply();
    }

    public static int frpServerPort(Context c) {
        return prefs(c).getInt(KEY_FRP_SERVER_PORT, 7000);
    }

    public static void setFrpServerPort(Context c, int port) {
        prefs(c).edit().putInt(KEY_FRP_SERVER_PORT, port).apply();
    }

    public static String frpToken(Context c) {
        return prefs(c).getString(KEY_FRP_TOKEN, "");
    }

    public static void setFrpToken(Context c, String token) {
        prefs(c).edit().putString(KEY_FRP_TOKEN, token).apply();
    }

    public static int frpLocalPort(Context c) {
        return prefs(c).getInt(KEY_FRP_LOCAL_PORT, 19132);
    }

    public static void setFrpLocalPort(Context c, int port) {
        prefs(c).edit().putInt(KEY_FRP_LOCAL_PORT, port).apply();
    }

    public static int frpRemotePort(Context c) {
        return prefs(c).getInt(KEY_FRP_REMOTE_PORT, 19132);
    }

    public static void setFrpRemotePort(Context c, int port) {
        prefs(c).edit().putInt(KEY_FRP_REMOTE_PORT, port).apply();
    }

    public static String frpVersion(Context c) {
        return prefs(c).getString(KEY_FRP_VERSION, FrpManager.DEFAULT_VERSION);
    }

    public static void setFrpVersion(Context c, String version) {
        prefs(c).edit().putString(KEY_FRP_VERSION, version).apply();
    }

    // ---- download proxy -------------------------------------------------

    /**
     * Whether downloads (PHP builds, frpc) should go through an HTTP proxy.
     *
     * GitHub release assets are served from objects.githubusercontent.com, which
     * is unreachable from some networks; enabling this routes them through the
     * user's local proxy instead.
     */
    public static boolean proxyEnabled() {
        return prefs(AppHolder.ctx).getBoolean(KEY_PROXY_ENABLED, false);
    }

    public static void setProxyEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_PROXY_ENABLED, enabled).apply();
    }

    public static String proxyHost() {
        return prefs(AppHolder.ctx).getString(KEY_PROXY_HOST, "127.0.0.1");
    }

    public static void setProxyHost(Context c, String host) {
        prefs(c).edit().putString(KEY_PROXY_HOST, host).apply();
    }

    public static int proxyPort() {
        return prefs(AppHolder.ctx).getInt(KEY_PROXY_PORT, 7897);
    }

    public static void setProxyPort(Context c, int port) {
        prefs(c).edit().putInt(KEY_PROXY_PORT, port).apply();
    }

    // ---- built-in features (toggleable in Settings) ----------------------
    public static final String KEY_FILE_MANAGER_ENABLED = "file_manager_enabled";
    public static final String KEY_SYSINFO_ENABLED = "sysinfo_enabled";

    // ---- server type & advanced launch ---------------------------------
    /** Active server software: "pocketmine" (PHP) or "nukkit" (Java). */
    public static final String KEY_SERVER_TYPE = "server_type";
    /** Path to a Java runtime executable used to launch Nukkit ("" = auto). */
    public static final String KEY_JAVA_PATH = "java_path";
    /** When on and a custom launch script exists, it replaces the built-in launch. */
    public static final String KEY_CUSTOM_SCRIPT_ENABLED = "custom_script_enabled";

    /**
     * Active server software.
     *
     * "pocketmine" runs the PHP-based PocketMine-MP (PocketMine-MP.phar /
     * src tree); "nukkit" runs the Java-based Nukkit (nukkit.jar) through a Java
     * runtime. Defaults to pocketmine so existing installs keep working.
     */
    public static String serverType(Context c) {
        String v = prefs(c).getString(KEY_SERVER_TYPE, "pocketmine");
        return "nukkit".equals(v) ? "nukkit" : "pocketmine";
    }

    public static void setServerType(Context c, String v) {
        prefs(c).edit().putString(KEY_SERVER_TYPE, "nukkit".equals(v) ? "nukkit" : "pocketmine").apply();
    }

    /** Path of the Java runtime used for Nukkit; empty means auto-detect. */
    public static String javaPath(Context c) {
        return prefs(c).getString(KEY_JAVA_PATH, "");
    }

    public static void setJavaPath(Context c, String p) {
        prefs(c).edit().putString(KEY_JAVA_PATH, p == null ? "" : p).apply();
    }

    /** Whether the user-supplied startup script overrides the built-in launch. */
    public static boolean customScriptEnabled(Context c) {
        return prefs(c).getBoolean(KEY_CUSTOM_SCRIPT_ENABLED, false);
    }

    public static void setCustomScriptEnabled(Context c, boolean e) {
        prefs(c).edit().putBoolean(KEY_CUSTOM_SCRIPT_ENABLED, e).apply();
    }

    /** Whether the in-app file manager (browse /storage/emulated/0/PocketMine) is available. */
    public static boolean fileManagerEnabled(Context c) {
        return prefs(c).getBoolean(KEY_FILE_MANAGER_ENABLED, true);
    }

    public static void setFileManagerEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_FILE_MANAGER_ENABLED, enabled).apply();
    }

    /** Whether the console shows live CPU / memory usage info. */
    public static boolean sysInfoEnabled(Context c) {
        return prefs(c).getBoolean(KEY_SYSINFO_ENABLED, true);
    }

    public static void setSysInfoEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_SYSINFO_ENABLED, enabled).apply();
    }

    /**
     * A process-wide application context, so Context-free getters (used by the
     * download helpers on background threads) can still reach the prefs.
     */
    public static final class AppHolder {
        public static Context ctx;

        private AppHolder() {
        }
    }

    /** Night mode to force for the current theme preference. */
    public static int nightMode(Context c) {
        switch (theme(c)) {
            case "dark":
                return AppCompatDelegate.MODE_NIGHT_YES;
            case "light":
                return AppCompatDelegate.MODE_NIGHT_NO;
            case "gray":
                return AppCompatDelegate.MODE_NIGHT_YES; // deep gray is dark
            case "deepblue":
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_NO;
        }
    }

    /** Theme resource id to apply in onCreate(), before setContentView. */
    public static int themeRes(Context c) {
        switch (theme(c)) {
            case "gray":
                return R.style.Theme_BlueLight_Gray;
            case "deepblue":
                return R.style.Theme_BlueLight_DeepBlue;
            default: // light + dark share the DayNight theme (night mode picks)
                return R.style.Theme_BlueLight;
        }
    }

    /**
     * Accent-color overlay style for the stored accent preference.
     *
     * The accent is applied as a ThemeOverlay via Theme.applyStyle() AFTER
     * setTheme(), so the user can change the accent independently of the
     * theme (previously the accent only took effect with the now-removed
     * "custom" theme, which is why it seemed impossible to set).
     */
    public static int accentOverlayRes(Context c) {
        switch (accent(c)) {
            case "green":  return R.style.OverlayAccent_Green;
            case "purple": return R.style.OverlayAccent_Purple;
            case "amber":  return R.style.OverlayAccent_Amber;
            case "teal":   return R.style.OverlayAccent_Teal;
            case "blue":   return 0; // blue == the original theme default, no overlay
            default:       return 0; // theme default
        }
    }

    /** setTheme() + accent overlay, shared by every activity. */
    public static void applyTheme(android.app.Activity a) {
        a.setTheme(themeRes(a));
        int overlay = accentOverlayRes(a);
        if (overlay != 0) {
            a.getTheme().applyStyle(overlay, true);
        }
    }

    /** Wraps a base context so that resources resolve in the chosen language. */
    public static Context wrap(Context base) {
        String l = lang(base);
        if (l == null || l.isEmpty()) {
            return base;
        }
        Locale locale = "zh".equals(l) ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        } else {
            config.locale = locale;
        }
        return base.createConfigurationContext(config);
    }
}
