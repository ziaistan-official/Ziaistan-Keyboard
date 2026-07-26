package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IndexingService {
    private static final String TAG = "IndexingService";
    private static final String BACKUP_PATH = "/storage/emulated/0/Download/ziaistan_keyboard_backup";
    private static final String AUTOCOMPLETE_DIR = BACKUP_PATH + "/sentence_autocompletion";
    private static final String INDEX_FILE = BACKUP_PATH + "/ziaistan_index.bin";

    private static IndexingService _instance;
    private final Context context;
    private final TrieNode root = new TrieNode();
    private boolean isIndexing = false;

    public interface ProgressListener {
        void onProgress(int current, int total, String fileName);
        void onFinished();
    }

    public static IndexingService getInstance(Context context) {
        if (_instance == null) {
            _instance = new IndexingService(context.getApplicationContext());
        }
        return _instance;
    }

    private IndexingService(Context context) {
        this.context = context;
        new Thread(this::loadIndex).start();
    }

    public static class TrieNode {
        public final Map<Character, TrieNode> children = new ConcurrentHashMap<>();
        public volatile boolean isEndOfSentence = false;
        public final Set<String> sourceFiles = Collections.synchronizedSet(new HashSet<>());
    }

    public boolean isIndexing() {
        return isIndexing;
    }

    public void startIndexing() {
        startIndexing(null);
    }

    public void startIndexing(ProgressListener listener) {
        if (isIndexing) return;
        isIndexing = true;
        new Thread(() -> {
            try {
                File dir = new File(AUTOCOMPLETE_DIR);
                if (!dir.exists()) {
                    dir.mkdirs();
                    if (listener != null) listener.onFinished();
                    isIndexing = false;
                    return;
                }

                List<File> txtFiles = new ArrayList<>();
                findTxtFiles(dir, txtFiles);

                int total = txtFiles.size();
                for (int i = 0; i < total; i++) {
                    File f = txtFiles.get(i);
                    if (listener != null) listener.onProgress(i + 1, total, f.getName());
                    processFile(f);
                }

                saveIndex();

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                prefs.edit().putLong("last_indexed_time", System.currentTimeMillis()).apply();

                if (listener != null) listener.onFinished();
            } catch (Exception e) {
                Log.e(TAG, "Indexing failed", e);
                if (listener != null) listener.onFinished();
            } finally {
                isIndexing = false;
            }
        }).start();
    }

    private void findTxtFiles(File dir, List<File> results) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                findTxtFiles(f, results);
            } else if (f.getName().endsWith(".txt")) {
                results.add(f);
            }
        }
    }

    private void processFile(File file) {
        try (Scanner scanner = new Scanner(new FileInputStream(file), "UTF-8")) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line != null && !line.trim().isEmpty()) {
                    addSentence(line.trim(), file.getName());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error processing file: " + file.getAbsolutePath(), e);
        }
    }

    private void addSentence(String sentence, String fileName) {
        TrieNode current = root;
        String filtered = filterText(sentence);
        if (filtered.isEmpty()) return;

        for (char c : filtered.toCharArray()) {
            TrieNode next = current.children.get(c);
            if (next == null) {
                next = new TrieNode();
                TrieNode old = current.children.putIfAbsent(c, next);
                if (old != null) next = old;
            }
            current = next;
        }
        current.isEndOfSentence = true;
        current.sourceFiles.add(fileName);
    }

    private String filterText(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '\u0600' && c <= '\u06FF') || c == ' ' ||
                ".,()[]".indexOf(c) != -1) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void saveIndex() {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(INDEX_FILE), "UTF-8")))) {
            saveNode(root, "", writer);
        } catch (IOException e) {
            Log.e(TAG, "Error saving index", e);
        }
    }

    private void saveNode(TrieNode node, String prefix, PrintWriter writer) {
        if (node.isEndOfSentence) {
            writer.print(prefix);
            writer.print("|");
            synchronized (node.sourceFiles) {
                boolean first = true;
                for (String s : node.sourceFiles) {
                    if (!first) writer.print(",");
                    writer.print(s);
                    first = false;
                }
                writer.println();
            }
        }
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            saveNode(entry.getValue(), prefix + entry.getKey(), writer);
        }
    }

    private void loadIndex() {
        File file = new File(INDEX_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int sep = line.lastIndexOf('|');
                if (sep != -1) {
                    String sentence = line.substring(0, sep);
                    String[] files = line.substring(sep + 1).split(",");
                    for (String f : files) {
                        addSentence(sentence, f);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading index", e);
        }
    }

    public List<CompletionResult> getCompletions(String prefix) {
        List<CompletionResult> results = new ArrayList<>();
        TrieNode current = root;
        String filteredPrefix = filterText(prefix);
        for (char c : filteredPrefix.toCharArray()) {
            current = current.children.get(c);
            if (current == null) return results;
        }
        collectCompletions(current, filteredPrefix, results);
        return results;
    }

    private void collectCompletions(TrieNode node, String prefix, List<CompletionResult> results) {
        if (results.size() >= 100) return; // Increased limit for better matching
        if (node.isEndOfSentence) {
            synchronized (node.sourceFiles) {
                for (String f : node.sourceFiles) {
                    results.add(new CompletionResult(prefix, f));
                }
            }
        }
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            collectCompletions(entry.getValue(), prefix + entry.getKey(), results);
            if (results.size() >= 100) return;
        }
    }

    public static class CompletionResult {
        public String text;
        public String sourceFile;

        public CompletionResult(String text, String sourceFile) {
            this.text = text;
            this.sourceFile = sourceFile;
        }
    }
}
