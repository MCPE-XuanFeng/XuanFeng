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
