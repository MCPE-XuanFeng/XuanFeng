package com.haniokasai.app.pmmp_srv;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.slider.Slider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class MainActivity extends AppCompatActivity {
    final static int
            CONSOLE_CODE = 1,
            INSTALL_PHP_CODE = CONSOLE_CODE + 1,
            DOWNLOAD_SERVER_CODE = INSTALL_PHP_CODE + 1,
            DELETE_SERVER = DOWNLOAD_SERVER_CODE + 1,
            BACKUP_SERVER = DELETE_SERVER + 1,
            FORCE_CLOSE_CODE = BACKUP_SERVER + 1,
            HP = BACKUP_SERVER + 2,
            TWITTER = HP + 1,
            BL_TWITTER = TWITTER + 1,
            HELP = BL_TWITTER + 1;

    public static Intent serverIntent = null;
    public static MainActivity instance = null;
    public static MaterialCheckBox check_ansi = null;
    public static MaterialButton button_start = null, button_stop = null;
    public static Slider seekbar_fontsize = null;
    public static MenuItem menu_force_close = null, menu_install_php = null, menu_download_server = null,
            menu_delete_server = null, menu_backup_server = null, menu_hp = null, menu_twitter = null,
            menu_bl_twitter = null, menu_help = null;
    public static SharedPreferences config = null;

    public static boolean isStarted = false, ansiMode = false;

    public static final String[] jenkins_pocketmine = new String[]{
            "BlueLight (haniokasai)|http://jenkins.haniokasai.com/job/BlueLight-PMMP/",
            "Genisys (ZXDA)|https://jenkins.zxda.net/job/Genisys/",
            "ClearSky-PHP7 (ZXDA)|https://jenkins.zxda.net/job/ClearSky-PHP7/",
            "PocketMine-MP (ZXDA)|https://jenkins.zxda.net/job/PocketMine-MP/",
            "PocketMine-MP (pmmp)|https://jenkins.pmmp.gq/job/PocketMine-MP/"
    };

    private MaterialButton button_open_installer;
    private android.widget.TextView text_php_status;

    private final ActivityResultLauncher<String[]> bgLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                    }
                    AppSettings.setBackgroundUri(this, uri.toString());
                    UiUtils.applyGlassBackdrop(this, (ImageView) findViewById(R.id.glassBackdrop));
                    toast(R.string.bg_set);
                }
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrap(newBase));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppSettings.nightMode(this));
        setTheme(AppSettings.themeRes(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UiUtils.setupEdgeToEdge(this, findViewById(R.id.root));
        UiUtils.applyGlassBackdrop(this, (ImageView) findViewById(R.id.glassBackdrop));

        ensureAllFilesAccess();

        Toolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());

        instance = this;
        config = getSharedPreferences("config", 0);
        ServerUtils.setContext(instance);

        ansiMode = config.getBoolean("ANSIMode", ansiMode);

        button_stop = findViewById(R.id.button_stop);
        button_start = findViewById(R.id.button_start);
        check_ansi = findViewById(R.id.check_ansi);
        seekbar_fontsize = findViewById(R.id.seekbar_fontsize);
        text_php_status = findViewById(R.id.text_php_status);
        button_open_installer = findViewById(R.id.button_open_installer);

        seekbar_fontsize.setValue(config.getInt("ConsoleFontSize", 16));
        ConsoleActivity.font_size = seekbar_fontsize.getValue();
        check_ansi.setChecked(ansiMode);

        check_ansi.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ansiMode = isChecked;
            config.edit().putBoolean("ANSIMode", ansiMode).apply();
        });

        seekbar_fontsize.addOnChangeListener((slider, value, fromUser) -> {
            ConsoleActivity.font_size = value;
            config.edit().putInt("ConsoleFontSize", (int) value).apply();
        });

        button_start.setOnClickListener(v -> {
            isStarted = true;
            refreshEnabled();
            serverIntent = new Intent(instance, ServerService.class);
            startService(serverIntent);
            // Spawn the PHP process on a worker thread: process start plus PHP
            // preparation can be slow and must not block the UI thread (ANR).
            new Thread(ServerUtils::runServer).start();
        });
        button_stop.setOnClickListener(v -> {
            if (ServerUtils.isRunning()) {
                ServerUtils.writeCommand("stop");
            }
        });
        button_open_installer.setOnClickListener(v -> startActivity(new Intent(instance, InstallPhpActivity.class)));

        refreshEnabled();
        updatePhpStatus();
        // Auto-install the bundled PHP 7.x binary + busybox on first launch.
        if (!ServerUtils.isPhpInstalled()) {
            autoInstallBundledPhp();
        }
    }

    private void updatePhpStatus() {
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
        text_php_status.setText(getString(R.string.php_current, v));
    }

    private void autoInstallBundledPhp() {
        final ProgressDialog dialog = new ProgressDialog(instance);
        dialog.setCancelable(false);
        dialog.setMessage(getString(R.string.message_installing));
        dialog.show();
        new Thread(() -> {
            PhpManager.installBundled(instance, new PhpManager.InstallListener() {
                @Override
                public void onProgress(String message) {
                    runOnUiThread(() -> dialog.setMessage(message));
                }

                @Override
                public void onSuccess(String versionInfo) {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        toast(getString(R.string.php_installed, versionInfo));
                        updatePhpStatus();
                        refreshEnabled();
                    });
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        toast(getString(R.string.php_install_failed, error));
                    });
                }
            });
        }).start();
    }

    public static void refreshEnabled() {
        check_ansi.setEnabled(!isStarted);
        if (menu_install_php != null) {
            menu_install_php.setEnabled(!isStarted);
            menu_download_server.setEnabled(!isStarted);
        }
        if (!ServerUtils.isPhpInstalled()) {
            button_start.setEnabled(false);
        } else {
            button_start.setEnabled(!isStarted);
        }
        button_stop.setEnabled(isStarted);
    }

    public static void stopNotifyService() {
        if (instance != null && serverIntent != null) {
            instance.runOnUiThread(() -> {
                isStarted = false;
                refreshEnabled();
                instance.stopService(serverIntent);
            });
        }
    }

    public static String getInternetString(String url) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(openNetConnection(url).getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\r');
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            instance.toast(e.toString());
        }
        return null;
    }

    public static void downloadServer(String jenkins, File saveTo, final ProgressDialog dialog) {
        try {
            JSONObject json = new JSONObject(getInternetString(jenkins + "lastSuccessfulBuild/api/json"));
            JSONArray artifacts = json.getJSONArray("artifacts");
            if (artifacts.length() <= 0) {
                throw new Exception(instance.getString(R.string.message_no_artifacts));
            }
            json = artifacts.getJSONObject(0);
            downloadFile(jenkins + "lastSuccessfulBuild/artifact/" + json.getString("relativePath"), saveTo, dialog);
        } catch (Exception e) {
            instance.toast(e.getMessage());
        }
    }

    public static void downloadFile(String url, File saveTo, final ProgressDialog dialog) {
        OutputStream output = null;
        InputStream input = null;
        try {
            if (saveTo.exists()) saveTo.delete();
            URLConnection connection = openNetConnection(url);
            input = new BufferedInputStream(connection.getInputStream());
            output = new FileOutputStream(saveTo);
            int count = 0;
            long read = 0;
            if (dialog != null) {
                final long max = connection.getContentLength();
                instance.runOnUiThread(() -> dialog.setMax((int) max / 1024));
            }
            byte[] buffer = new byte[4096];
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
                read += count;
                if (dialog != null) {
                    final int temp = (int) (read / 1000);
                    instance.runOnUiThread(() -> dialog.setProgress(temp));
                }
            }
            output.close();
            input.close();
            instance.toast(R.string.message_done);
        } catch (Exception e) {
            instance.toast(e.getMessage());
        } finally {
            try {
                if (output != null) output.close();
                if (input != null) input.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public static URLConnection openNetConnection(String url) throws Exception {
        final SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] p1, String p2) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] p1, String p2) throws CertificateException {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }
        }, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        URL req = new URL(url);
        URLConnection connection = req.openConnection();
        connection.connect();
        return connection;
    }

    public void toast(int text) {
        toast(getString(text));
    }

    public void toast(final String text) {
        if (instance != null) {
            instance.runOnUiThread(() -> Toast.makeText(instance, text, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        menu_install_php = menu.findItem(R.id.menu_install_php);
        menu_download_server = menu.findItem(R.id.menu_download);
        menu_delete_server = menu.findItem(R.id.menu_delete_server);
        menu_backup_server = menu.findItem(R.id.menu_backup_server);
        menu_force_close = menu.findItem(R.id.menu_kill);
        menu_hp = menu.findItem(R.id.menu_hp);
        menu_twitter = menu.findItem(R.id.menu_twitter);
        menu_bl_twitter = menu.findItem(R.id.menu_bl_twitter);
        menu_help = menu.findItem(R.id.menu_help);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        Uri uri;
        Intent intent;
        final ProgressDialog processing_dialog = new ProgressDialog(instance);
        if (id == R.id.menu_console) {
            startActivity(new Intent(instance, ConsoleActivity.class));
            return true;
        } else if (id == R.id.menu_install_php) {
            startActivity(new Intent(instance, InstallPhpActivity.class));
            return true;
        } else if (id == R.id.menu_kill) {
            ServerUtils.killServer();
            if (serverIntent != null) stopService(serverIntent);
            isStarted = false;
            refreshEnabled();
            return true;
        } else if (id == R.id.menu_download) {
            AlertDialog.Builder download_dialog_builder = new AlertDialog.Builder(this);
            String[] jenkins = jenkins_pocketmine, values = new String[jenkins.length];
            for (int i = 0; i < jenkins.length; ++i) {
                values[i] = jenkins[i].split("\\|", 2)[0];
            }
            download_dialog_builder.setTitle(getString(R.string.message_select_repository).replace("%s", "PocketMine"));
            download_dialog_builder.setItems(values, (p1, p2) -> {
                p1.dismiss();
                processing_dialog.setCancelable(false);
                processing_dialog.setMessage(getString(R.string.message_downloading).replace("%s", "PocketMine-MP.phar"));
                processing_dialog.setIndeterminate(false);
                processing_dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                processing_dialog.show();
                new Thread(() -> {
                    String[] wtf = jenkins_pocketmine;
                    wtf = wtf[p2].split("\\|");
                    downloadServer(wtf[1], new File(ServerUtils.getDataDirectory() + "/" + ("PocketMine-MP.phar")), processing_dialog);
                    runOnUiThread(() -> processing_dialog.dismiss());
                }).start();
            });
            download_dialog_builder.show();
            return true;
        } else if (id == R.id.menu_delete_server) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setMessage(R.string.dialog_delete);
            alertDialogBuilder.setPositiveButton(R.string.dialog_ok, (dialog, which) -> serverdel());
            alertDialogBuilder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> {
            });
            alertDialogBuilder.setCancelable(true);
            alertDialogBuilder.create().show();
            return true;
        } else if (id == R.id.menu_backup_server) {
            processing_dialog.setCancelable(false);
            processing_dialog.setMessage(getString(R.string.message_backuping));
            processing_dialog.show();
            new Thread(() -> {
                try {
                    ServerUtils.BackupDir();
                    toast(R.string.message_backup_success);
                } catch (Exception e) {
                    toast(getString(R.string.message_backup_fail) + "\n" + e.toString());
                }
                runOnUiThread(() -> {
                    processing_dialog.dismiss();
                    refreshEnabled();
                });
            }).start();
            return true;
        } else if (id == R.id.menu_hp) {
            uri = Uri.parse("http://bluelight.cf/");
            intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_twitter) {
            uri = Uri.parse(String.valueOf(R.string.twitter_hani));
            intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_bl_twitter) {
            uri = Uri.parse("https://twitter.com/BlueLightJapan");
            intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_help) {
            uri = Uri.parse("https://twitter.com/BlueLightJapan");
            intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            return true;
        } else if (id == R.id.menu_settings) {
            showSettingsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings_title);
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        builder.setView(view);

        RadioGroup rgLang = view.findViewById(R.id.rg_lang);
        RadioGroup rgTheme = view.findViewById(R.id.rg_theme);
        RadioGroup rgAccent = view.findViewById(R.id.rg_accent);

        String lang = AppSettings.lang(this);
        if (lang.isEmpty()) rgLang.check(R.id.rb_lang_sys);
        else if ("zh".equals(lang)) rgLang.check(R.id.rb_lang_zh);
        else rgLang.check(R.id.rb_lang_en);

        String theme = AppSettings.theme(this);
        if ("dark".equals(theme)) rgTheme.check(R.id.rb_theme_dark);
        else if ("custom".equals(theme)) rgTheme.check(R.id.rb_theme_custom);
        else rgTheme.check(R.id.rb_theme_light);

        switch (AppSettings.accent(this)) {
            case "green":
                rgAccent.check(R.id.rb_accent_green);
                break;
            case "purple":
                rgAccent.check(R.id.rb_accent_purple);
                break;
            case "amber":
                rgAccent.check(R.id.rb_accent_amber);
                break;
            case "teal":
                rgAccent.check(R.id.rb_accent_teal);
                break;
            default:
                rgAccent.check(R.id.rb_accent_blue);
                break;
        }

        MaterialCheckBox checkPty = view.findViewById(R.id.check_pty_console);
        checkPty.setChecked(AppSettings.ptyConsole(this));

        view.findViewById(R.id.button_change_bg).setOnClickListener(v -> {
            bgLauncher.launch(new String[]{"image/*"});
        });
        view.findViewById(R.id.button_reset_bg).setOnClickListener(v -> {
            AppSettings.clearBackgroundUri(this);
            UiUtils.applyGlassBackdrop(this, (ImageView) findViewById(R.id.glassBackdrop));
            toast(R.string.bg_reset_done);
        });

        builder.setPositiveButton(R.string.dialog_ok, (d, w) -> {
            String newLang = "";
            int lid = rgLang.getCheckedRadioButtonId();
            if (lid == R.id.rb_lang_zh) newLang = "zh";
            else if (lid == R.id.rb_lang_en) newLang = "en";

            String newTheme = "light";
            int tid = rgTheme.getCheckedRadioButtonId();
            if (tid == R.id.rb_theme_dark) newTheme = "dark";
            else if (tid == R.id.rb_theme_custom) newTheme = "custom";

            String newAccent = "blue";
            int aid = rgAccent.getCheckedRadioButtonId();
            if (aid == R.id.rb_accent_green) newAccent = "green";
            else if (aid == R.id.rb_accent_purple) newAccent = "purple";
            else if (aid == R.id.rb_accent_amber) newAccent = "amber";
            else if (aid == R.id.rb_accent_teal) newAccent = "teal";

            SharedPreferences.Editor ed = AppSettings.prefs(this).edit();
            ed.putString(AppSettings.KEY_LANG, newLang);
            ed.putString(AppSettings.KEY_THEME, newTheme);
            ed.putString(AppSettings.KEY_ACCENT, newAccent);
            ed.apply();

            boolean newPty = checkPty.isChecked();
            boolean ptyChanged = newPty != AppSettings.ptyConsole(this);
            if (ptyChanged) {
                AppSettings.setPtyConsole(this, newPty);
            }

            toast(getString(R.string.settings_applied));
            if (ptyChanged) {
                toast(getString(R.string.settings_pty_changed));
            }
            recreate();
        });
        builder.setNegativeButton(R.string.dialog_cancel, (d, w) -> d.dismiss());
        AlertDialog dialog = builder.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_glass_surface);
        }
    }

    /**
     * Ensure the app can write to /storage/emulated/0/PocketMine.
     *
     * - API >= 30: WRITE_EXTERNAL_STORAGE is scoped and can never reach an
     *   arbitrary /PocketMine folder, so MANAGE_EXTERNAL_STORAGE (All files
     *   access) is required. Open the system settings page for this package.
     *   Without this check the old code requested WRITE_EXTERNAL_STORAGE on
     *   every launch, which the OS never grants above API 29 — hence the
     *   "repeatedly asks for permission" loop.
     * - API 23..29: request WRITE_EXTERNAL_STORAGE at runtime (legacy storage).
     */
    private void ensureAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.storage_permission_title)
                        .setMessage(R.string.storage_permission_message)
                        .setPositiveButton(R.string.dialog_ok, (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            try {
                                startActivity(intent);
                            } catch (Exception e) {
                                toast(R.string.storage_permission_denied);
                            }
                        })
                        .setNegativeButton(R.string.dialog_cancel, (d, w) ->
                                toast(R.string.storage_permission_denied))
                        .show();
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.storage_permission_title)
                    .setMessage(R.string.storage_permission_message)
                    .setPositiveButton(R.string.dialog_ok, (d, w) ->
                            requestPermissions(new String[]{
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001))
                    .setNegativeButton(R.string.dialog_cancel, (d, w) ->
                            toast(R.string.storage_permission_denied))
                    .setCancelable(false)
                    .show();
        }
    }

    public void serverdel() {
        new Thread(() -> {
            ServerUtils.killServer();
            if (serverIntent != null) stopService(serverIntent);
            if (ServerUtils.RemoveSrvDirectory()) {
                runOnUiThread(() -> toast(R.string.message_delete_success));
            } else {
                runOnUiThread(() -> toast(R.string.message_delete_failed));
            }
        }).start();
    }
}
