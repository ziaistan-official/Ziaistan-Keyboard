package juloo.keyboard2;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TermuxCommandsController {

    private final Context context;
    private final KeyEventHandler.IReceiver receiver;
    private Dialog dialog;
    private CommandsAdapter adapter;
    private List<CommandItem> allItems = new ArrayList<>();

    public TermuxCommandsController(Context context, KeyEventHandler.IReceiver receiver) {
        this.context = context;
        this.receiver = receiver;
    }

    public static class CommandItem {
        public String name;
        public String command;
        public String tag;

        public CommandItem(String name, String command, String tag) {
            this.name = name;
            this.command = command;
            this.tag = tag;
        }
    }

    public void showCommandsDialog() {
        loadCommands();

        Context themeContext = new android.view.ContextThemeWrapper(context, Config.globalConfig().theme);
        dialog = new Dialog(context);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(themeContext).inflate(R.layout.dialog_termux_commands, null);
        dialog.setContentView(view);

        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.6);
        params.gravity = Gravity.BOTTOM;
        params.dimAmount = 0.5f;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.getWindow().setAttributes(params);

        EditText searchInput = view.findViewById(R.id.search_input);
        RecyclerView recyclerView = view.findViewById(R.id.commands_recycler);
        ImageButton btnClose = view.findViewById(R.id.btn_close);

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new CommandsAdapter();
        recyclerView.setAdapter(adapter);
        adapter.setItems(allItems);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filter(s.toString());
            }
        });

        dialog.show();
    }

    private void loadCommands() {
        allItems.clear();

        File backupDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "ziaistan_keyboard_backup");
        if (!backupDir.exists()) backupDir.mkdirs();
        File backupFile = new File(backupDir, "termux-commands.json");
        File oldFile = new File(context.getFilesDir(), "termux_commands.json");

        File file = backupFile;
        if (!backupFile.exists()) {
            if (oldFile.exists()) {
                file = oldFile;
            } else {
                createDefaultCommandsFile(backupFile);
            }
        }

        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String jsonStr = sb.toString().trim();
            if (jsonStr.startsWith("\ufeff")) {
                jsonStr = jsonStr.substring(1).trim();
            }
            if (jsonStr.startsWith("{")) {
                JSONObject root = new JSONObject(jsonStr);

                if (root.has("commands") && root.get("commands") instanceof JSONArray) {
                    JSONArray arr = root.getJSONArray("commands");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        allItems.add(new CommandItem(obj.getString("name"), obj.getString("command"), obj.optString("tag", "General")));
                    }
                } else {
                    Iterator<String> keys = root.keys();
                    while (keys.hasNext()) {
                        String tag = keys.next();
                        Object val = root.get(tag);
                        if (val instanceof JSONArray) {
                            JSONArray arr = (JSONArray) val;
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                allItems.add(new CommandItem(obj.getString("name"), obj.getString("command"), tag));
                            }
                        } else if (val instanceof JSONObject) {
                            JSONObject obj = (JSONObject) val;
                            if (obj.has("command")) {
                                allItems.add(new CommandItem(obj.optString("name", tag), obj.getString("command"), obj.optString("tag", "General")));
                            } else {

                                Iterator<String> innerKeys = obj.keys();
                                while (innerKeys.hasNext()) {
                                    String cmdName = innerKeys.next();
                                    Object innerVal = obj.get(cmdName);
                                    if (innerVal instanceof String) {
                                        allItems.add(new CommandItem(cmdName, (String) innerVal, tag));
                                    }
                                }
                            }
                        } else if (val instanceof String) {

                            allItems.add(new CommandItem(tag, (String) val, "General"));
                        }
                    }
                }
            } else if (jsonStr.startsWith("[")) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    allItems.add(new CommandItem(
                        obj.getString("name"),
                        obj.getString("command"),
                        obj.optString("tag", "General")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            android.util.Log.e("TermuxCommands", "JSON Error: " + e.getMessage());
            Toast.makeText(context, "Failed to load commands: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void createDefaultCommandsFile(File file) {
        try {
            JSONObject root = new JSONObject();
            root.put("_comment", "AI Prompt: To add more commands, insert objects into the 'commands' array. Each object should have 'name', 'command', and optionally 'tag'. Users can categorize commands using tags for better grouping.");

            JSONArray arr = new JSONArray();
            String[][] defaults = {
                {"Update", "pkg update", "System"}, {"Upgrade", "pkg upgrade", "System"}, {"Install Git", "pkg install git", "System"},
                {"Install Node", "pkg install nodejs", "System"}, {"Install Python", "pkg install python", "System"},
                {"List Files", "ls -la", "Files"}, {"Long List", "ls -lR", "Files"}, {"Disk Free", "df -h", "Files"},
                {"Memory Info", "free -m", "System"}, {"Processes", "top", "System"}, {"Network Stat", "netstat -tuln", "Network"},
                {"IP Address", "ip a", "Network"}, {"Whoami", "whoami", "General"}, {"Uptime", "uptime", "General"},
                {"Clear", "clear", "General"}, {"Exit", "exit", "General"}, {"Ping Google", "ping -c 4 google.com", "Network"},
                {"Install Vim", "pkg install vim", "System"}, {"Install Emacs", "pkg install emacs", "System"},
                {"Install Nano", "pkg install nano", "System"}, {"SSH User", "ssh ", "Network"}, {"SCP File", "scp ", "Network"},
                {"CURL Info", "curl -I ", "Network"}, {"Wget Download", "wget ", "Network"}, {"Tar Compress", "tar -cvzf ", "Files"},
                {"Tar Extract", "tar -xvzf ", "Files"}, {"Zip Folder", "zip -r ", "Files"}, {"Unzip File", "unzip ", "Files"},
                {"Find File", "find . -name ", "Files"}, {"Grep Search", "grep -r ", "General"}, {"Tail Logs", "tail -f ", "General"},
                {"Cat File", "cat ", "General"}, {"Chmod +x", "chmod +x ", "Files"}, {"History", "history", "General"},
                {"Date", "date", "General"}, {"Calendar", "cal", "General"}, {"Weather", "curl wttr.in", "Network"},
                {"Termux Info", "termux-info", "Termux"}, {"Open URL", "termux-open ", "Termux"}, {"Vibrate", "termux-vibrate", "Termux"},
                {"Battery Status", "termux-battery-status", "Termux"}, {"Clipboard Set", "termux-clipboard-set ", "Termux"},
                {"Clipboard Get", "termux-clipboard-get", "Termux"}, {"Toast Message", "termux-toast ", "Termux"},
                {"Contact List", "termux-contact-list", "Termux"}, {"SMS Send", "termux-sms-send ", "Termux"},
                {"Call Number", "termux-telephony-call ", "Termux"}, {"Volume Set", "termux-volume ", "Termux"},
                {"Camera Info", "termux-camera-info", "Termux"}, {"Microphone Record", "termux-microphone-record", "Termux"},
                {"Download Manager", "termux-download ", "Termux"}, {"Fingerprint", "termux-fingerprint", "Termux"},
                {"Location", "termux-location", "Termux"}, {"Notification", "termux-notification ", "Termux"},
                {"Sensor", "termux-sensor -l", "Termux"}, {"TTS Speak", "termux-tts-speak ", "Termux"},
                {"WIFI Connection", "termux-wifi-connectioninfo", "Termux"}, {"WIFI Scan", "termux-wifi-scaninfo", "Termux"},
                {"Setup Storage", "termux-setup-storage", "Termux"}, {"Install Root", "pkg install root-repo", "System"},
                {"Install X11", "pkg install x11-repo", "System"}, {"Start SSH", "sshd", "Network"}, {"Stop SSH", "pkill sshd", "Network"},
                {"Install Go", "pkg install golang", "System"}, {"Install Rust", "pkg install rust", "System"},
                {"Install Ruby", "pkg install ruby", "System"}, {"Install Perl", "pkg install perl", "System"},
                {"Install PHP", "pkg install php", "System"}, {"Python Server", "python -m http.server", "Dev"},
                {"Node Version", "node -v", "Dev"}, {"NPM Install", "npm install ", "Dev"}, {"Pip Install", "pip install ", "Dev"},
                {"Git Clone", "git clone ", "Dev"}, {"Git Status", "git status", "Dev"}, {"Git Add", "git add .", "Dev"},
                {"Git Commit", "git commit -m \"\"", "Dev"}, {"Git Push", "git push", "Dev"}, {"Git Pull", "git pull", "Dev"},
                {"Docker Info", "docker info", "Dev"}, {"HTOP", "htop", "System"}, {"NCurses Disk Usage", "ncdu", "Files"},
                {"Tree View", "tree", "Files"}, {"Word Count", "wc -l", "General"}, {"Sort Unique", "sort -u", "General"},
                {"Environment", "env", "System"}, {"Path Info", "echo $PATH", "System"}, {"Alias", "alias", "General"},
                {"Disk Usage Summary", "du -sh *", "Files"}, {"Disk Free Human", "df -h", "Files"}, {"Mount Info", "mount", "System"},
                {"Dmesg", "dmesg", "System"}, {"Logcat", "logcat", "System"}, {"PM List Packages", "pm list packages", "System"},
                {"Am Start", "am start ", "System"}, {"Screencap", "screencap ", "Files"}, {"Screenrecord", "screenrecord ", "Files"},
                {"Input Tap", "input tap ", "System"}, {"Input Text", "input text ", "System"}, {"Reboot", "reboot", "System"}
            };

            for (String[] d : defaults) {
                JSONObject o = new JSONObject();
                o.put("name", d[0]);
                o.put("command", d[1]);
                o.put("tag", d[2]);
                arr.put(o);
            }
            root.put("commands", arr);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(root.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void filter(String query) {
        List<CommandItem> filtered = new ArrayList<>();
        String q = query.toLowerCase();
        for (CommandItem item : allItems) {
            if (item.name.toLowerCase().contains(q) || item.command.toLowerCase().contains(q) || item.tag.toLowerCase().contains(q)) {
                filtered.add(item);
            }
        }
        adapter.setItems(filtered);
    }

    private class CommandsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Object> items = new ArrayList<>();

        void setItems(List<CommandItem> commandItems) {
            this.items.clear();
            java.util.TreeMap<String, List<CommandItem>> grouped = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (CommandItem item : commandItems) {
                String tag = (item.tag == null || item.tag.trim().isEmpty()) ? "General" : item.tag.trim();
                if (!grouped.containsKey(tag)) grouped.put(tag, new ArrayList<>());
                grouped.get(tag).add(item);
            }

            for (Map.Entry<String, List<CommandItem>> entry : grouped.entrySet()) {
                items.add(entry.getKey());
                items.addAll(entry.getValue());
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof String ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
                return new TagViewHolder(v);
            } else {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_termux_command, parent, false);
                return new CommandViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof TagViewHolder) {
                ((TagViewHolder) holder).text.setText((String) items.get(position));
                ((TagViewHolder) holder).text.setTextSize(14);
                ((TagViewHolder) holder).text.setAlpha(0.7f);
            } else {
                CommandItem item = (CommandItem) items.get(position);
                CommandViewHolder h = (CommandViewHolder) holder;
                h.name.setText(item.name);
                h.command.setText(item.command);
                h.itemView.setOnClickListener(v -> {
                    if (context instanceof Keyboard2) {
                        ((Keyboard2) context).setPendingCommitText(item.command);
                    }
                    dialog.dismiss();
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class TagViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            TagViewHolder(View v) { super(v); text = v.findViewById(android.R.id.text1); }
        }

        class CommandViewHolder extends RecyclerView.ViewHolder {
            TextView name, command;
            CommandViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.command_name);
                command = v.findViewById(R.id.command_text);
            }
        }
    }
}
