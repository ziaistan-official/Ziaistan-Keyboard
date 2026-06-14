package juloo.keyboard2;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import juloo.keyboard2.prefs.LayoutsPreference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SuggestionProvider {

    private static final int MAX_SUGGESTIONS = 10000;
    private static final String CUSTOM_DICTIONARY_BASE = "custom";
    private static final String BACKUP_PATH = "/storage/emulated/0/Download/ziaistan_keyboard_backup";
    private static final String FILTERS_BASE = "suggestion_filters";
    private static final String TYPED_BASE = "typed";
    public static final String RELOAD_FILTERS_ACTION = "juloo.keyboard2.RELOAD_FILTERS";

    public static final String SEARCH_PREFIX = "prefix";
    public static final String SEARCH_KEYBOARD_AWARE = "keyboard_aware";
    public static final String SEARCH_DELETION = "deletion";
    public static final String SEARCH_INSERTION = "insertion";
    public static final String SEARCH_SUBSTITUTION = "substitution";
    public static final String SEARCH_TRANSPOSITION = "transposition";
    public static final String SEARCH_DOUBLING = "doubling";
    public static final String SEARCH_SINGLING = "singling";

    public static class TrieNode {
        public char[] keys;
        public TrieNode[] children;
        public boolean isEndOfWord;

        public TrieNode() {
            this.keys = new char[0];
            this.children = new TrieNode[0];
        }

        public synchronized TrieNode getChild(char c) {
            final int len = keys.length;
            for (int i = 0; i < len; i++) {
                if (keys[i] == c) {
                    return children[i];
                }
            }
            return null;
        }

        public synchronized TrieNode addChild(char c) {
            final int len = keys.length;
            for (int i = 0; i < len; i++) {
                if (keys[i] == c) {
                    return children[i];
                }
            }

            char[] newKeys = new char[len + 1];
            TrieNode[] newChildren = new TrieNode[len + 1];
            if (len > 0) {
                System.arraycopy(keys, 0, newKeys, 0, len);
                System.arraycopy(children, 0, newChildren, 0, len);
            }

            newKeys[len] = c;
            TrieNode newNode = new TrieNode();
            newChildren[len] = newNode;

            this.keys = newKeys;
            this.children = newChildren;
            return newNode;
        }
    }

    public final TrieNode customRoot;
    public final TrieNode commonRoot;
    public final TrieNode wordlistRoot;
    private final Map<String, TrieNode> extraTries = new ConcurrentHashMap<>();
    private final Context context;
    public final NextWordProbability nextWordProbability;
    private String mCurrentScript = "en";

    public volatile boolean commonLoaded = false;
    private volatile boolean wordlistLoaded = false;
    private String currentWordlistName = null;
    private volatile boolean useCommonDictionary = true;


    public static final String FEATURE_PREFIX = "prefix";
    public static final String FEATURE_AUTOCORRECT = "autocorrect";
    public static final String FEATURE_KEYBOARD_AWARE = "keyboard_aware";
    public static final String FEATURE_KEYBOARD_AWARE_PREFIX = "keyboard_aware_prefix";
    public static final String FEATURE_NEXT_WORD = "next_word";
    public static final String FEATURE_PREFIX_NEXT_WORD = "prefix_next_word";
    public static final String FEATURE_TUTORIAL = "tutorial";

    public enum SuggestionMode {
        NONE,
        PREFIX,
        NEXT_WORD,
        AUTO_CORRECTION
    }

    public static class Suggestion {
        public final String word;
        public final String source;
        public int contextWeight = 0;
        public WordSource wordSource = WordSource.NONE;
        public int rank = 0;

        public Suggestion(String word, String source) {
            this.word = word;
            this.source = source;
        }

        public Suggestion(String word, String source, int contextWeight) {
            this.word = word;
            this.source = source;
            this.contextWeight = contextWeight;
        }

        public Suggestion(String word, String source, int contextWeight, WordSource wordSource) {
            this.word = word;
            this.source = source;
            this.contextWeight = contextWeight;
            this.wordSource = wordSource;
        }

        public Suggestion(String word, String source, int contextWeight, WordSource wordSource, int rank) {
            this.word = word;
            this.source = source;
            this.contextWeight = contextWeight;
            this.wordSource = wordSource;
            this.rank = rank;
        }

        @Override
        public String toString() {
            return word;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Suggestion that = (Suggestion) o;
            return contextWeight == that.contextWeight &&
                    rank == that.rank &&
                    word.equals(that.word) &&
                    source.equals(that.source) &&
                    wordSource == that.wordSource;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(word, source, contextWeight, wordSource, rank);
        }
    }

    public final Map<String, Set<String>> featureBlacklisted = new ConcurrentHashMap<>();
    public final Map<String, Set<String>> featureDeprioritized = new ConcurrentHashMap<>();
    public final Map<String, List<String>> featurePromoted = new ConcurrentHashMap<>();

    public final Map<String, Set<String>> contextBlacklisted = new ConcurrentHashMap<>();
    public final Map<String, Set<String>> contextDeprioritized = new ConcurrentHashMap<>();
    public final Map<String, List<String>> contextPromoted = new ConcurrentHashMap<>();

    public final List<String> typedWords = Collections.synchronizedList(new ArrayList<>());
    public final Set<String> fieldWords = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SuggestionProvider(Context context) {
        this.context = context;
        customRoot = new TrieNode();
        commonRoot = new TrieNode();
        wordlistRoot = new TrieNode();
        nextWordProbability = new NextWordProbability(context);

        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            // Wait for Config to be initialized if called from Keyboard2.onCreate
            for (int i=0; i<10; i++) {
                if (Config.globalConfig() != null) break;
                try { Thread.sleep(100); } catch (Exception e) {}
            }
            migrateLegacyData();
            migrateLegacyLayouts();
            reloadAllData();
        });

        setWordlist(null);
    }

    public void setScript(String script) {
        String newScript = "en";
        if (script != null) {
            if (script.equalsIgnoreCase("urdu") || script.equalsIgnoreCase("ur")) {
                newScript = "ur";
            }
        }
        if (!newScript.equals(mCurrentScript)) {
            mCurrentScript = newScript;
            nextWordProbability.setScript(newScript);
            reloadAllData();
        }
    }

    private void reloadAllData() {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            synchronized (customRoot) {
                customRoot.keys = new char[0];
                customRoot.children = new TrieNode[0];
                customRoot.isEndOfWord = false;
                loadCustomDictionary(customRoot);
            }
            loadFilters();
            loadTypedWords();
            reloadCommonDictionary();
            reloadExtraTries();
        });
    }

    private void reloadExtraTries() {
        String sourcePriority = Config.globalConfig().suggestion_source_priority;
        if (sourcePriority == null) return;
        String[] sources = sourcePriority.split(",");
        for (String s : sources) {
            s = s.trim();
            if (isReservedSource(s)) continue;

            TrieNode root = new TrieNode();
            loadCustomDictionary(root, s);
            extraTries.put(s, root);
        }
    }

    private boolean isReservedSource(String s) {
        return s.equals("typed") || s.equals("filters") || s.equals("next_word") ||
               s.equals("custom") || s.equals("common") || s.equals("wordlist") ||
               s.equals("suggestion_filters") || s.equals("next_word_prob");
    }

    private void reloadCommonDictionary() {
        commonLoaded = false;
        synchronized (commonRoot) {
            commonRoot.keys = new char[0];
            commonRoot.children = new TrieNode[0];
            commonRoot.isEndOfWord = false;
        }
        int resId = mCurrentScript.equals("ur") ? R.raw.common_ur : R.raw.common_en;
        loadDictionary(resId, commonRoot);
        commonLoaded = true;
    }

    public void setWordlist(String wordlistName) {

        final String targetList = wordlistName != null ? wordlistName : "wordlist_en";

        if (targetList.equals(currentWordlistName)) return;

        currentWordlistName = targetList;
        setScript(targetList.endsWith("_ur") ? "ur" : "en");

        useCommonDictionary = "wordlist_en".equals(targetList);
        wordlistLoaded = false;


        synchronized (wordlistRoot) {
            wordlistRoot.keys = new char[0];
            wordlistRoot.children = new TrieNode[0];
            wordlistRoot.isEndOfWord = false;
        }

        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {

            if (!targetList.equals(currentWordlistName)) return;

            int resourceId = context.getResources().getIdentifier(targetList, "raw", context.getPackageName());
            if (resourceId != 0) {
                synchronized (wordlistRoot) {

                     if (!targetList.equals(currentWordlistName)) return;







                    wordlistRoot.keys = new char[0];
                    wordlistRoot.children = new TrieNode[0];
                    wordlistRoot.isEndOfWord = false;

                    loadDictionary(resourceId, wordlistRoot);
                }
                if (targetList.equals(currentWordlistName)) {
                    wordlistLoaded = true;
                }
            }
        });
    }

    public void reloadCustomDictionary() {
        synchronized (customRoot) {
            customRoot.keys = new char[0];
            customRoot.children = new TrieNode[0];
            customRoot.isEndOfWord = false;
            loadCustomDictionary(customRoot);
        }
    }

    private void loadCustomDictionary(TrieNode root) {
        loadCustomDictionary(root, CUSTOM_DICTIONARY_BASE);
    }

    private void loadCustomDictionary(TrieNode root, String baseName) {
        String fileName = baseName + "_" + mCurrentScript + ".txt";
        File customDictFile = new File(context.getFilesDir(), fileName);
        if (!customDictFile.exists()) {
            customDictFile = new File(BACKUP_PATH, fileName);
        }
        // Also try without script suffix if requested file is generic
        if (!customDictFile.exists()) {
            customDictFile = new File(BACKUP_PATH, baseName + ".txt");
        }

        if (!customDictFile.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(customDictFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                insert(line.trim(), root);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadDictionary(int resourceId, TrieNode root) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getResources().openRawResource(resourceId)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                insert(line.trim(), root);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void insert(String word, TrieNode root) {
        TrieNode current = root;
        final int len = word.length();
        for (int i = 0; i < len; i++) {
            current = current.addChild(word.charAt(i));
        }
        current.isEndOfWord = true;
    }

    public void blacklistWord(String word, String feature, String contextWord) {
        String lower = word.toLowerCase();
        if (contextWord != null) {
            String ctx = contextWord.toLowerCase();
            contextBlacklisted.computeIfAbsent(ctx, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(lower);
            removeFromContext(ctx, contextPromoted, lower);
            Set<String> deprio = contextDeprioritized.get(ctx);
            if (deprio != null) deprio.remove(lower);
        } else if (feature != null) {
            featureBlacklisted.computeIfAbsent(feature, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(lower);
            removeFromFeature(feature, featurePromoted, lower);
            Set<String> deprio = featureDeprioritized.get(feature);
            if (deprio != null) deprio.remove(lower);
        }
        typedWords.remove(lower);
        saveFilters();
    }

    public void deprioritizeWord(String word, String feature, String contextWord) {
        String lower = word.toLowerCase();
        if (contextWord != null) {
            String ctx = contextWord.toLowerCase();
            contextDeprioritized.computeIfAbsent(ctx, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(lower);
            removeFromContext(ctx, contextPromoted, lower);
            Set<String> black = contextBlacklisted.get(ctx);
            if (black != null) black.remove(lower);
        } else if (feature != null) {
            featureDeprioritized.computeIfAbsent(feature, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(lower);
            removeFromFeature(feature, featurePromoted, lower);
            Set<String> black = featureBlacklisted.get(feature);
            if (black != null) black.remove(lower);
        }
        typedWords.remove(lower);
        saveFilters();
    }

    public void promoteWord(String word, String feature, String contextWord) {
        String lower = word.toLowerCase();
        if (contextWord != null) {
            String ctx = contextWord.toLowerCase();
            List<String> list = contextPromoted.computeIfAbsent(ctx, k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (list) {
                list.remove(lower);
                list.add(0, lower);
            }
            Set<String> black = contextBlacklisted.get(ctx);
            if (black != null) black.remove(lower);
            Set<String> deprio = contextDeprioritized.get(ctx);
            if (deprio != null) deprio.remove(lower);
        } else if (feature != null) {
            List<String> list = featurePromoted.computeIfAbsent(feature, k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (list) {
                list.remove(lower);
                list.add(0, lower);
            }
            Set<String> black = featureBlacklisted.get(feature);
            if (black != null) black.remove(lower);
            Set<String> deprio = featureDeprioritized.get(feature);
            if (deprio != null) deprio.remove(lower);
        }
        typedWords.remove(lower);
        saveFilters();
    }

    private void removeFromFeature(String feature, Map<String, List<String>> map, String word) {
        List<String> list = map.get(feature);
        if (list != null) {
            list.remove(word);
        }
    }

    private void removeFromContext(String ctx, Map<String, List<String>> map, String word) {
        List<String> list = map.get(ctx);
        if (list != null) {
            list.remove(word);
        }
    }

    public void recordTypedWord(String word) {
        if (word == null || word.length() <= 1) return;
        String lower = word.toLowerCase();

        // Only record valid dictionary words
        if (!isValidWord(lower)) return;

        // Check if blacklisted in any feature
        for (Set<String> black : featureBlacklisted.values()) {
            if (black.contains(lower)) return;
        }

        synchronized (typedWords) {
            typedWords.remove(lower);
            typedWords.add(0, lower);
            if (typedWords.size() > 5000) {
                typedWords.remove(typedWords.size() - 1);
            }
        }
        saveTypedWords();
    }

    public void reloadFilters() {
        loadFilters();
    }

    public void reloadNextWordProbability() {
        nextWordProbability.reload();
    }

    private void loadTypedWords() {
        typedWords.clear();
        File typedFile = new File(context.getFilesDir(), TYPED_BASE + "_" + mCurrentScript + ".txt");
        if (!typedFile.exists()) {
            typedFile = new File(BACKUP_PATH, TYPED_BASE + "_" + mCurrentScript + ".txt");
        }
        if (!typedFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(typedFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!word.isEmpty()) {
                    typedWords.add(word);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadFilters() {
        featureBlacklisted.clear();
        featureDeprioritized.clear();
        featurePromoted.clear();
        contextBlacklisted.clear();
        contextDeprioritized.clear();
        contextPromoted.clear();

        // Favor internal storage for app logic
        String fileName = FILTERS_BASE + "_" + mCurrentScript + ".json";
        File filterFile = new File(context.getFilesDir(), fileName);
        if (!filterFile.exists()) {
            // Fallback to backup location
            filterFile = new File(BACKUP_PATH, fileName);
        }

        if (!filterFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(filterFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject json = new JSONObject(sb.toString());
            loadContextMap(json, "feature_blacklist", featureBlacklisted, true);
            loadContextMap(json, "feature_deprioritized", featureDeprioritized, true);
            loadContextMap(json, "feature_promoted", featurePromoted, false);

            loadContextMap(json, "context_blacklist", contextBlacklisted, true);
            loadContextMap(json, "context_deprioritized", contextDeprioritized, true);
            loadContextMap(json, "context_promoted", contextPromoted, false);

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void loadArray(JSONObject json, String key, Collection<String> target) {
        JSONArray arr = json.optJSONArray(key);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                try {
                    target.add(arr.getString(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void loadContextMap(JSONObject json, String key, Map target, boolean isSet) {
        JSONObject obj = json.optJSONObject(key);
        if (obj != null) {
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String ctx = keys.next();
                JSONArray arr = obj.optJSONArray(ctx);
                if (arr != null) {
                    Collection<String> col = isSet ? Collections.newSetFromMap(new ConcurrentHashMap<>()) : Collections.synchronizedList(new ArrayList<>());
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            col.add(arr.getString(i));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    target.put(ctx, col);
                }
            }
        }
    }

    private void saveTypedWords() {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            String fileName = TYPED_BASE + "_" + mCurrentScript + ".txt";
            File internalFile = new File(context.getFilesDir(), fileName);
            try (FileWriter writer = new FileWriter(internalFile)) {
                synchronized (typedWords) {
                    for (String word : typedWords) {
                        writer.write(word);
                        writer.write("\n");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            File backupDir = new File(BACKUP_PATH);
            if (!backupDir.exists()) backupDir.mkdirs();
            File backupFile = new File(backupDir, fileName);
            try (FileWriter writer = new FileWriter(backupFile)) {
                synchronized (typedWords) {
                    for (String word : typedWords) {
                        writer.write(word);
                        writer.write("\n");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            DriveSyncHelper.syncFileToDrive(context, backupFile, "text/plain");
        });
    }

    private void saveFilters() {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("feature_blacklist", contextMapToJson(featureBlacklisted));
                json.put("feature_deprioritized", contextMapToJson(featureDeprioritized));
                json.put("feature_promoted", contextMapToJson(featurePromoted));

                json.put("context_blacklist", contextMapToJson(contextBlacklisted));
                json.put("context_deprioritized", contextMapToJson(contextDeprioritized));
                json.put("context_promoted", contextMapToJson(contextPromoted));

                // Always write to internal storage for app logic
                String fileName = FILTERS_BASE + "_" + mCurrentScript + ".json";
                File internalFile = new File(context.getFilesDir(), fileName);
                try (FileWriter writer = new FileWriter(internalFile)) {
                    writer.write(json.toString());
                }

                // Write to external storage for real-time backup
                File backupDir = new File(BACKUP_PATH);
                if (!backupDir.exists()) {
                    backupDir.mkdirs();
                }

                File filterFile = new File(backupDir, fileName);
                try (FileWriter writer = new FileWriter(filterFile)) {
                    writer.write(json.toString());
                }

                // Trigger Drive Sync
                DriveSyncHelper.syncFileToDrive(context, filterFile, "application/json");

            } catch (JSONException | IOException e) {
                e.printStackTrace();
            }
        });
    }

    interface SuggestionConsumer {
        boolean accept(String word);
    }

    public List<Suggestion> getSuggestions(String prefix) {
        return getSuggestions(prefix, MAX_SUGGESTIONS, null);
    }

    public List<Suggestion> getSuggestions(String prefix, int limit, CancellationSignal signal) {
        return getSuggestions(prefix, limit, signal, "typed,filters,next_word,custom,common,wordlist");
    }

    public List<Suggestion> getSuggestions(String prefix, int limit, CancellationSignal signal, String sourcePriority) {
        return getSuggestions(prefix, null, limit, signal, sourcePriority);
    }

    public List<Suggestion> getSuggestions(String prefix, String context, int limit, CancellationSignal signal, String sourcePriority) {
        return getSuggestions(prefix, context, limit, signal, sourcePriority, Config.globalConfig().suggestion_search_priority);
    }

    public List<Suggestion> getSuggestions(String prefix, String context, int limit, CancellationSignal signal, String sourcePriority, String searchPriority) {
        return getSuggestions(prefix, context, limit, signal, sourcePriority, searchPriority, null, null);
    }

    public List<Suggestion> getSuggestions(String prefix, String context, int limit, CancellationSignal signal, String sourcePriority, String searchPriority, LayoutBasedAutoCorrectionProvider correctionProvider, KeyboardAwareSuggester awareSuggester) {
        List<Suggestion> suggestions = new ArrayList<>();
        if (prefix == null) prefix = "";

        final String prefixLower = prefix.toLowerCase();
        final boolean isUrdu = Utils.isUrdu(prefixLower);
        Set<String> seen = new HashSet<>();
        List<Suggestion> deprioritizedCandidates = new ArrayList<>();

        if (searchPriority == null || searchPriority.trim().isEmpty()) {
            searchPriority = "prefix,keyboard_aware,deletion,insertion,substitution,transposition,doubling,singling";
        }
        if (sourcePriority == null || sourcePriority.trim().isEmpty()) {
            sourcePriority = "typed,filters,next_word,custom,common,wordlist";
        }

        String[] searchTypes = searchPriority.split(",");
        String[] sources = sourcePriority.split(",");

        for (String searchType : searchTypes) {
            searchType = searchType.trim();
            if (suggestions.size() >= limit || (signal != null && signal.isCancelled())) break;

            // Pre-calculate candidates for search types that are not source-specific Prefix search
            List<String> candidates = null;
            if (!searchType.equals(SEARCH_PREFIX)) {
                if (searchType.equals(SEARCH_KEYBOARD_AWARE)) {
                    if (awareSuggester != null) candidates = awareSuggester.suggestPrefix(prefix, limit, signal);
                } else if (correctionProvider != null) {
                    switch (searchType) {
                        case SEARCH_DELETION: candidates = correctionProvider.getDeletionCandidates(prefix); break;
                        case SEARCH_INSERTION: candidates = correctionProvider.getInsertionCandidates(prefix); break;
                        case SEARCH_SUBSTITUTION: candidates = correctionProvider.getSubstitutionCandidates(prefix); break;
                        case SEARCH_TRANSPOSITION: candidates = correctionProvider.getTranspositionCandidates(prefix); break;
                        case SEARCH_DOUBLING: candidates = correctionProvider.getDoublingCandidates(prefix); break;
                        case SEARCH_SINGLING: candidates = correctionProvider.getSinglingCandidates(prefix); break;
                    }
                }
            }

            for (String source : sources) {
                source = source.trim();
                if (suggestions.size() >= limit || (signal != null && signal.isCancelled())) break;

                List<Suggestion> batch = new ArrayList<>();
                if (searchType.equals(SEARCH_PREFIX)) {
                    if (prefixLower.isEmpty()) {
                        batch = getNextWordSuggestionsForSource(context, source, limit, signal);
                    } else {
                        batch = getPrefixSuggestionsForSource(prefixLower, context, source, limit, isUrdu, signal);
                    }
                } else if (candidates != null && !prefixLower.isEmpty()) {
                    batch = filterCandidatesBySource(candidates, source, searchType);
                }

                for (Suggestion s : batch) {
                    String lowerWord = s.word.toLowerCase();
                    if (seen.contains(lowerWord)) continue;

                    if (isBlacklisted(s.word, searchType, context)) {
                        seen.add(lowerWord);
                        continue;
                    }

                    if (isDeprioritized(s.word, searchType, context)) {
                        deprioritizedCandidates.add(s);
                        seen.add(lowerWord);
                    } else {
                        suggestions.add(s);
                        seen.add(lowerWord);
                    }
                    if (suggestions.size() >= limit) break;
                }
            }
        }

        for (Suggestion s : deprioritizedCandidates) {
            if (suggestions.size() >= limit || (signal != null && signal.isCancelled())) break;
            suggestions.add(s);
        }

        return suggestions;
    }

    private List<Suggestion> getNextWordSuggestionsForSource(String context, String source, int limit, CancellationSignal signal) {
        List<Suggestion> results = new ArrayList<>();
        if (source.equals("suggestion_filters")) source = "filters";
        if (source.equals("next_word_prob")) source = "next_word";

        // Limit fallbacks to 20 to allow next_word predictions to remain visible
        int fallbackLimit = Math.min(limit, 20);

        switch (source) {
            case "typed":
                synchronized (typedWords) {
                    int rank = 0;
                    for (String word : typedWords) {
                        if (signal != null && signal.isCancelled()) break;
                        results.add(new Suggestion(word, "typed", 0, WordSource.TYPED, rank++));
                        if (results.size() >= fallbackLimit) break;
                    }
                }
                break;
            case "filters":
                List<String> promoted = featurePromoted.get(FEATURE_NEXT_WORD);
                if (promoted != null) {
                    synchronized (promoted) {
                        int rank = 0;
                        for (String word : promoted) {
                            if (signal != null && signal.isCancelled()) break;
                            results.add(new Suggestion(word, "filters", 0, WordSource.FILTERS, rank++));
                            if (results.size() >= fallbackLimit) break;
                        }
                    }
                }
                break;
            case "next_word":
                if (context != null) {
                    List<NextWordProbability.WeightedResult> predicted = nextWordProbability.getNextWordSuggestionsWeighted(context);
                    int rank = 0;
                    for (NextWordProbability.WeightedResult r : predicted) {
                        if (signal != null && signal.isCancelled()) break;
                        results.add(new Suggestion(r.word, FEATURE_NEXT_WORD, r.weight, getWordSource(r.word), rank++));
                        if (results.size() >= limit) break;
                    }
                }
                break;
            case "custom":
                break;
            case "field":
                int fRank = 0;
                for (String word : fieldWords) {
                    if (signal != null && signal.isCancelled()) break;
                    results.add(new Suggestion(word, "field", 0, WordSource.FIELD, fRank++));
                    if (results.size() >= fallbackLimit) break;
                }
                break;
        }
        return results;
    }

    private List<Suggestion> getPrefixSuggestionsForSource(String prefixLower, String context, String source, int limit, boolean isUrdu, CancellationSignal signal) {
        List<Suggestion> results = new ArrayList<>();
        // Handle alias
        if (source.equals("suggestion_filters")) source = "filters";
        if (source.equals("next_word_prob")) source = "next_word";

        switch (source) {
            case "typed":
                synchronized (typedWords) {
                    int rank = 0;
                    for (String word : typedWords) {
                        if (signal != null && signal.isCancelled()) break;
                        if (Utils.urduStartsWith(word, prefixLower)) {
                            results.add(new Suggestion(word, "typed", 0, WordSource.TYPED, rank++));
                        }
                        if (results.size() >= limit) break;
                    }
                }
                break;
            case "filters":
                List<String> promoted = featurePromoted.get(FEATURE_PREFIX);
                if (promoted != null) {
                    synchronized (promoted) {
                        int rank = 0;
                        for (String word : promoted) {
                            if (signal != null && signal.isCancelled()) break;
                            if (Utils.urduStartsWith(word, prefixLower)) {
                                results.add(new Suggestion(word, "filters", 0, WordSource.FILTERS, rank++));
                            }
                            if (results.size() >= limit) break;
                        }
                    }
                }
                break;
            case "next_word":
                // Strictly only predicted next words starting with prefix
                Set<String> nwSeen = new HashSet<>();
                if (context != null) {
                    List<NextWordProbability.WeightedResult> predicted = nextWordProbability.getNextWordSuggestionsWeighted(context);
                    int nwRank = 0;
                    for (NextWordProbability.WeightedResult r : predicted) {
                        if (signal != null && signal.isCancelled()) break;
                        if (Utils.urduStartsWith(r.word, prefixLower)) {
                            results.add(new Suggestion(r.word, "next_word", r.weight, getWordSource(r.word), nwRank++));
                            nwSeen.add(r.word.toLowerCase());
                        }
                        if (results.size() >= limit) break;
                    }
                }
                // Fallback to all learned words matching prefix if we have room
                if (results.size() < limit) {
                    List<String> allLearned = nextWordProbability.getPrefixMatches(prefixLower, limit);
                    for (String w : allLearned) {
                        if (signal != null && signal.isCancelled()) break;
                        if (!nwSeen.contains(w.toLowerCase())) {
                            if (Utils.urduStartsWith(w, prefixLower)) {
                                results.add(new Suggestion(w, "next_word", 0, WordSource.NEXT_WORD));
                            }
                        }
                        if (results.size() >= limit) break;
                    }
                }
                break;
            case "custom":
                Map<TrieNode, String> customNodes = findPrefixNodes(prefixLower, customRoot, isUrdu, signal);
                for (Map.Entry<TrieNode, String> entry : customNodes.entrySet()) {
                    if (results.size() >= limit || (signal != null && signal.isCancelled())) break;
                    findAllWords(entry.getKey(), entry.getValue(), (word) -> {
                        if (signal != null && signal.isCancelled()) return false;
                        results.add(new Suggestion(word, "custom", 0, WordSource.CUSTOM));
                        return results.size() < limit;
                    }, signal);
                }
                break;
            case "common":
                if (commonLoaded) {
                    Map<TrieNode, String> commonNodes = findPrefixNodes(prefixLower, commonRoot, isUrdu, signal);
                    for (Map.Entry<TrieNode, String> entry : commonNodes.entrySet()) {
                        if (results.size() >= limit || (signal != null && signal.isCancelled())) break;
                        findAllWords(entry.getKey(), entry.getValue(), (word) -> {
                            if (signal != null && signal.isCancelled()) return false;
                            results.add(new Suggestion(word, "common", 0, WordSource.COMMON));
                            return results.size() < limit;
                        }, signal);
                    }
                }
                break;
            case "wordlist":
                if (wordlistLoaded) {
                    synchronized (wordlistRoot) {
                        Map<TrieNode, String> wordlistNodes = findPrefixNodes(prefixLower, wordlistRoot, isUrdu, signal);
                        for (Map.Entry<TrieNode, String> entry : wordlistNodes.entrySet()) {
                            if (results.size() >= limit || (signal != null && signal.isCancelled())) break;
                            findAllWords(entry.getKey(), entry.getValue(), (word) -> {
                                if (signal != null && signal.isCancelled()) return false;
                                results.add(new Suggestion(word, "wordlist", 0, WordSource.WORDLIST));
                                return results.size() < limit;
                            }, signal);
                        }
                    }
                }
                break;
            default:
                // Handle dynamic files
                TrieNode dynamicRoot = extraTries.get(source);
                if (dynamicRoot != null) {
                    Map<TrieNode, String> nodes = findPrefixNodes(prefixLower, dynamicRoot, isUrdu, signal);
                    for (Map.Entry<TrieNode, String> entry : nodes.entrySet()) {
                        if (results.size() >= limit || (signal != null && signal.isCancelled())) break;
                        final String sourceName = source;
                        findAllWords(entry.getKey(), entry.getValue(), (word) -> {
                            if (signal != null && signal.isCancelled()) return false;
                            results.add(new Suggestion(word, sourceName, 0, WordSource.CUSTOM));
                            return results.size() < limit;
                        }, signal);
                    }
                }
                break;
        }
        return results;
    }

    private List<Suggestion> filterCandidatesBySource(List<String> candidates, String source, String searchType) {
        List<Suggestion> results = new ArrayList<>();
        for (String cand : candidates) {
            String lower = cand.toLowerCase();
            WordSource sourceOfCand = getWordSource(lower);
            boolean sourceMatches = false;

            String checkSource = source;
            if (checkSource.equals("suggestion_filters")) checkSource = "filters";
            if (checkSource.equals("next_word_prob")) checkSource = "next_word";

            switch (checkSource) {
                case "typed":
                    synchronized (typedWords) { if (typedWords.contains(lower)) sourceMatches = true; }
                    break;
                case "filters":
                    List<String> promoted = featurePromoted.get(FEATURE_PREFIX);
                    if (promoted != null && promoted.contains(lower)) sourceMatches = true;
                    break;
                case "next_word":
                    if (sourceOfCand == WordSource.NEXT_WORD) sourceMatches = true;
                    break;
                case "custom":
                    if (sourceOfCand == WordSource.CUSTOM) sourceMatches = true;
                    break;
                case "common":
                    if (sourceOfCand == WordSource.COMMON) sourceMatches = true;
                    break;
                case "wordlist":
                    if (sourceOfCand == WordSource.WORDLIST) sourceMatches = true;
                    break;
                default:
                    TrieNode dynamicRoot = extraTries.get(checkSource);
                    if (dynamicRoot != null) {
                         if (findPrefixNode(lower, dynamicRoot) != null) sourceMatches = true;
                    }
                    break;
            }

            if (sourceMatches) {
                results.add(new Suggestion(cand, searchType, 0, sourceOfCand));
            }
        }
        return results;
    }

    private boolean isBlacklisted(String word, String feature, String contextWord) {
        String lower = word.toLowerCase();
        Set<String> black = featureBlacklisted.get(feature);
        if (black != null && black.contains(lower)) return true;
        if (contextWord != null) {
            Set<String> cBlack = contextBlacklisted.get(contextWord.toLowerCase());
            if (cBlack != null && cBlack.contains(lower)) return true;
        }
        return false;
    }

    private boolean isDeprioritized(String word, String feature, String contextWord) {
        String lower = word.toLowerCase();
        Set<String> deprio = featureDeprioritized.get(feature);
        if (deprio != null && deprio.contains(lower)) return true;
        if (contextWord != null) {
            Set<String> cDeprio = contextDeprioritized.get(contextWord.toLowerCase());
            if (cDeprio != null && cDeprio.contains(lower)) return true;
        }
        return false;
    }

    private Map<TrieNode, String> findPrefixNodes(String prefix, TrieNode root, boolean isUrdu, CancellationSignal signal) {
        Map<TrieNode, String> results = new HashMap<>();
        if (isUrdu) {
            collectUrduPrefixNodes(root, prefix, 0, "", results, signal);
        } else {
            TrieNode node = findPrefixNode(prefix, root);
            if (node != null) results.put(node, prefix);
        }
        return results;
    }

    private static void collectUrduPrefixNodes(TrieNode node, String prefix, int idx, String path, Map<TrieNode, String> results, CancellationSignal signal) {
        if (signal != null && signal.isCancelled()) return;

        // Skip leading diacritics in prefix if they don't match (user error)
        while (idx < prefix.length() && Utils.isUrduDiacritic(prefix.charAt(idx))) {
            TrieNode child = node.getChild(prefix.charAt(idx));
            if (child != null) {
                node = child;
                path += prefix.charAt(idx);
                idx++;
            } else {
                idx++; // Ignore unmatched diacritic
            }
        }

        if (idx == prefix.length()) {
            results.put(node, path);
            // Explore variations in Trie with trailing diacritics
            if (node.keys != null) {
                for (int i = 0; i < node.keys.length; i++) {
                    if (Utils.isUrduDiacritic(node.keys[i])) {
                        collectUrduPrefixNodes(node.children[i], prefix, idx, path + node.keys[i], results, signal);
                    }
                }
            }
            return;
        }

        char c = prefix.charAt(idx);
        if (node.keys != null) {
            for (int i = 0; i < node.keys.length; i++) {
                char key = node.keys[i];
                if (Utils.isUrduDiacritic(key)) {
                    // Skip diacritic in Trie (Trie has it, user didn't type it)
                    collectUrduPrefixNodes(node.children[i], prefix, idx, path + key, results, signal);
                } else if (Character.toLowerCase(key) == c) {
                    // Match base character
                    collectUrduPrefixNodes(node.children[i], prefix, idx + 1, path + key, results, signal);
                }
            }
        }
    }

    private TrieNode findPrefixNode(String prefix, TrieNode root) {
        TrieNode current = root;
        final int len = prefix.length();
        for (int i = 0; i < len; i++) {
            char ch = Character.toLowerCase(prefix.charAt(i));
            current = current.getChild(ch);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    public boolean findAllWords(TrieNode node, String prefix, SuggestionConsumer consumer, CancellationSignal signal) {
        char[] buffer = new char[100];
        int depth = 0;
        if (prefix != null) {
            depth = prefix.length();
            prefix.getChars(0, depth, buffer, 0);
        }
        return findAllWordsConsumerRecursive(node, buffer, depth, consumer, signal);
    }

    private boolean findAllWordsConsumerRecursive(TrieNode node, char[] buffer, int depth, SuggestionConsumer consumer, CancellationSignal signal) {
        if (signal != null && signal.isCancelled()) return false;
        if (depth >= buffer.length) return true;

        final char[] k;
        final TrieNode[] ch;
        final boolean isEnd;

        synchronized (node) {
            k = node.keys;
            ch = node.children;
            isEnd = node.isEndOfWord;
        }

        if (isEnd) {
            if (!consumer.accept(new String(buffer, 0, depth))) {
                return false;
            }
        }

        if (k != null) {
            final int childCount = k.length;
            for (int i = 0; i < childCount; i++) {
                buffer[depth] = k[i];
                if (!findAllWordsConsumerRecursive(ch[i], buffer, depth + 1, consumer, signal)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void findAllWords(TrieNode node, String prefix, List<String> suggestions) {
        findAllWords(node, prefix, suggestions, MAX_SUGGESTIONS);
    }

    public void findAllWords(TrieNode node, String prefix, List<String> suggestions, int limit) {
        findAllWords(node, prefix, suggestions, limit, null);
    }

    public void findAllWords(TrieNode node, String prefix, List<String> suggestions, int limit, CancellationSignal signal) {
        char[] buffer = new char[100];
        int depth = 0;
        if (prefix != null) {
            depth = prefix.length();
            prefix.getChars(0, depth, buffer, 0);
        }
        findAllWordsRecursive(node, buffer, depth, suggestions, limit, signal);
    }

    private void findAllWordsRecursive(TrieNode node, char[] buffer, int depth, List<String> suggestions, int limit, CancellationSignal signal) {
        if (signal != null && signal.isCancelled()) return;
        if (suggestions.size() >= limit || depth >= buffer.length) {
            return;
        }

        final char[] k;
        final TrieNode[] ch;
        final boolean isEnd;

        synchronized (node) {
            k = node.keys;
            ch = node.children;
            isEnd = node.isEndOfWord;
        }

        if (isEnd) {
            String word = new String(buffer, 0, depth);
            if (!suggestions.contains(word)) {
                suggestions.add(word);
            }
        }
        if (k != null) {
            final int childCount = k.length;
            for (int i = 0; i < childCount; i++) {
                buffer[depth] = k[i];
                findAllWordsRecursive(ch[i], buffer, depth + 1, suggestions, limit, signal);
                if (suggestions.size() >= limit) {
                    return;
                }
            }
        }
    }

    public enum WordSource { TYPED, FILTERS, NEXT_WORD, CUSTOM, COMMON, WORDLIST, FIELD, NONE }

    private WordSource getDictionarySource(String word) {
        if (word == null || word.isEmpty()) {
            return WordSource.NONE;
        }

        synchronized (customRoot) {
            TrieNode customNode = findPrefixNode(word, customRoot);
            if (customNode != null && customNode.isEndOfWord) {
                return WordSource.CUSTOM;
            }
        }

        if (useCommonDictionary && commonLoaded) {
            synchronized (commonRoot) {
                TrieNode commonNode = findPrefixNode(word, commonRoot);
                if (commonNode != null && commonNode.isEndOfWord) {
                    return WordSource.COMMON;
                }
            }
        }

        if (wordlistLoaded) {
            synchronized (wordlistRoot) {
                TrieNode wordlistNode = findPrefixNode(word, wordlistRoot);
                if (wordlistNode != null && wordlistNode.isEndOfWord) {
                    return WordSource.WORDLIST;
                }
            }
        }

        return WordSource.NONE;
    }

    public WordSource getWordSource(String word) {
        if (word == null || word.isEmpty()) {
            return WordSource.NONE;
        }

        String lower = word.toLowerCase();

        // Check Typed
        synchronized (typedWords) {
            if (typedWords.contains(lower)) return WordSource.TYPED;
        }

        // Check Promoted (Filters)
        List<String> promoted = featurePromoted.get(FEATURE_PREFIX);
        if (promoted != null && promoted.contains(lower)) return WordSource.FILTERS;

        if (fieldWords != null && fieldWords.contains(word)) {
            return WordSource.FIELD;
        }

        if (nextWordProbability.containsWord(lower)) {
            return WordSource.NEXT_WORD;
        }

        for (Map.Entry<String, TrieNode> entry : extraTries.entrySet()) {
            TrieNode node = findPrefixNode(lower, entry.getValue());
            if (node != null && node.isEndOfWord) return WordSource.CUSTOM;
        }

        return getDictionarySource(word);
    }

    public boolean isValidWord(String word) {
        return getDictionarySource(word) != WordSource.NONE;
    }

    public boolean isValidWordForAutoCorrect(String word) {
        return getWordSource(word) != WordSource.NONE;
    }

    public List<Suggestion> getNextWordSuggestions(String currentWord) {
        return getNextWordSuggestions(currentWord, null);
    }

    public List<Suggestion> getNextWordSuggestions(String currentWord, String prefix) {
        String ctx = currentWord != null ? currentWord.toLowerCase() : null;
        List<Suggestion> suggestions = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String lowerPrefix = (prefix != null) ? prefix.toLowerCase() : null;

        // 1. Predicted next words from probability model (High Weight)
        if (ctx != null) {
            List<NextWordProbability.WeightedResult> predicted = nextWordProbability.getNextWordSuggestionsWeighted(ctx);
            int nextWordRank = 0;
            for (NextWordProbability.WeightedResult result : predicted) {
                String word = result.word;
                if (lowerPrefix != null && !word.toLowerCase().startsWith(lowerPrefix)) continue;

                if (isBlacklisted(word, FEATURE_NEXT_WORD, ctx)) continue;

                if (seen.add(word.toLowerCase())) {
                    suggestions.add(new Suggestion(word, FEATURE_NEXT_WORD, result.weight, getWordSource(word), nextWordRank++));
                }
                if (suggestions.size() >= MAX_SUGGESTIONS) break;
            }
        }

        // 2. Fallback: Recently typed words (Broadening)
        synchronized (typedWords) {
            for (String word : typedWords) {
                if (lowerPrefix != null && !word.toLowerCase().startsWith(lowerPrefix)) continue;
                if (seen.add(word.toLowerCase())) {
                    suggestions.add(new Suggestion(word, "typed", 0, WordSource.TYPED));
                }
                if (suggestions.size() >= MAX_SUGGESTIONS) break;
            }
        }

        // 3. Fallback: Field words
        for (String word : fieldWords) {
            if (lowerPrefix != null && !word.toLowerCase().startsWith(lowerPrefix)) continue;
            if (seen.add(word.toLowerCase())) {
                suggestions.add(new Suggestion(word, "field", 0, WordSource.FIELD));
            }
            if (suggestions.size() >= MAX_SUGGESTIONS) break;
        }

        return suggestions;
    }

    public void trackWordSequence(String previousWord, String currentWord) {
        nextWordProbability.trackWordSequence(previousWord, currentWord);
    }

    public void mergeCustomDictionary(File otherFile) {
        if (otherFile == null || !otherFile.exists()) return;
        Set<String> words = new HashSet<>();
        File customFile = new File(context.getFilesDir(), CUSTOM_DICTIONARY_BASE + "_" + mCurrentScript + ".txt");
        if (customFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(customFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String w = line.trim();
                    if (!w.isEmpty()) words.add(w);
                }
            } catch (IOException e) {}
        }

        boolean changed = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(otherFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String w = line.trim();
                if (!w.isEmpty() && words.add(w)) changed = true;
            }
        } catch (IOException e) {}

        if (changed) {
            try (FileWriter writer = new FileWriter(customFile)) {
                for (String w : words) {
                    writer.write(w);
                    writer.write("\n");
                }
            } catch (IOException e) {}
            reloadCustomDictionary();
        }
    }

    public void mergeFilters(File otherFile) {
        if (otherFile == null || !otherFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(otherFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject otherJson = new JSONObject(sb.toString());

            mergeArray(otherJson, "typed", typedWords);
            mergeContextMap(otherJson, "feature_blacklist", featureBlacklisted, true);
            mergeContextMap(otherJson, "feature_deprioritized", featureDeprioritized, true);
            mergeContextMap(otherJson, "feature_promoted", featurePromoted, false);
            mergeContextMap(otherJson, "context_blacklist", contextBlacklisted, true);
            mergeContextMap(otherJson, "context_deprioritized", contextDeprioritized, true);
            mergeContextMap(otherJson, "context_promoted", contextPromoted, false);

            saveFilters();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mergeArray(JSONObject json, String key, Collection<String> target) {
        JSONArray arr = json.optJSONArray(key);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                try {
                    String val = arr.getString(i);
                    if (target instanceof List) {
                        if (!target.contains(val)) target.add(val);
                    } else {
                        target.add(val);
                    }
                } catch (JSONException e) {}
            }
        }
    }

    private void mergeContextMap(JSONObject json, String key, Map target, boolean isSet) {
        JSONObject obj = json.optJSONObject(key);
        if (obj != null) {
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String ctx = keys.next();
                JSONArray arr = obj.optJSONArray(ctx);
                if (arr != null) {
                    Collection<String> col = (Collection<String>) target.get(ctx);
                    if (col == null) {
                        col = isSet ? Collections.newSetFromMap(new ConcurrentHashMap<>()) : Collections.synchronizedList(new ArrayList<>());
                        target.put(ctx, col);
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            String val = arr.getString(i);
                            if (col instanceof List) {
                                if (!col.contains(val)) col.add(val);
                            } else {
                                col.add(val);
                            }
                        } catch (JSONException e) {}
                    }
                }
            }
        }
    }

    public void learnFromText(String text) {
        if (text == null || text.isEmpty()) return;
        List<String> words = NextWordProbability.tokenize(text);
        List<String> validWords = new ArrayList<>();
        for (String w : words) {
            if (isValidWord(w)) {
                validWords.add(w);
            }
        }
        if (validWords.size() < 2) return;

        // Re-join valid words to learn from them, or modify learnFromText to take a list
        nextWordProbability.learnFromList(validWords);
    }

    public void updateFieldWords(String text) {
        updateFieldWords(text, null);
    }

    public void updateFieldWords(String text, String excludeWord) {
        if (text == null || text.isEmpty()) {
            fieldWords.clear();
            return;
        }
        final String exclude = (excludeWord != null) ? excludeWord.toLowerCase() : null;
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            List<String> words = NextWordProbability.tokenize(text);
            Set<String> newFieldWords = new HashSet<>();
            for (String w : words) {
                if (w.length() > 1 && !w.equals(exclude)) {
                    newFieldWords.add(w);
                }
            }
            fieldWords.clear();
            fieldWords.addAll(newFieldWords);
        });
    }

    private JSONObject contextMapToJson(Map<String, ? extends Collection<String>> map) throws JSONException {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, ? extends Collection<String>> entry : map.entrySet()) {
            Collection<String> col = entry.getValue();
            synchronized (col) {
                obj.put(entry.getKey(), new JSONArray(new ArrayList<>(col)));
            }
        }
        return obj;
    }

    public String getTutorial() {
        return context.getString(R.string.next_word_tutorial);
    }

    private void migrateLegacyLayouts() {
        android.content.SharedPreferences prefs = Config.globalPrefs();
        List<LayoutsPreference.Layout> layouts = LayoutsPreference.load_layouts_from_preferences(prefs);
        boolean changed = false;
        for (int i = 0; i < layouts.size(); i++) {
            LayoutsPreference.Layout l = layouts.get(i);
            if (l instanceof LayoutsPreference.CustomLayout) {
                LayoutsPreference.CustomLayout cl = (LayoutsPreference.CustomLayout) l;
                if (!cl.xml.contains("<ziaistan_custom_layout")) {
                    // Force update to new format by re-serializing
                    String unifiedXml = cl.parsed != null ? KeyboardData.serialize_to_unified_xml(cl.parsed) : cl.xml;
                    layouts.set(i, new LayoutsPreference.CustomLayout(unifiedXml, cl.parsed));
                    changed = true;
                }
            }
        }
        if (changed) {
            LayoutsPreference.save_to_preferences(prefs.edit(), layouts);
        }
    }

    private void migrateLegacyData() {
        File internalDir = context.getFilesDir();
        migrateFile(new File(internalDir, "custom.txt"), "custom", ".txt", false);
        migrateFile(new File(internalDir, "suggestion_filters.json"), "suggestion_filters", ".json", true);
        migrateFile(new File(internalDir, "next_word_prob.txt"), "next_word_prob", ".txt", false);
        migrateFile(new File(internalDir, "typed.txt"), "typed", ".txt", false);

        File backupDir = new File(BACKUP_PATH);
        if (backupDir.exists()) {
            migrateFile(new File(backupDir, "custom.txt"), "custom", ".txt", false);
            migrateFile(new File(backupDir, "suggestion_filters.json"), "suggestion_filters", ".json", true);
            migrateFile(new File(backupDir, "next_word_prob.txt"), "next_word_prob", ".txt", false);
            migrateFile(new File(backupDir, "typed.txt"), "typed", ".txt", false);
        }
    }

    private void migrateFile(File legacyFile, String baseName, String ext, boolean isJson) {
        if (!legacyFile.exists()) return;

        try {
            if (isJson) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(legacyFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }
                JSONObject legacyJson = new JSONObject(sb.toString());

                JSONObject urJson = new JSONObject();
                JSONObject enJson = new JSONObject();
                List<String> urTyped = new ArrayList<>();
                List<String> enTyped = new ArrayList<>();

                JSONArray typedArr = legacyJson.optJSONArray("typed");
                if (typedArr != null) {
                    for (int i = 0; i < typedArr.length(); i++) {
                        String word = typedArr.getString(i);
                        if (Utils.isUrdu(word)) urTyped.add(word);
                        else enTyped.add(word);
                    }
                }

                String[] contextKeys = {"feature_blacklist", "feature_deprioritized", "feature_promoted",
                                       "context_blacklist", "context_deprioritized", "context_promoted"};

                for (String key : contextKeys) {
                    JSONObject obj = legacyJson.optJSONObject(key);
                    if (obj != null) {
                        JSONObject urSub = new JSONObject();
                        JSONObject enSub = new JSONObject();
                        Iterator<String> it = obj.keys();
                        while (it.hasNext()) {
                            String ctx = it.next();
                            JSONArray vals = obj.optJSONArray(ctx);
                            if (vals == null) continue;

                            JSONArray urVals = new JSONArray();
                            JSONArray enVals = new JSONArray();
                            for (int i=0; i<vals.length(); i++) {
                                String v = vals.getString(i);
                                if (Utils.isUrdu(v)) urVals.put(v); else enVals.put(v);
                            }

                            if (Utils.isUrdu(ctx)) {
                                if (urVals.length() > 0 || enVals.length() > 0) urSub.put(ctx, vals);
                            } else {
                                if (urVals.length() > 0 || enVals.length() > 0) enSub.put(ctx, vals);
                            }
                        }
                        if (urSub.length() > 0) urJson.put(key, urSub);
                        if (enSub.length() > 0) enJson.put(key, enSub);
                    }
                }

                saveStringToFile(new File(legacyFile.getParentFile(), baseName + "_ur" + ext), urJson.toString());
                saveStringToFile(new File(legacyFile.getParentFile(), baseName + "_en" + ext), enJson.toString());

                if (!urTyped.isEmpty()) {
                    saveLinesToFile(new File(legacyFile.getParentFile(), "typed_ur.txt"), urTyped);
                }
                if (!enTyped.isEmpty()) {
                    saveLinesToFile(new File(legacyFile.getParentFile(), "typed_en.txt"), enTyped);
                }

            } else if (baseName.equals("next_word_prob")) {
                List<String> urLines = new ArrayList<>();
                List<String> enLines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new FileReader(legacyFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int spaceIdx = line.indexOf(' ');
                        if (spaceIdx == -1) continue;
                        String contextPart = line.substring(0, spaceIdx);
                        if (Utils.isUrdu(contextPart)) urLines.add(line);
                        else enLines.add(line);
                    }
                }
                saveLinesToFile(new File(legacyFile.getParentFile(), baseName + "_ur" + ext), urLines);
                saveLinesToFile(new File(legacyFile.getParentFile(), baseName + "_en" + ext), enLines);
            } else {
                // custom.txt or similar
                List<String> urLines = new ArrayList<>();
                List<String> enLines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new FileReader(legacyFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String word = line.trim();
                        if (Utils.isUrdu(word)) urLines.add(word);
                        else enLines.add(word);
                    }
                }
                saveLinesToFile(new File(legacyFile.getParentFile(), baseName + "_ur" + ext), urLines);
                saveLinesToFile(new File(legacyFile.getParentFile(), baseName + "_en" + ext), enLines);
            }
            File backup = new File(legacyFile.getParentFile(), legacyFile.getName() + ".bak");
            if (legacyFile.renameTo(backup)) {
                // Keep backup for now, or delete later if absolutely sure
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveStringToFile(File file, String content) throws IOException {
        if (content == null || content.equals("{}")) return;
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private void saveLinesToFile(File file, List<String> lines) throws IOException {
        if (lines.isEmpty()) return;
        try (FileWriter writer = new FileWriter(file)) {
            for (String line : lines) {
                writer.write(line);
                writer.write("\n");
            }
        }
    }
}
