package com.haniokasai.app.pmmp_srv;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ConsoleActivity extends AppCompatActivity {
    final static int CLEAR_CODE = 143;
    final static int COPY_CODE = CLEAR_CODE + 1;

    public static ConsoleActivity instance = null;
    public static ScrollView scroll_log;
    public static SpannableStringBuilder currentLog = new SpannableStringBuilder();
    public static MaterialButton button_command = null;
    public static TextView label_log = null;
    public static TextInputEditText edit_command = null;
    public static float font_size = 16.0f;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppSettings.nightMode(this));
        setTheme(AppSettings.themeRes(this));
        super.onCreate(savedInstanceState);
        AppSettings.applyOrientation(this);
        setContentView(R.layout.activity_console);
        UiUtils.setupEdgeToEdge(this, findViewById(R.id.root));
        UiUtils.applyGlassBackdrop(this, (ImageView) findViewById(R.id.glassBackdrop));

        Toolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        instance = this;

        label_log = findViewById(R.id.label_log);
        edit_command = findViewById(R.id.edit_command);
        scroll_log = findViewById(R.id.logScrollView);
        button_command = findViewById(R.id.button_send);

        label_log.setText(currentLog);
        label_log.setTextSize(font_size);
        if (AppSettings.consoleAutoScroll(this)) {
            // Opening the console should land on the newest line.
            applyScrollOnNextDraw(true, 0);
        }

        edit_command.setOnKeyListener((p1, keyCode, p3) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                button_command.callOnClick();
                return true;
            }
            return false;
        });

        button_command.setOnClickListener(arg0 -> {
            log("> " + edit_command.getText());
            ServerUtils.writeCommand(edit_command.getText().toString());
            edit_command.setText("");
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_clear) {
            currentLog = new SpannableStringBuilder();
            label_log.setText("");
            return true;
        } else if (id == R.id.menu_copy) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("PocketMine log", currentLog));
            Toast.makeText(this, R.string.message_copied, Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.console_menu, menu);
        return true;
    }

    public static void log(String line) {
        if (MainActivity.ansiMode) {
            line = "<font>" + line.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace(" ", "&nbsp;")
                    .replace("\u001b[m", "</font>")
                    .replace("\u001b[0m", "</font>")
                    .replace("\u001b[1m", "</font><font style=\"font-weight:bold\">")
                    .replace("\u001b[3m", "</font><font style=\"font-style:italic\">")
                    .replace("\u001b[4m", "</font><font style=\"text-decoration:underline\">")
                    .replace("\u001b[8m", "</font><font>")
                    .replace("\u001b[9m", "</font><font style=\"text-decoration:line-through\">");
            line = line.replace("\u001b[38;5;16m", "</font><font color=\"#000000\">")
                    .replace("\u001b[38;5;19m", "</font><font color=\"#0000AA\">")
                    .replace("\u001b[38;5;34m", "</font><font color=\"#00AA00\">")
                    .replace("\u001b[38;5;37m", "</font><font color=\"#00AAAA\">")
                    .replace("\u001b[38;5;124m", "</font><font color=\"#AA0000\">")
                    .replace("\u001b[38;5;127m", "</font><font color=\"#AA00AA\">")
                    .replace("\u001b[38;5;214m", "</font><font color=\"#FFAA00\">")
                    .replace("\u001b[38;5;145m", "</font><font color=\"#AAAAAA\">")
                    .replace("\u001b[38;5;59m", "</font><font color=\"#555555\">")
                    .replace("\u001b[38;5;63m", "</font><font color=\"#5555FF\">")
                    .replace("\u001b[38;5;83m", "</font><font color=\"#55FF55\">")
                    .replace("\u001b[38;5;87m", "</font><font color=\"#55FFFF\">")
                    .replace("\u001b[38;5;203m", "</font><font color=\"#FF5555\">")
                    .replace("\u001b[38;5;207m", "</font><font color=\"#FF55FF\">")
                    .replace("\u001b[38;5;227m", "</font><font color=\"#FFFF55\">")
                    .replace("\u001b[38;5;231m", "</font><font color=\"#FFFFFF\">");

            line = line + "</font><br />";
        }
        final CharSequence result = MainActivity.ansiMode ? Html.fromHtml(line) : (line + "\n");
        currentLog.append(result);
        if (instance != null) {
            instance.runOnUiThread(() -> followNewOutput(result));
        }
    }

    /**
     * Appends one line and puts the log where the user expects it.
     *
     * label_log is selectable, so appending text makes the TextView reset its
     * own scroll anchor and drag the parent ScrollView back to the top - and
     * the old in-line fullScroll() was computed against the *previous* content
     * height anyway. So: note where we were, append, then settle the position
     * once the layout pass for the new text has run.
     *
     * AppSettings#consoleAutoScroll() decides the direction: on = jump to the
     * newest line, off = stay exactly where the user was.
     */
    private static void followNewOutput(CharSequence result) {
        if (label_log == null || scroll_log == null) {
            return;
        }
        final boolean follow = AppSettings.consoleAutoScroll(instance);
        final int keepY = scroll_log.getScrollY();
        label_log.append(result);
        applyScrollOnNextDraw(follow, keepY);
    }

    /** True while a scroll is already queued for the current burst of output. */
    private static boolean scrollPending = false;

    private static void applyScrollOnNextDraw(final boolean follow, final int keepY) {
        final ScrollView sv = scroll_log;
        final TextView tv = label_log;
        if (sv == null || tv == null) {
            return;
        }
        if (scrollPending) {
            // A hook is already queued for this burst; it uses the position
            // captured before the first line of the burst landed, which is
            // exactly what we want.
            return;
        }
        ViewTreeObserver vto = tv.getViewTreeObserver();
        if (vto == null || !vto.isAlive()) {
            tv.post(() -> settleScroll(sv, follow, keepY));
            return;
        }
        scrollPending = true;
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ViewTreeObserver o = tv.getViewTreeObserver();
                if (o != null && o.isAlive()) {
                    o.removeOnPreDrawListener(this);
                }
                scrollPending = false;
                settleScroll(sv, follow, keepY);
                return true;
            }
        });
    }

    private static void settleScroll(ScrollView sv, boolean follow, int keepY) {
        if (follow) {
            sv.fullScroll(ScrollView.FOCUS_DOWN);
        } else {
            sv.scrollTo(0, keepY);
        }
    }
}
