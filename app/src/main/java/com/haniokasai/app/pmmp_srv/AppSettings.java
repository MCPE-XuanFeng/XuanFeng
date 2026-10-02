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
        return prefs(c).getString(KEY_THEME, "light");
    }

    public static String accent(Context c) {
        return prefs(c).getString(KEY_ACCENT, "blue");
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
            default: // custom follows the system
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    /** Theme resource id to apply in onCreate(), before setContentView. */
    public static int themeRes(Context c) {
        if (!"custom".equals(theme(c))) {
            return R.style.Theme_BlueLight;
        }
        switch (accent(c)) {
            case "green":
                return R.style.Theme_BlueLight_Custom_Green;
            case "purple":
                return R.style.Theme_BlueLight_Custom_Purple;
            case "amber":
                return R.style.Theme_BlueLight_Custom_Amber;
            case "teal":
                return R.style.Theme_BlueLight_Custom_Teal;
            default:
                return R.style.Theme_BlueLight_Custom_Blue;
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
