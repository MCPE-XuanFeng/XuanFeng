package com.haniokasai.app.pmmp_srv;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * "Advanced" page: lets power users override how the server is launched.
 *
 *   - Custom startup script (start.sh): when enabled, ServerUtils runs this
 *     script instead of the built-in PocketMine/Nukkit command. This is the
 *     escape hatch for running Nukkit with a specific Java, a tuned PocketMine,
 *     or anything else.
 *   - Java runtime path: where to find "java" for Nukkit (empty = auto-detect).
 */
public class AdvancedActivity extends AppCompatActivity {

    private TextInputEditText editScript;
    private TextInputEditText editJavaPath;
    private MaterialCheckBox checkCustomScript;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppSettings.nightMode(this));
        AppSettings.applyTheme(this);
        AppSettings.applyOrientation(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advanced);
        UiUtils.setupEdgeToEdge(this, findViewById(R.id.root));
        UiUtils.applyGlassBackdrop(this, (android.widget.ImageView) findViewById(R.id.glassBackdrop));

        Toolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editScript = findViewById(R.id.edit_script);
        editJavaPath = findViewById(R.id.edit_java_path);
        checkCustomScript = findViewById(R.id.check_custom_script);

        // Restore current values.
        checkCustomScript.setChecked(AppSettings.customScriptEnabled(this));
        editJavaPath.setText(AppSettings.javaPath(this));
        editScript.setText(readScript());

        findViewById(R.id.button_save_script).setOnClickListener(v -> {
            saveScript(editScript.getText() == null ? "" : editScript.getText().toString());
            AppSettings.setCustomScriptEnabled(this, checkCustomScript.isChecked());
            Toast.makeText(this, R.string.advanced_script_saved, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.button_reset_script).setOnClickListener(v -> {
            editScript.setText(defaultScript());
        });

        findViewById(R.id.button_save_java).setOnClickListener(v -> {
            String p = editJavaPath.getText() == null ? "" : editJavaPath.getText().toString().trim();
            AppSettings.setJavaPath(this, p);
            Toast.makeText(this, R.string.settings_applied, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String readScript() {
        File f = new File(ServerUtils.getCustomScriptPath());
        if (!f.exists()) return defaultScript();
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = in.read(buf);
            in.close();
            return n > 0 ? new String(buf, 0, n, "UTF-8") : defaultScript();
        } catch (Exception e) {
            return defaultScript();
        }
    }

    private void saveScript(String content) {
        File f = new File(ServerUtils.getCustomScriptPath());
        try {
            FileOutputStream os = new FileOutputStream(f);
            os.write(content.getBytes("UTF-8"));
            os.close();
            // Make it executable so it can be run directly if the user prefers.
            PhpManager.makeExecutable(f);
        } catch (Exception e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Sensible default that launches the active server type. */
    private String defaultScript() {
        String dir = ServerUtils.getDataDirectory();
        if ("nukkit".equals(AppSettings.serverType(this))) {
            String java = AppSettings.javaPath(this);
            if (java == null || java.isEmpty()) java = "java";
            return "#!/system/bin/sh\n"
                    + "# Custom Nukkit startup script (runs from the server directory).\n"
                    + "cd \"" + dir + "\"\n"
                    + "exec " + shellQuote(java) + " -jar nukkit.jar\n";
        }
        return "#!/system/bin/sh\n"
                + "# Custom PocketMine-MP startup script (runs from the server directory).\n"
                + "cd \"" + dir + "\"\n"
                + "exec ./php -c php.ini PocketMine-MP.phar\n";
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
