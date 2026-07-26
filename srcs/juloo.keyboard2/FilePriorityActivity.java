package juloo.keyboard2;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FilePriorityActivity extends Activity {
    private RecyclerView recyclerView;
    private FilePriorityAdapter adapter;
    private List<String> files = new ArrayList<>();
    private TextView lastIndexedText;
    private static final String AUTOCOMPLETE_DIR = "/storage/emulated/0/Download/ziaistan_keyboard_backup/sentence_autocompletion";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_priority);

        recyclerView = findViewById(R.id.file_priority_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        lastIndexedText = new TextView(this);
        lastIndexedText.setPadding(0, 8, 0, 8);
        ((ViewGroup)recyclerView.getParent()).addView(lastIndexedText, 1);
        updateLastIndexedUI();

        loadFiles();

        adapter = new FilePriorityAdapter();
        recyclerView.setAdapter(adapter);

        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                Collections.swap(files, fromPos, toPos);
                adapter.notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        };
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        Button saveButton = findViewById(R.id.save_priority_button);
        saveButton.setOnClickListener(v -> savePriorityAndIndex());
    }

    private void updateLastIndexedUI() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        long lastTime = prefs.getLong("last_indexed_time", 0);
        if (lastTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            lastIndexedText.setText("Last Indexed: " + sdf.format(new Date(lastTime)));
        } else {
            lastIndexedText.setText("Last Indexed: Never");
        }
    }

    private void loadFiles() {
        File dir = new File(AUTOCOMPLETE_DIR);
        List<String> foundFiles = new ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            File[] list = dir.listFiles((d, name) -> name.endsWith(".txt"));
            if (list != null) {
                for (File f : list) foundFiles.add(f.getName());
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String priorityStr = prefs.getString("clipboard_autocomplete_file_priority", "");
        if (!priorityStr.isEmpty()) {
            List<String> savedPriority = new ArrayList<>(Arrays.asList(priorityStr.split(",")));
            for (String s : savedPriority) {
                if (foundFiles.contains(s)) {
                    files.add(s);
                    foundFiles.remove(s);
                }
            }
        }
        files.addAll(foundFiles);
    }

    private void savePriorityAndIndex() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(files.get(i));
        }
        String priorityStr = sb.toString();
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString("clipboard_autocomplete_file_priority", priorityStr)
                .apply();

        Config.globalConfig().clipboard_autocomplete_file_priority = priorityStr;

        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Indexing Files");
        pd.setMessage("Starting...");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setCancelable(false);
        pd.show();

        IndexingService.getInstance(this).startIndexing(new IndexingService.ProgressListener() {
            @Override
            public void onProgress(int current, int total, String fileName) {
                runOnUiThread(() -> {
                    pd.setMax(total);
                    pd.setProgress(current);
                    pd.setMessage("Indexing: " + fileName);
                });
            }

            @Override
            public void onFinished() {
                runOnUiThread(() -> {
                    pd.dismiss();
                    updateLastIndexedUI();
                    Toast.makeText(FilePriorityActivity.this, "Indexing Complete", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private class FilePriorityAdapter extends RecyclerView.Adapter<FilePriorityAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_priority, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.fileNameText.setText(files.get(position));
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView fileNameText;
            ImageView dragHandle;

            ViewHolder(View view) {
                super(view);
                fileNameText = view.findViewById(R.id.file_name_text);
                dragHandle = view.findViewById(R.id.drag_handle);
            }
        }
    }
}
