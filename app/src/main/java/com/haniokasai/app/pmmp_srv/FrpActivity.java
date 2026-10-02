package com.haniokasai.app.pmmp_srv;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;

public class FrpActivity extends AppCompatActivity {

    private TextInputEditText editAddr;
    private TextInputEditText editServerPort;
    private TextInputEditText editLocalPort;
    private TextInputEditText editRemotePort;
    private TextInputEditText editToken;
    private TextInputEditText editVersion;
    private TextInputEditText editCustomUrl;
    private MaterialCheckBox checkEnable;
    private TextView textStatus;
    private TextView textFrpStatus;
    private TextView textAbiWarning;
    private LinearProgressIndicator progress;
    private MaterialButton buttonInstall;

    private boolean busy = false;

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
        setContentView(R.layout.activity_frp);
        UiUtils.setupEdgeToEdge(this, findViewById(R.id.root));
        UiUtils.applyGlassBackdrop(this, (ImageView) findViewById(R.id.glassBackdrop));

        Toolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        editAddr = findViewById(R.id.edit_frp_addr);
        editServerPort = findViewById(R.id.edit_frp_server_port);
        editLocalPort = findViewById(R.id.edit_frp_local_port);
        editRemotePort = findViewById(R.id.edit_frp_remote_port);
        editToken = findViewById(R.id.edit_frp_token);
        editVersion = findViewById(R.id.edit_frp_version);
        editCustomUrl = findViewById(R.id.edit_frp_custom_url);
        checkEnable = findViewById(R.id.check_frp_enable);
        textStatus = findViewById(R.id.text_status);
        textFrpStatus = findViewById(R.id.text_frp_status);
        textAbiWarning = findViewById(R.id.text_abi_warning);
        progress = findViewById(R.id.progress);
        buttonInstall = findViewById(R.id.button_install);

        // Pre-fill from saved settings.
        editAddr.setText(AppSettings.frpServerAddr(this));
        editServerPort.setText(String.valueOf(AppSettings.frpServerPort(this)));
        editLocalPort.setText(String.valueOf(AppSettings.frpLocalPort(this)));
        editRemotePort.setText(String.valueOf(AppSettings.frpRemotePort(this)));
        editToken.setText(AppSettings.frpToken(this));
        editVersion.setText(AppSettings.frpVersion(this));
        checkEnable.setChecked(AppSettings.frpEnabled(this));

        // frpc is a linux_arm64 binary; it only runs on arm64 devices.
        if (!PhpManager.isArm64()) {
            textAbiWarning.setText(getString(R.string.frp_unsupported_abi, PhpManager.getAbiSummary()));
            textAbiWarning.setVisibility(View.VISIBLE);
            buttonInstall.setEnabled(false);
        }

        buttonInstall.setOnClickListener(v -> saveAndInstall());
        refreshStatus();
    }

    private void saveAndInstall() {
        if (busy) return;

        // Persist settings first.
        AppSettings.setFrpEnabled(this, checkEnable.isChecked());
        AppSettings.setFrpServerAddr(this, str(editAddr));
        AppSettings.setFrpToken(this, str(editToken));
        AppSettings.setFrpServerPort(this, parseInt(editServerPort, 7000));
        AppSettings.setFrpLocalPort(this, parseInt(editLocalPort, 19132));
        AppSettings.setFrpRemotePort(this, parseInt(editRemotePort, 19132));
        AppSettings.setFrpVersion(this, str(editVersion).isEmpty() ? FrpManager.DEFAULT_VERSION : str(editVersion));

        // Validate the essentials before downloading.
        if (checkEnable.isChecked() && str(editAddr).trim().isEmpty()) {
            Toast.makeText(this, R.string.frp_addr_required, Toast.LENGTH_SHORT).show();
            return;
        }

        busy = true;
        buttonInstall.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        textStatus.setVisibility(View.VISIBLE);

        String version = AppSettings.frpVersion(this);
        String custom = str(editCustomUrl).trim();
        String url = custom.isEmpty() ? FrpManager.defaultUrl(version) : custom;

        FrpManager.FrpInstallListener listener = new FrpManager.FrpInstallListener() {
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> textStatus.setText(message));
            }

            @Override
            public void onSuccess(String versionInfo) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    textStatus.setText(getString(R.string.frp_installed, versionInfo));
                    refreshStatus();
                    busy = false;
                    buttonInstall.setEnabled(PhpManager.isArm64());
                    Toast.makeText(FrpActivity.this,
                            getString(R.string.frp_installed, versionInfo), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    textStatus.setText(getString(R.string.frp_install_failed, String.valueOf(error)));
                    busy = false;
                    buttonInstall.setEnabled(PhpManager.isArm64());
                });
            }
        };

        new Thread(() -> FrpManager.installFromUrl(this, url, listener)).start();
    }

    private void refreshStatus() {
        File frpc = new File(FrpManager.getFrpcPath(this));
        if (!frpc.exists()) {
            textFrpStatus.setText(getString(R.string.frp_not_installed));
        } else if (FrpManager.isRunning()) {
            textFrpStatus.setText(getString(R.string.frp_running));
        } else {
            textFrpStatus.setText(getString(R.string.frp_installed_status, FrpManager.getFrpcPath(this)));
        }
    }

    private static String str(TextInputEditText e) {
        CharSequence c = e.getText();
        return c == null ? "" : c.toString().trim();
    }

    private static int parseInt(TextInputEditText e, int def) {
        try {
            return Integer.parseInt(str(e));
        } catch (Exception ex) {
            return def;
        }
    }
}
