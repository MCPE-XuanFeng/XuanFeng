package com.haniokasai.app.pmmp_srv;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
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
        // based on the current night mode so they stay legible over the glass.
        int night = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = (night == Configuration.UI_MODE_NIGHT_YES);
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

    /** Load the chosen background into the backdrop ImageView and frost it on API 31+. */
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
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap != null) {
                        backdrop.setImageBitmap(bitmap);
                        loaded = true;
                    }
                }
            } catch (Exception e) {
                // fall back to default below
            }
        }
        if (!loaded) {
            backdrop.setImageResource(R.drawable.default_bg);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdrop.setRenderEffect(
                    RenderEffect.createBlurEffect(26f, 26f, Shader.TileMode.CLAMP));
        }
    }
}
