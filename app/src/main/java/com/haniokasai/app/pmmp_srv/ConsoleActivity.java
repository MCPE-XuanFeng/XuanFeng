package com.haniokasai.app.pmmp_srv;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
        AppSettings.applyTheme(this);
        // Apply the saved orientation BEFORE super.onCreate so the very
        // first frame already uses the right direction (no landscape flash).
        AppSettings.applyOrientation(this);
        super.onCreate(savedInstanceState);
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
        UiUtils.tintMenuIcons(this, menu);
        return true;
    }

    /** Max kept log characters: the buffer is trimmed from the oldest end. */
    private static final int MAX_LOG_CHARS = 200000;

    /** Pending output waiting to be pushed into the TextView. */
    private static final SpannableStringBuilder pendingUi = new SpannableStringBuilder();
    private static final Object PENDING_LOCK = new Object();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static boolean flushScheduled = false;

    /**
     * Flushes buffered console output to the TextView at most once every
     * FLUSH_MS. A busy server can emit hundreds of lines per second; appending
     * and re-measuring the text for every single one was the main UI
     * bottleneck (constant relayout + scroll churn).
     */
    private static final long FLUSH_MS = 120L;

    private static final Runnable FLUSH_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            CharSequence chunk;
            synchronized (PENDING_LOCK) {
                chunk = pendingUi.subSequence(0, pendingUi.length());
                pendingUi.clear();
                flushScheduled = false;
            }
            if (label_log == null) {
                return;
            }
            followNewOutput(chunk);
        }
    };

    /**
     * Logs a localized message: resolves the string resource with the
     * application context (which follows the in-app language) and optional
     * format arguments, so console diagnostics can be translated too.
     */
    public static void log(int resId, Object... args) {
        android.content.Context c = AppSettings.AppHolder.ctx;
        if (c == null && instance != null) c = instance.getApplicationContext();
        if (c == null) return;
        log(c.getString(resId, args));
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
        // In ANSI mode Html.fromHtml() collapses an empty message to nothing,
        // so a blank line would not push the view down. Keep it as a newline:
        // every message - even an empty one - must advance the scroll.
        CharSequence result = MainActivity.ansiMode ? Html.fromHtml(line) : (line + "\n");
        if (result == null || result.length() == 0) {
            result = "\n";
        }
        currentLog.append(result);
        // Keep the buffer bounded so a long session cannot eat all the memory.
        if (currentLog.length() > MAX_LOG_CHARS) {
            int cut = currentLog.length() - MAX_LOG_CHARS;
            currentLog.delete(0, cut);
            // The view is rebuilt from currentLog on the next flush.
            synchronized (PENDING_LOCK) {
                pendingUi.clear();
            }
            if (instance != null) {
                instance.runOnUiThread(() -> {
                    if (label_log != null) label_log.setText(currentLog);
                });
            }
        }
        synchronized (PENDING_LOCK) {
            pendingUi.append(result);
            if (!flushScheduled) {
                flushScheduled = true;
                MAIN_HANDLER.postDelayed(FLUSH_RUNNABLE, FLUSH_MS);
            }
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
        if (!follow) {
            sv.scrollTo(0, keepY);
            return;
        }
        // Hard-jump to the bottom (no smooth animation: it gets interrupted by
        // the next burst of output and then stops halfway).
        scrollToBottom(sv);
        // Appending to a selectable TextView triggers a second relayout
        // (text measurement + selection anchor), so settle once more after it.
        sv.post(() -> scrollToBottom(sv));
    }

    /** Puts the log scroll at the very bottom of its content. */
    private static void scrollToBottom(ScrollView sv) {
        View child = sv.getChildAt(0);
        if (child == null) {
            sv.fullScroll(ScrollView.FOCUS_DOWN);
            return;
        }
        int bottom = Math.max(0, child.getBottom() - sv.getHeight() + sv.getPaddingBottom());
        sv.scrollTo(0, bottom);
    }
}
