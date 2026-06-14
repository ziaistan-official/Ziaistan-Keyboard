package juloo.keyboard2;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NextWordProbability {

    public static final String RELOAD_NEXT_WORD_ACTION = "juloo.keyboard2.RELOAD_NEXT_WORD";
    private static final String PROBABILITY_BASE = "next_word_prob";
    private static final String EXTERNAL_DIR_NAME = "ziaistan_keyboard_backup";
    public static final int MAX_CONTEXT_LENGTH = 3;
    private static final int MAX_TOTAL_NODES = 50000;
    private static int nodeCount = 0;

    private File probabilityFile;
    private File externalProbabilityFile;
    private String mCurrentScript = "en";

    private final Map<String, Integer> wordToId = new HashMap<>(5000);
    private final Map<Integer, String> idToWord = new HashMap<>(5000);
    private int nextId = 0;

    private static class ContextNode {
        // predictions: [nextWordId1, count1, nextWordId2, count2, ...]
        int[] predictions = null;
        int[] childIds = null;
        ContextNode[] children = null;

        void addPrediction(int nextWordId) {
            addPredictionInternal(nextWordId, -1);
        }

        void addPredictionInternal(int nextWordId, int exactCount) {
            if (predictions == null) {
                predictions = new int[]{nextWordId, exactCount == -1 ? 1 : exactCount};
                return;
            }
            for (int i = 0; i < predictions.length; i += 2) {
                if (predictions[i] == nextWordId) {
                    if (exactCount != -1) {
                        predictions[i + 1] += exactCount;
                        if (predictions[i + 1] > 9999) predictions[i + 1] = 9999;
                    } else if (predictions[i + 1] < 9999) {
                        predictions[i + 1]++;
                    }
                    return;
                }
            }
            // Limit predictions per context to 50 for efficiency
            if (predictions.length >= 100) return;

            int[] next = new int[predictions.length + 2];
            System.arraycopy(predictions, 0, next, 0, predictions.length);
            next[predictions.length] = nextWordId;
            next[predictions.length + 1] = exactCount == -1 ? 1 : exactCount;
            predictions = next;
        }

        ContextNode getOrCreateChild(int wordId) {
            ContextNode child = getChild(wordId);
            if (child != null) return child;

            // Limit branching factor to 100 for efficiency
            if (childIds != null && childIds.length >= 100) return null;

            if (childIds == null) {
                childIds = new int[]{wordId};
                children = new ContextNode[]{new ContextNode()};
                return children[0];
            }

            int[] nextIds = new int[childIds.length + 1];
            ContextNode[] nextChildren = new ContextNode[children.length + 1];
            System.arraycopy(childIds, 0, nextIds, 0, childIds.length);
            System.arraycopy(children, 0, nextChildren, 0, children.length);
            nextIds[childIds.length] = wordId;
            if (nodeCount >= MAX_TOTAL_NODES) return null;
            ContextNode newNode = new ContextNode();
            nodeCount++;
            nextChildren[children.length] = newNode;
            childIds = nextIds;
            children = nextChildren;
            return newNode;
        }

        ContextNode getChild(int wordId) {
            if (childIds == null) return null;
            for (int i = 0; i < childIds.length; i++) {
                if (childIds[i] == wordId) return children[i];
            }
            return null;
        }
    }

    private final ContextNode root = new ContextNode();
    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = this::saveProbabilitiesInternal;
    private final Context context;

    public NextWordProbability(Context context) {
        this.context = context;
        updateFiles();
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            synchronized (root) {
                loadProbabilities();
            }
        });
    }

    public void setScript(String script) {
        if (script == null) script = "en";
        if (!script.equals(mCurrentScript)) {
            mCurrentScript = script;
            updateFiles();
            reload();
        }
    }

    private void updateFiles() {
        String fileName = PROBABILITY_BASE + "_" + mCurrentScript + ".txt";
        this.probabilityFile = new File(context.getFilesDir(), fileName);
        File backupDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXTERNAL_DIR_NAME);
        if (!backupDir.exists()) backupDir.mkdirs();
        this.externalProbabilityFile = new File(backupDir, fileName);
    }

    public void reload() {
        synchronized (root) {
            wordToId.clear();
            idToWord.clear();
            nextId = 0;
            nodeCount = 0;
            root.predictions = null;
            root.childIds = null;
            root.children = null;
            loadProbabilities();
        }
    }

    public void mergeNextWordProbabilities(File otherFile) {
        if (otherFile == null || !otherFile.exists()) return;
        synchronized (root) {
            loadProbabilitiesInternal(otherFile);
        }
        saveProbabilities();
    }

    private void loadProbabilities() {
        if (!probabilityFile.exists()) return;
        loadProbabilitiesInternal(probabilityFile);
    }

    private void loadProbabilitiesInternal(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int spaceIdx = line.indexOf(' ');
                if (spaceIdx == -1) continue;

                String contextPart = line.substring(0, spaceIdx);
                String[] contextWords = contextPart.split(",");

                ContextNode node = root;
                for (String w : contextWords) {
                    node = node.getOrCreateChild(getOrCreateId(w));
                    if (node == null) break;
                }

                if (node == null) continue;

                String[] predictionsParts = line.substring(spaceIdx + 1).split(" ");
                for (String part : predictionsParts) {
                    int colonIdx = part.lastIndexOf(':');
                    if (colonIdx == -1) continue;
                    String nextWord = part.substring(0, colonIdx);
                    int count = Integer.parseInt(part.substring(colonIdx + 1));

                    int nextWordId = getOrCreateId(nextWord);
                    node.addPredictionInternal(nextWordId, count);
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private void saveProbabilitiesInternal() {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            saveToFile(probabilityFile);
            saveToFile(externalProbabilityFile);
            DriveSyncHelper.syncFileToDrive(context, externalProbabilityFile, "text/plain");
        });
    }

    private void saveToFile(File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            synchronized (root) {
                saveNode(writer, root, "");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveNode(BufferedWriter writer, ContextNode node, String prefix) throws IOException {
        if (node.predictions != null && node.predictions.length > 0) {
            writer.write(prefix.substring(0, prefix.length() - 1));
            for (int i = 0; i < node.predictions.length; i += 2) {
                String nextWord = idToWord.get(node.predictions[i]);
                if (nextWord != null) {
                    writer.write(" ");
                    writer.write(nextWord);
                    writer.write(":");
                    writer.write(String.valueOf(node.predictions[i + 1]));
                }
            }
            writer.newLine();
        }
        if (node.childIds != null) {
            for (int i = 0; i < node.childIds.length; i++) {
                String word = idToWord.get(node.childIds[i]);
                if (word != null) {
                    saveNode(writer, node.children[i], prefix + word + ",");
                }
            }
        }
    }

    private int getOrCreateId(String word) {
        Integer id = wordToId.get(word);
        if (id != null) return id;
        int newId = nextId++;
        wordToId.put(word, newId);
        idToWord.put(newId, word);
        return newId;
    }

    public void learnFromText(String text) {
        if (text == null || text.isEmpty()) return;
        learnFromList(tokenize(text));
    }

    private boolean isUrdu(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return true;
        }
        return false;
    }

    private boolean isLatin(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }

    public void learnFromList(List<String> words) {
        if (words == null || words.size() < 2) return;

        synchronized (root) {
            for (int i = 0; i < words.size() - 1; i++) {
                String nextWord = words.get(i + 1);
                boolean nextIsUrdu = isUrdu(nextWord);
                boolean nextIsLatin = isLatin(nextWord);

                int nextWordId = getOrCreateId(nextWord);
                // Explicitly learn 1-gram, 2-gram, 3-gram in order
                for (int len = 1; len <= MAX_CONTEXT_LENGTH; len++) {
                    int start = i - len + 1;
                    if (start >= 0) {
                        // Ensure all words in sequence match the script of nextWord
                        boolean sequenceMatch = true;
                        for (int k = start; k <= i; k++) {
                            String w = words.get(k);
                            if (nextIsUrdu && !isUrdu(w)) { sequenceMatch = false; break; }
                            if (nextIsLatin && !isLatin(w)) { sequenceMatch = false; break; }
                        }
                        if (!sequenceMatch) continue;

                        ContextNode node = root;
                        for (int j = start; j <= i; j++) {
                            node = node.getOrCreateChild(getOrCreateId(words.get(j)));
                            if (node == null) break;
                        }
                        if (node != null) node.addPrediction(nextWordId);
                    }
                }
            }
        }
        saveProbabilities();
    }

    public static List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();
        List<String> words = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Utils.isWordPart(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (sb.length() > 0) {
                words.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) words.add(sb.toString());
        return words;
    }

    private void learnSequence(List<String> words, int start, int end, String nextWord) {
        ContextNode node = root;
        for (int i = start; i <= end; i++) {
            node = node.getOrCreateChild(getOrCreateId(words.get(i)));
            if (node == null) return;
        }
        node.addPrediction(getOrCreateId(nextWord));
    }

    public void trackWordSequence(String previousWord, String currentWord) {
        if (previousWord == null || currentWord == null || previousWord.isEmpty() || currentWord.isEmpty()) return;
        List<String> prevWords = tokenize(previousWord);
        List<String> currWords = tokenize(currentWord);

        List<String> history = new ArrayList<>(prevWords);
        synchronized (root) {
            for (String w : currWords) {
                int wId = getOrCreateId(w);
                boolean isUrdu = isUrdu(w);
                boolean isLatin = isLatin(w);

                // Explicitly learn 1-gram, 2-gram, 3-gram in order
                for (int len = 1; len <= MAX_CONTEXT_LENGTH; len++) {
                    if (history.size() >= len) {
                        // Language consistency check
                        boolean sequenceMatch = true;
                        for (int i = history.size() - len; i < history.size(); i++) {
                            String hWord = history.get(i);
                            if (isUrdu && !isUrdu(hWord)) { sequenceMatch = false; break; }
                            if (isLatin && !isLatin(hWord)) { sequenceMatch = false; break; }
                        }
                        if (!sequenceMatch) continue;

                        ContextNode node = root;
                        for (int i = history.size() - len; i < history.size(); i++) {
                            node = node.getOrCreateChild(getOrCreateId(history.get(i)));
                            if (node == null) break;
                        }
                        if (node != null) node.addPrediction(wId);
                    }
                }
                history.add(w);
                if (history.size() > MAX_CONTEXT_LENGTH) history.remove(0);
            }
        }
        saveProbabilities();
    }

    public void saveProbabilities() {
        saveHandler.removeCallbacks(saveRunnable);
        saveHandler.postDelayed(saveRunnable, 2000);
    }

    public static class WeightedResult {
        public final String word;
        public final int weight;
        public WeightedResult(String word, int weight) {
            this.word = word;
            this.weight = weight;
        }
    }

    public List<WeightedResult> getNextWordSuggestionsWeighted(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<String> words = tokenize(text);
        if (words.isEmpty()) return Collections.emptyList();

        List<WeightedResult> allSuggestions = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        synchronized (root) {
            // Pre-calculate IDs for faster lookup
            int[] wordIds = new int[words.size()];
            for (int i = 0; i < words.size(); i++) {
                Integer id = wordToId.get(words.get(i));
                wordIds[i] = (id != null) ? id : -1;
            }

            // Strict priority fallback: Collect only from the longest matching context
            for (int len = Math.min(words.size(), MAX_CONTEXT_LENGTH); len >= 1; len--) {
                ContextNode node = root;
                boolean match = true;
                for (int i = words.size() - len; i < words.size(); i++) {
                    int id = wordIds[i];
                    if (id == -1 || (node = node.getChild(id)) == null) {
                        match = false;
                        break;
                    }
                }
                if (match && node.predictions != null && node.predictions.length > 0) {
                    sortAndAddTo(node.predictions, allSuggestions, seen, len);
                    if (allSuggestions.size() >= 100) break;
                }
            }
        }
        return allSuggestions;
    }

    public boolean containsWord(String word) {
        if (word == null) return false;
        synchronized (root) {
            return wordToId.containsKey(word.toLowerCase());
        }
    }

    public List<String> getPrefixMatches(String prefix, int limit) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        String lower = prefix.toLowerCase();
        List<String> results = new ArrayList<>();
        synchronized (root) {
            for (String word : wordToId.keySet()) {
                if (word.startsWith(lower)) {
                    results.add(word);
                    if (results.size() >= limit) break;
                }
            }
        }
        return results;
    }

    public List<String> getNextWordSuggestions(String text) {
        List<WeightedResult> weighted = getNextWordSuggestionsWeighted(text);
        List<String> results = new ArrayList<>(weighted.size());
        for (WeightedResult wr : weighted) results.add(wr.word);
        return results;
    }

    private void sortAndAddTo(int[] predictions, List<WeightedResult> target, java.util.Set<String> seen, int weight) {
        int size = predictions.length / 2;
        int[] ids = new int[size];
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            ids[i] = predictions[i * 2];
            values[i] = predictions[i * 2 + 1];
        }
        // Shell sort for maximum efficiency and zero object allocation during sort
        for (int gap = size / 2; gap > 0; gap /= 2) {
            for (int i = gap; i < size; i++) {
                int tempVal = values[i];
                int tempId = ids[i];
                int j;
                for (j = i; j >= gap && values[j - gap] < tempVal; j -= gap) {
                    values[j] = values[j - gap];
                    ids[j] = ids[j - gap];
                }
                values[j] = tempVal;
                ids[j] = tempId;
            }
        }
        for (int i = 0; i < size; i++) {
            String w = idToWord.get(ids[i]);
            if (w != null && seen.add(w)) {
                target.add(new WeightedResult(w, weight));
            }
            if (target.size() >= 100) break;
        }
    }
}
