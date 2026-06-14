package juloo.keyboard2;

import android.content.Context;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;

public class KeyboardAwareSuggester {


    private Map<Character, char[]> surroundings;
    private final SuggestionProvider suggestionProvider;
    private static final int MAX_WORD_LENGTH = 100;
    private static final int MAX_CANDIDATES_PER_BRANCH = 100;
    private static final int MAX_FUZZY_SUBSTITUTIONS = 2;

    private final Map<String, List<String>> prefixCache = new LinkedHashMap<String, List<String>>(20, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
            return size() > 20;
        }
    };

    public KeyboardAwareSuggester(Context context, SuggestionProvider suggestionProvider) {
        this.surroundings = loadStaticSurroundings(context);
        this.suggestionProvider = suggestionProvider;
    }

    public void updateLayout(KeyboardData keyboardData) {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            Map<Character, List<Character>> map = keyboardData.surroundings;
            if (map == null || map.isEmpty()) {
                map = KeyboardLayoutAnalyzer.getAdjacencyMap(keyboardData);
            }
            Map<Character, char[]> newSurroundings = new HashMap<>();
            for (Map.Entry<Character, List<Character>> entry : map.entrySet()) {
                char value = entry.getKey();
                List<Character> neighbors = entry.getValue();
                char[] neighborsArray = new char[neighbors.size() + 1];
                neighborsArray[0] = value;
                for (int i = 0; i < neighbors.size(); i++) {
                    neighborsArray[i + 1] = neighbors.get(i);
                }
                newSurroundings.put(value, neighborsArray);
            }
            this.surroundings = newSurroundings;
            synchronized (prefixCache) {
                prefixCache.clear();
            }
        });
    }

    private Map<Character, char[]> loadStaticSurroundings(Context context) {
        Map<Character, char[]> surroundingsMap = new HashMap<>();
        try {
            XmlPullParser parser = context.getResources().getXml(R.xml.surroundings);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "char".equals(parser.getName())) {
                    String valueStr = parser.getAttributeValue(null, "value");
                    String neighborsStr = parser.getAttributeValue(null, "neighbors");

                    if (valueStr != null && !valueStr.isEmpty() && neighborsStr != null) {
                        char value = valueStr.charAt(0);
                        char[] neighbors = new char[neighborsStr.length() + 1];
                        neighbors[0] = value;
                        for (int i = 0; i < neighborsStr.length(); i++) {
                            neighbors[i + 1] = neighborsStr.charAt(i);
                        }
                        surroundingsMap.put(value, neighbors);
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }
        return surroundingsMap;
    }

    public List<String> suggestPrefix(String prefix) {
        return suggestPrefix(prefix, MAX_CANDIDATES_PER_BRANCH * 4, null);
    }

    public List<String> suggestPrefix(String prefix, int limit, CancellationSignal signal) {
        return suggestPrefix(prefix, limit, signal, Config.globalConfig().suggestion_source_priority);
    }

    public List<String> suggestPrefix(String prefix, int limit, CancellationSignal signal, String sourcePriority) {
        if (prefix == null || prefix.isEmpty()) {
            return Collections.emptyList();
        }

        final String normalizedPrefixString = prefix.toLowerCase();

        synchronized (prefixCache) {
            List<String> cached = prefixCache.get(normalizedPrefixString);
            if (cached != null) return cached;
        }

        final char[] normalizedPrefix = normalizedPrefixString.toCharArray();

        final List<String> suggestions = Collections.synchronizedList(new ArrayList<>());
        String[] sources = sourcePriority.split(",");
        List<String> activeSources = new ArrayList<>();
        for (String s : sources) {
            String ts = s.trim();
            if (ts.equals("custom") || ts.equals("common") || ts.equals("wordlist") || ts.equals("typed")) {
                activeSources.add(ts);
            }
        }

        final CountDownLatch latch = new CountDownLatch(activeSources.size());

        for (String source : activeSources) {
            if (signal != null && signal.isCancelled()) break;
            List<String> sourceResults = new ArrayList<>();
            switch (source) {
                case "custom":
                    fullSubstitutionPrefixSearch(new char[MAX_WORD_LENGTH], 0, suggestionProvider.customRoot, normalizedPrefix, 0, 0, sourceResults, limit, signal);
                    break;
                case "common":
                    if (suggestionProvider.commonLoaded) {
                        fullSubstitutionPrefixSearch(new char[MAX_WORD_LENGTH], 0, suggestionProvider.commonRoot, normalizedPrefix, 0, 0, sourceResults, limit, signal);
                    }
                    break;
                case "wordlist":
                    fullSubstitutionPrefixSearch(new char[MAX_WORD_LENGTH], 0, suggestionProvider.wordlistRoot, normalizedPrefix, 0, 0, sourceResults, limit, signal);
                    break;
                case "typed":
                    int count = 0;
                    synchronized (suggestionProvider.typedWords) {
                        for (String word : suggestionProvider.typedWords) {
                            if (signal != null && signal.isCancelled()) break;
                            if (isFuzzyPrefixMatch(normalizedPrefix, word.toLowerCase())) {
                                sourceResults.add(word);
                                if (++count >= MAX_CANDIDATES_PER_BRANCH || sourceResults.size() >= limit) break;
                            }
                        }
                    }
                    break;
            }
            for (String s : sourceResults) {
                if (!suggestions.contains(s)) {
                    suggestions.add(s);
                }
            }
            if (suggestions.size() >= limit) break;
        }

        List<String> result = new ArrayList<>(suggestions);
        synchronized (prefixCache) {
            prefixCache.put(normalizedPrefixString, result);
        }
        return result;
    }

    private boolean isFuzzyPrefixMatch(char[] prefix, String word) {
        if (word.length() < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            char c = prefix[i];
            char w = word.charAt(i);
            if (c == w) continue;
            char[] neighbors = surroundings.get(c);
            if (neighbors == null) return false;
            boolean found = false;
            for (char n : neighbors) {
                if (n == w) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private void fullSubstitutionPrefixSearch(char[] buffer, int depth, SuggestionProvider.TrieNode node,
                                              char[] prefix, int position, int subs, Collection<String> suggestions,
                                              int limit, CancellationSignal signal) {
        if (signal != null && signal.isCancelled()) return;
        if (depth >= MAX_WORD_LENGTH || suggestions.size() >= limit) return;

        if (position == prefix.length) {
            suggestionProvider.findAllWords(node, new String(buffer, 0, depth), (List<String>) suggestions, limit, signal);
            return;
        }

        char cInPrefix = prefix[position];
        char[] neighbors = surroundings.get(cInPrefix);

        if (neighbors == null) {
            SuggestionProvider.TrieNode child = node.getChild(cInPrefix);
            if (child != null) {
                buffer[depth] = cInPrefix;
                fullSubstitutionPrefixSearch(buffer, depth + 1, child, prefix, position + 1, subs, suggestions, limit, signal);
            }
            return;
        }

        for (char c : neighbors) {
            int nextSubs = subs + (c == cInPrefix ? 0 : 1);
            if (nextSubs > MAX_FUZZY_SUBSTITUTIONS) continue;

            SuggestionProvider.TrieNode child = node.getChild(c);
            if (child != null) {
                buffer[depth] = c;
                fullSubstitutionPrefixSearch(buffer, depth + 1, child, prefix, position + 1, nextSubs, suggestions, limit, signal);
            }
        }
    }

    public List<String> suggest(String token) {
        return suggest(token, MAX_CANDIDATES_PER_BRANCH * 6, null);
    }

    public List<String> suggest(String token, int limit, CancellationSignal signal) {
        if (token == null || token.isEmpty()) {
            return Collections.emptyList();
        }


        final String normalizedTokenString = token.toLowerCase();
        final char[] normalizedToken = normalizedTokenString.toCharArray();
        final int tokenLen = normalizedToken.length;



        final List<String> customSuggestions = new ArrayList<>();
        final List<String> commonSuggestions = new ArrayList<>();
        final List<String> wordlistSuggestions = new ArrayList<>();

        final Set<String> customEdits = new HashSet<>();
        final Set<String> commonEdits = new HashSet<>();
        final Set<String> wordlistEdits = new HashSet<>();

        final CountDownLatch latch = new CountDownLatch(6);



        runInParallel(latch, () -> fullSubstitutionSearch(new char[MAX_WORD_LENGTH], 0, suggestionProvider.customRoot, normalizedToken, 0, 0, customSuggestions, limit, signal));
        runInParallel(latch, () -> {
            if (suggestionProvider.commonLoaded) {
                fullSubstitutionSearch(new char[MAX_WORD_LENGTH], 0, suggestionProvider.commonRoot, normalizedToken, 0, 0, commonSuggestions, limit, signal);
            }
        });
        runInParallel(latch, () -> fullSubstitutionSearch(new char[MAX_WORD_LENGTH], 0, suggestionProvider.wordlistRoot, normalizedToken, 0, 0, wordlistSuggestions, limit, signal));


        runInParallel(latch, () -> editDistanceSearch(new char[MAX_WORD_LENGTH], 0, suggestionProvider.customRoot, normalizedToken, 0, 1, customEdits, limit, signal));
        runInParallel(latch, () -> {
            if (suggestionProvider.commonLoaded) {
                editDistanceSearch(new char[MAX_WORD_LENGTH], 0, suggestionProvider.commonRoot, normalizedToken, 0, 1, commonEdits, limit, signal);
            }
        });
        runInParallel(latch, () -> editDistanceSearch(new char[MAX_WORD_LENGTH], 0, suggestionProvider.wordlistRoot, normalizedToken, 0, 1, wordlistEdits, limit, signal));

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }


        Set<String> finalSuggestions = new LinkedHashSet<>();
        finalSuggestions.addAll(customSuggestions);
        finalSuggestions.addAll(commonSuggestions);
        finalSuggestions.addAll(wordlistSuggestions);
        finalSuggestions.addAll(customEdits);
        finalSuggestions.addAll(commonEdits);
        finalSuggestions.addAll(wordlistEdits);

        return new ArrayList<>(finalSuggestions);
    }

    private void runInParallel(CountDownLatch latch, Runnable task) {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            try {
                task.run();
            } finally {
                latch.countDown();
            }
        });
    }

    private boolean isFuzzyMatch(char typed, char trieChar) {
        if (typed == trieChar) return true;
        char[] neighbors = surroundings.get(typed);
        if (neighbors == null) return false;
        for (char n : neighbors) {
            if (n == trieChar) return true;
        }
        return false;
    }


    private void fullSubstitutionSearch(char[] buffer, int depth, SuggestionProvider.TrieNode node,
                                        char[] token, int position, int subs, Collection<String> suggestions,
                                        int limit, CancellationSignal signal) {

        if (signal != null && signal.isCancelled()) return;
        if (suggestions.size() >= limit) return;

        if (position == token.length) {
            if (node.isEndOfWord) {
                suggestions.add(new String(buffer, 0, depth));
            }
            return;
        }

        if (depth >= MAX_WORD_LENGTH) return;

        char cInToken = token[position];
        char[] neighbors = surroundings.get(cInToken);


        if (neighbors == null) {
            SuggestionProvider.TrieNode child = node.getChild(cInToken);
            if (child != null) {
                buffer[depth] = cInToken;
                fullSubstitutionSearch(buffer, depth + 1, child, token, position + 1, subs, suggestions, limit, signal);
            }
            return;
        }


        for (char c : neighbors) {
            int nextSubs = subs + (c == cInToken ? 0 : 1);
            if (nextSubs > MAX_FUZZY_SUBSTITUTIONS) continue;

            SuggestionProvider.TrieNode child = node.getChild(c);
            if (child != null) {
                buffer[depth] = c;
                fullSubstitutionSearch(buffer, depth + 1, child, token, position + 1, nextSubs, suggestions, limit, signal);
            }
        }
    }

    private void editDistanceSearch(char[] buffer, int depth, SuggestionProvider.TrieNode node,
                                    char[] token, int tokenIndex, int edits, Collection<String> suggestions,
                                    int limit, CancellationSignal signal) {

        if (signal != null && signal.isCancelled()) return;
        if (suggestions.size() >= limit) return;

        if (node.isEndOfWord && tokenIndex == token.length) {
            suggestions.add(new String(buffer, 0, depth));
        }

        if (tokenIndex > token.length || edits < 0 || depth >= MAX_WORD_LENGTH) {
            return;
        }




        final int childCount = node.keys.length;
        for (int i = 0; i < childCount; i++) {
            char c = node.keys[i];
            SuggestionProvider.TrieNode child = node.children[i];
            buffer[depth] = c;


            if (tokenIndex < token.length && isFuzzyMatch(token[tokenIndex], c)) {
                editDistanceSearch(buffer, depth + 1, child, token, tokenIndex + 1, edits, suggestions, limit, signal);
            } else if (edits > 0) {

                editDistanceSearch(buffer, depth + 1, child, token, tokenIndex + 1, edits - 1, suggestions, limit, signal);

                editDistanceSearch(buffer, depth + 1, child, token, tokenIndex, edits - 1, suggestions, limit, signal);

                editDistanceSearch(buffer, depth + 1, node, token, tokenIndex + 1, edits - 1, suggestions, limit, signal);

                if (tokenIndex < token.length - 1 && c == token[tokenIndex + 1]) {

                     SuggestionProvider.TrieNode grandChild = child.getChild(token[tokenIndex]);
                     if (grandChild != null) {

                         if (depth + 1 < MAX_WORD_LENGTH) {
                             buffer[depth + 1] = token[tokenIndex];
                             editDistanceSearch(buffer, depth + 2, grandChild, token, tokenIndex + 2, edits - 1, suggestions, limit, signal);
                         }
                     }
                }
            }
        }
    }
}
