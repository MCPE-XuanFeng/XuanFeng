package com.haniokasai.app.pmmp_srv;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;

public class InstallPhpActivity extends AppCompatActivity {

    // Source spinner positions
    private static final int SRC_PMMP = 0;
    private static final int SRC_BUNDLED = 1;
    private static final int SRC_CUSTOM = 2;
    private static final int SRC_LOCAL = 3;

    private Spinner spinnerSource;
    private Spinner spinnerVersion;
    private TextInputEditText editCustomUrl;
    private TextView textStatus;
    private TextView textAbiWarning;
    private TextView textPhpStatus;
    private LinearProgressIndicator progress;
    private MaterialButton buttonInstall;
    private MaterialButton buttonChooseFile;
    private TextView textLocalFile;
    private TextView textLocalHint;
    private MaterialCheckBox checkProxy;
    private View proxyFields;
    private TextInputEditText editProxyHost;
    private TextInputEditText editProxyPort;
    private Uri selectedLocalUri = null;

    private boolean busy = false;

    private final ActivityResultLauncher<String[]> pickLocal = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                selectedLocalUri = uri;
                String name = localFileName(uri);
                textLocalFile.setText(name != null ? name : uri.toString());
                buttonInstall.setEnabled(!busy);
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppSettings.nightMode(this));
        setTheme(AppSettings.themeRes(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_install_php);
        UiUtils.setupEdgeToEdge(this, findViewById(R.id.root));
        UiUtils.applyGlassBackdrop(this, (ImageView) findViewById(R.id.glassBackdrop));

        Toolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerSource = findViewById(R.id.spinner_source);
        spinnerVersion = findViewById(R.id.spinner_version);
        editCustomUrl = findViewById(R.id.edit_custom_url);
        textStatus = findViewById(R.id.text_status);
        textAbiWarning = findViewById(R.id.text_abi_warning);
        textPhpStatus = findViewById(R.id.text_php_status);
        progress = findViewById(R.id.progress);
        buttonInstall = findViewById(R.id.button_install);
        buttonChooseFile = findViewById(R.id.button_choose_file);
        textLocalFile = findViewById(R.id.text_local_file);
        textLocalHint = findViewById(R.id.text_local_hint);
        buttonChooseFile.setOnClickListener(v -> pickLocal.launch(new String[]{"*/*"}));

        // ---- download proxy ----
        checkProxy = findViewById(R.id.check_proxy);
        proxyFields = findViewById(R.id.proxy_fields);
        editProxyHost = findViewById(R.id.edit_proxy_host);
        editProxyPort = findViewById(R.id.edit_proxy_port);
        boolean proxyOn = AppSettings.proxyEnabled();
        checkProxy.setChecked(proxyOn);
        proxyFields.setVisibility(proxyOn ? View.VISIBLE : View.GONE);
        editProxyHost.setText(AppSettings.proxyHost());
        editProxyPort.setText(String.valueOf(AppSettings.proxyPort()));
        checkProxy.setOnCheckedChangeListener((btn, checked) ->
                proxyFields.setVisibility(checked ? View.VISIBLE : View.GONE));

        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{
                getString(R.string.php_source_pmmp),
                getString(R.string.php_source_bundled),
                getString(R.string.php_source_custom),
                getString(R.string.php_source_local)
        });
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSource.setAdapter(sourceAdapter);

        ArrayAdapter<String> versionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new java.util.ArrayList<String>() {{
                    for (PhpManager.PhpRelease r : PhpManager.pmmpReleases()) add(r.label);
                }});
        versionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVersion.setAdapter(versionAdapter);
        spinnerSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onSourceChanged(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        buttonInstall.setOnClickListener(v -> doInstall());

        refreshPhpStatus();
        onSourceChanged(spinnerSource.getSelectedItemPosition());
    }

    private void onSourceChanged(int position) {
        // Local controls are only relevant for the local-file source.
        buttonChooseFile.setVisibility(View.GONE);
        textLocalFile.setVisibility(View.GONE);
        textLocalHint.setVisibility(View.GONE);

        if (position == SRC_PMMP) {
            setVersionItems(PhpManager.pmmpReleasesLabels(), 0);
            spinnerVersion.setEnabled(true);
            editCustomUrl.setEnabled(false);
            if (!PhpManager.isArm64()) {
                textAbiWarning.setText(getString(R.string.php_unsupported_abi, PhpManager.getAbiSummary()));
                textAbiWarning.setVisibility(View.VISIBLE);
                buttonInstall.setEnabled(false);
            } else {
                textAbiWarning.setVisibility(View.GONE);
                buttonInstall.setEnabled(!busy);
            }
        } else if (position == SRC_BUNDLED) {
            setVersionItems(PhpManager.bundledBuildsLabels(), 0);
            spinnerVersion.setEnabled(true);
            editCustomUrl.setEnabled(false);
            // Warn only when the *selected* build cannot run here: the bundled
            // set mixes aarch64 builds with older 32-bit ones.
            int pos = spinnerVersion.getSelectedItemPosition();
            PhpManager.BundledBuild b = PhpManager.bundledBuilds().get(
                    Math.min(pos, PhpManager.bundledBuilds().size() - 1));
            boolean runnable = b.arm64 ? PhpManager.isArm64() : PhpManager.deviceSupports32Bit();
            if (!runnable) {
                textAbiWarning.setText(getString(R.string.php_bundled_arch_warning,
                        PhpManager.getAbiSummary()));
                textAbiWarning.setVisibility(View.VISIBLE);
                buttonInstall.setEnabled(false);
            } else {
                textAbiWarning.setVisibility(View.GONE);
                buttonInstall.setEnabled(!busy);
            }
        } else if (position == SRC_CUSTOM) {
            spinnerVersion.setEnabled(false);
            editCustomUrl.setEnabled(true);
            textAbiWarning.setVisibility(View.GONE);
            buttonInstall.setEnabled(!busy);
        } else { // local file
            spinnerVersion.setEnabled(false);
            editCustomUrl.setEnabled(false);
            textAbiWarning.setVisibility(View.GONE);
            buttonChooseFile.setVisibility(View.VISIBLE);
            textLocalHint.setVisibility(View.VISIBLE);
            textLocalFile.setVisibility(View.VISIBLE);
            buttonInstall.setEnabled(!busy && selectedLocalUri != null);
        }
    }

    /** Rebuilds the version spinner for the selected source. */
    private void setVersionItems(java.util.List<String> labels, int selection) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        final int sel = Math.max(0, Math.min(selection, labels.size() - 1));
        spinnerVersion.setAdapter(a);
        spinnerVersion.setSelection(sel);
        // Re-evaluate the architecture warning when the user picks another build.
        spinnerVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (spinnerSource.getSelectedItemPosition() == SRC_BUNDLED) {
                    onSourceChanged(SRC_BUNDLED);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void refreshPhpStatus() {
        File php = new File(ServerUtils.getPhpBinaryPath());
        String v;
        if (!php.exists()) {
            v = getString(R.string.php_none);
        } else {
            v = PhpManager.getPhpVersion(php);
            if (v == null) {
                if (!PhpManager.isBinaryCompatibleWithDevice(php)) {
                    v = getString(R.string.php_incompatible,
                            PhpManager.getBinaryArch(php), PhpManager.getAbiSummary());
                } else {
                    String err = PhpManager.testPhp(php);
                    if (err.startsWith("ERROR:")) err = err.substring(6).trim();
                    v = getString(R.string.php_run_failed, err);
                }
            }
        }
        textPhpStatus.setText(v);
    }

    private void doInstall() {
        if (busy) return;
        int src = spinnerSource.getSelectedItemPosition();
        busy = true;
        buttonInstall.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        textStatus.setVisibility(View.VISIBLE);

        PhpManager.InstallListener listener = new PhpManager.InstallListener() {
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> textStatus.setText(message));
            }

            @Override
            public void onSuccess(String versionInfo) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    textStatus.setText(getString(R.string.php_installed, versionInfo));
                    refreshPhpStatus();
                    busy = false;
                    onSourceChanged(spinnerSource.getSelectedItemPosition());
                    Toast.makeText(InstallPhpActivity.this,
                            getString(R.string.php_installed, versionInfo), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    textStatus.setText(getString(R.string.php_install_failed, String.valueOf(error)));
                    busy = false;
                    onSourceChanged(spinnerSource.getSelectedItemPosition());
                });
            }
        };

        // Persist the proxy settings before any download starts, so the
        // background download reads them.
        saveProxySettings();

        if (src == SRC_BUNDLED) {
            int vpos = spinnerVersion.getSelectedItemPosition();
            java.util.List<PhpManager.BundledBuild> builds = PhpManager.bundledBuilds();
            PhpManager.BundledBuild build = builds.get(
                    Math.max(0, Math.min(vpos, builds.size() - 1)));
            textStatus.setText(getString(R.string.php_extracting));
            new Thread(() -> PhpManager.installBundled(this, build, listener)).start();
        } else if (src == SRC_PMMP) {
            int vpos = spinnerVersion.getSelectedItemPosition();
            String url = PhpManager.pmmpReleases().get(vpos).buildUrl();
            textStatus.setText(getString(R.string.php_downloading, url));
            new Thread(() -> PhpManager.installFromUrl(this, url, listener)).start();
        } else if (src == SRC_CUSTOM) {
            String url = editCustomUrl.getText() == null ? "" : editCustomUrl.getText().toString().trim();
            if (url.isEmpty()) {
                progress.setVisibility(View.GONE);
                textStatus.setText(getString(R.string.php_install_failed, "empty URL"));
                busy = false;
                onSourceChanged(src);
                return;
            }
            textStatus.setText(getString(R.string.php_downloading, url));
            new Thread(() -> PhpManager.installFromUrl(this, url, listener)).start();
        } else { // local file
            if (selectedLocalUri == null) {
                progress.setVisibility(View.GONE);
                textStatus.setText(getString(R.string.php_install_failed, "no file selected"));
                busy = false;
                onSourceChanged(src);
                return;
            }
            textStatus.setText(getString(R.string.php_extracting));
            new Thread(() -> PhpManager.installLocalFile(this, selectedLocalUri, listener)).start();
        }
    }

    /** Stores the proxy checkbox/host/port into AppSettings. */
    private void saveProxySettings() {
        AppSettings.setProxyEnabled(this, checkProxy.isChecked());
        String host = editProxyHost.getText() == null ? "" : editProxyHost.getText().toString().trim();
        if (!host.isEmpty()) {
            AppSettings.setProxyHost(this, host);
        }
        String portStr = editProxyPort.getText() == null ? "" : editProxyPort.getText().toString().trim();
        try {
            if (!portStr.isEmpty()) {
                AppSettings.setProxyPort(this, Integer.parseInt(portStr));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private String localFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = c.getString(idx);
                }
            } catch (Exception ignored) {
            }
        }
        if (result == null) result = uri.getLastPathSegment();
        return result;
    }
}
