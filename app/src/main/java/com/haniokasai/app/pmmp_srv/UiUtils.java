package com.haniokasai.app.pmmp_srv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.InputStream;

/**
 * Small UI helpers shared by the activities.
 *
 * setupEdgeToEdge() turns on edge-to-edge drawing and then reserves space for the
 * system status bar (top) and navigation bar (bottom) by applying their insets as
 * padding to the given root view. This keeps the toolbar / content from being hidden
 * behind the system bars on any device (including notched / rounded-corner ones).
 *
 * applyGlassBackdrop() loads the user-selected background image (or the built-in
 * default) into the full-screen ImageView and frosts it with the native
 * RenderEffect blur (Android 12 / API 31+). On older devices the image is shown
 * without blur and the translucent glass surfaces still read as tinted glass.
 */
public final class UiUtils {

    private UiUtils() {
    }

    public static void setupEdgeToEdge(AppCompatActivity activity, View root) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        // Pick light (dark icons) vs dark (light icons) status/nav bar glyphs
        // from the THEME PREFERENCE (not the system night mode): in the light
        // and gray themes the bars sit on a bright backdrop, so icons must be
        // dark/black; only the dark theme gets white icons.
        boolean isNight = "dark".equals(AppSettings.theme(activity));
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!isNight);
        controller.setAppearanceLightNavigationBars(!isNight);

        // Reserve space for the system bars on ALL four sides. In landscape the
        // navigation bar usually sits on the side, so we must pad left/right too,
        // otherwise content (toolbar, buttons) ends up hidden behind it.
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /**
     * Backdrop policy (saves GPU/CPU):
     *  - No custom wallpaper selected: a SOLID color taken from the current
     *    theme's android:colorBackground — so a light theme gets a white
     *    backdrop, a dark theme a black one, and the gray theme a gray one.
     *    No image decoding, no blur — the cheapest possible option.
     *  - Custom wallpaper selected: show the image as-is, WITHOUT blur
     *    (RenderEffect blur on every frame is expensive on low-end devices).
     */
    /**
     * Tints every menu icon with the theme's colorOnSurface.
     *
     * The bundled toolbar icons (console, copy, clear) are plain white PNGs,
     * which are invisible on the light/gray themes' bright toolbar. This makes
     * them black in light/gray themes and white in the dark theme.
     */
    public static void tintMenuIcons(AppCompatActivity activity, android.view.Menu menu) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (!activity.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface, tv, true)) {
            return;
        }
        int color = tv.data;
        for (int i = 0; i < menu.size(); i++) {
            android.view.MenuItem item = menu.getItem(i);
            if (item.getIcon() != null) {
                item.getIcon().mutate();
                item.getIcon().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }
    }

    /**
     * Decodes the wallpaper down to roughly the screen size.
     *
     * Full-resolution phone photos (12+ MP) cost ~48 MB of RAM each and force
     * the GPU to scale them every frame; downsampling keeps the backdrop
     * crisp while using a fraction of the memory.
     */
    private static Bitmap decodeSampled(android.app.Activity activity, InputStream is) {
        android.graphics.BitmapFactory.Options bounds =
                new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeStream(is, null, bounds);
        int maxSide = Math.max(
                activity.getResources().getDisplayMetrics().widthPixels,
                activity.getResources().getDisplayMetrics().heightPixels);
        int sample = 1;
        int larger = Math.max(bounds.outWidth, bounds.outHeight);
        while (larger / (sample * 2) > maxSide) {
            sample *= 2;
        }
        android.graphics.BitmapFactory.Options opts =
                new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = sample;
        return android.graphics.BitmapFactory.decodeStream(is, null, opts);
    }

    public static void applyGlassBackdrop(AppCompatActivity activity, ImageView backdrop) {
        if (backdrop == null) {
            return;
        }
        String uriStr = AppSettings.backgroundUri(activity);
        boolean loaded = false;
        if (uriStr != null && !uriStr.isEmpty()) {
            try {
                Uri uri = Uri.parse(uriStr);
                try (InputStream is = activity.getContentResolver().openInputStream(uri)) {
                    Bitmap bitmap = decodeSampled(activity, is);
                    if (bitmap != null) {
                        backdrop.setImageBitmap(bitmap);
                        loaded = true;
                    }
                }
            } catch (Exception e) {
                // fall back to theme color below
            }
        }
        if (!loaded) {
            backdrop.setImageDrawable(null);
            // Follow the active theme (light → white, dark → black, gray → gray).
            android.util.TypedValue tv = new android.util.TypedValue();
            if (activity.getTheme().resolveAttribute(android.R.attr.colorBackground, tv, true)) {
                backdrop.setBackgroundColor(tv.data);
            } else {
                backdrop.setBackgroundColor(android.graphics.Color.BLACK);
            }
        }
    }
}
