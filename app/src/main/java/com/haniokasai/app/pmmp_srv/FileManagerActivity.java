package com.haniokasai.app.pmmp_srv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileManagerActivity extends AppCompatActivity {

    private TextView textPath;
    private ListView listFiles;
    private File currentDir;
    private final java.util.ArrayDeque<File> history = new java.util.ArrayDeque<>();

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

        // Honor the toggle from Settings; the menu hides the entry, but guard here too.
        if (!AppSettings.fileManagerEnabled(this)) {
            Toast.makeText(this, R.string.file_manager_disabled, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_file_manager);
        UiUtils.setupEdgeToEdge(this, findViewById(R.id.root));
        UiUtils.applyGlassBackdrop(this, (ImageView) findViewById(R.id.glassBackdrop));

        Toolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        textPath = findViewById(R.id.text_path);
        listFiles = findViewById(R.id.list_files);

        currentDir = new File(ServerUtils.getDataDirectory());
        if (!currentDir.exists()) {
            currentDir.mkdirs();
        }

        listFiles.setOnItemClickListener((parent, view, position, id) -> {
            FileItem item = (FileItem) parent.getItemAtPosition(position);
            if (item.file == null) return;
            if (item.file.isDirectory()) {
                history.push(currentDir);
                currentDir = item.file;
                refresh();
            } else {
                showFileActions(item.file);
            }
        });

        listFiles.setOnItemLongClickListener((parent, view, position, id) -> {
            FileItem item = (FileItem) parent.getItemAtPosition(position);
            if (item.file != null) confirmDelete(item.file);
            return true;
        });

        refresh();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (!history.isEmpty()) {
                currentDir = history.pop();
                refresh();
            } else {
                finish();
            }
            return true;
        } else if (id == R.id.menu_refresh) {
            refresh();
            return true;
        } else if (id == R.id.menu_go_root) {
            history.clear();
            currentDir = new File(ServerUtils.getDataDirectory());
            if (!currentDir.exists()) currentDir.mkdirs();
            refresh();
            return true;
        } else if (id == R.id.menu_new_folder) {
            showNewFolderDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!history.isEmpty()) {
            currentDir = history.pop();
            refresh();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_manager_menu, menu);
        UiUtils.tintMenuIcons(this, menu);
        return true;
    }

    private void showNewFolderDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.file_manager_new_folder_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.file_manager_new_folder)
                .setView(input)
                .setPositiveButton(R.string.dialog_ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File target = new File(currentDir, name);
                    if (target.exists()) {
                        Toast.makeText(this, R.string.file_manager_new_folder_exists, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean ok = target.mkdir();
                    Toast.makeText(this,
                            ok ? R.string.file_manager_new_folder_success
                               : R.string.file_manager_new_folder_fail,
                            Toast.LENGTH_SHORT).show();
                    if (ok) refresh();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void refresh() {
        if (textPath != null) {
            textPath.setText(getString(R.string.file_manager_path, currentDir.getAbsolutePath()));
        }
        List<FileItem> items = new ArrayList<>();
        File[] files = currentDir.listFiles();
        if (files != null) {
            for (File f : files) {
                items.add(new FileItem(f));
            }
        }
        items.sort((a, b) -> {
            if (a.file.isDirectory() != b.file.isDirectory()) {
                return a.file.isDirectory() ? -1 : 1;
            }
            return a.file.getName().compareToIgnoreCase(b.file.getName());
        });
        if (items.isEmpty()) {
            items.add(new FileItem(null));
        }
        listFiles.setAdapter(new FileAdapter(this, items));
    }

    private void showFileActions(File file) {
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(new String[]{getString(R.string.file_manager_open), getString(R.string.file_manager_delete)},
                        (d, which) -> {
                            if (which == 0) openFile(file);
                            else confirmDelete(file);
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void openFile(File file) {
        if (file.length() > 16L * 1024L * 1024L) {
            Toast.makeText(this,
                    getString(R.string.text_editor_too_large, Formatter.formatFileSize(this, file.length())),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, TextEditorActivity.class);
        intent.putExtra(TextEditorActivity.EXTRA_PATH, file.getAbsolutePath());
        startActivity(intent);
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.file_manager_delete)
                .setMessage(getString(R.string.file_manager_delete_confirm, file.getName()))
                .setPositiveButton(R.string.dialog_ok, (d, w) -> doDelete(file))
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void doDelete(File file) {
        boolean ok = file.isDirectory() ? deleteRecursive(file) : file.delete();
        Toast.makeText(this,
                ok ? R.string.file_manager_delete_success : R.string.file_manager_delete_failed,
                Toast.LENGTH_SHORT).show();
        refresh();
    }

    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        return f.delete();
    }

    /** List row model. A null file marks the "empty directory" placeholder. */
    private static class FileItem {
        final File file;
        FileItem(File f) { this.file = f; }
    }

    /** Renders each row as "name [folder?] / size / modified date". */
    private static class FileAdapter extends ArrayAdapter<FileItem> {
        private final Context ctx;
        private final List<FileItem> items;

        FileAdapter(Context c, List<FileItem> items) {
            super(c, android.R.layout.simple_list_item_1, items);
            this.ctx = c;
            this.items = items;
        }

        @Override
        public View getView(int pos, View convertView, android.view.ViewGroup parent) {
            TextView tv = (TextView) (convertView != null ? convertView :
                    android.view.LayoutInflater.from(ctx)
                            .inflate(android.R.layout.simple_list_item_1, parent, false));
            FileItem it = items.get(pos);
            if (it.file == null) {
                tv.setText(R.string.file_manager_empty);
                return tv;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(it.file.getName());
            if (it.file.isDirectory()) sb.append("   [").append(ctx.getString(R.string.file_manager_dir_label)).append("]");
            sb.append("\n");
            if (it.file.isDirectory()) {
                sb.append(ctx.getString(R.string.file_manager_dir_label));
            } else {
                sb.append(Formatter.formatFileSize(ctx, it.file.length()));
            }
            sb.append("   ").append(
                    new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(new Date(it.file.lastModified())));
            tv.setText(sb.toString());
            return tv;
        }
    }
}
