package com.haniokasai.app.pmmp_srv;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A dedicated, full-screen page for viewing a text file (config, log, source…).
 *
 * Kept separate from the file manager list so long files get their own
 * scrollable, selectable surface and a Copy / Share action, instead of being
 * crammed into a dialog.
 */
public class TextEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PATH = "com.haniokasai.app.pmmp_srv.path";

    /** Cap the loaded text to keep the TextView responsive on big files. */
    private static final int MAX_CHARS = 200000;

    private String filePath = null;
    private String loadedText = "";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppSettings.nightMode(this));
        AppSettings.applyTheme(this);
        AppSettings.applyOrientation(this);
        super.onCreate(savedInstanceState);

        filePath = getIntent() != null ? getIntent().getStringExtra(EXTRA_PATH) : null;
        if (filePath == null || !new File(filePath).isFile()) {
            Toast.makeText(this, R.string.text_editor_read_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_text_editor);
        UiUtils.setupEdgeToEdge(this, findViewById(R.id.root));
        UiUtils.applyGlassBackdrop(this, (ImageView) findViewById(R.id.glassBackdrop));

        Toolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(new File(filePath).getName());
        }

        TextView content = findViewById(R.id.text_content);
        TextView info = findViewById(R.id.text_info);

        File file = new File(filePath);
        long size = file.length();
        loadedText = readText(file);

        content.setText(loadedText);
        String truncated = loadedText.length() >= MAX_CHARS ? " · …" : "";
        info.setText(getString(R.string.text_editor_info,
                Formatter.formatFileSize(this, size), file.getAbsolutePath()) + truncated);
    }

    /** Reads the file as UTF-8, bounded to {@link #MAX_CHARS}. */
    private String readText(File file) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int read;
                int total = 0;
                while ((read = reader.read(buf)) != -1) {
                    sb.append(buf, 0, read);
                    total += read;
                    if (total >= MAX_CHARS) {
                        sb.append("\n…");
                        break;
                    }
                }
            }
            if (sb.length() == 0) {
                return getString(R.string.text_editor_too_large,
                        Formatter.formatFileSize(this, file.length()));
            }
            return sb.toString();
        } catch (OutOfMemoryError oom) {
            return getString(R.string.text_editor_too_large,
                    Formatter.formatFileSize(this, file.length()));
        } catch (Exception e) {
            return getString(R.string.text_editor_read_error, e.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor_menu, menu);
        UiUtils.tintMenuIcons(this, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.menu_copy) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(new File(filePath).getName(), loadedText));
                Toast.makeText(this, R.string.text_editor_copied, Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.menu_share) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, new File(filePath).getName());
            share.putExtra(Intent.EXTRA_TEXT, loadedText);
            startActivity(Intent.createChooser(share, getString(R.string.text_editor_share)));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
