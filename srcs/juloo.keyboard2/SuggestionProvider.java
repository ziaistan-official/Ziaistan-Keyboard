package juloo.keyboard2;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SuggestionProvider {

    private static final int MAX_SUGGESTIONS = 20;
    private static final String CUSTOM_DICTIONARY_FILE = "custom.txt";

    public static class TrieNode {
        public Map<Character, TrieNode> children = new HashMap<>();
        public boolean isEndOfWord;
    }

    public final TrieNode customRoot;
    public final TrieNode commonRoot;
    public final TrieNode wordlistRoot;
    private final Context context;

    public volatile boolean commonLoaded = false;
    private volatile boolean wordlistLoaded = false;

    public SuggestionProvider(Context context) {
        this.context = context;
        customRoot = new TrieNode();
        commonRoot = new TrieNode();
        wordlistRoot = new TrieNode();
        loadCustomDictionary(customRoot);
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            loadDictionary(R.raw.common, commonRoot);
            commonLoaded = true;
        });
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            loadDictionary(R.raw.wordlist, wordlistRoot);
            wordlistLoaded = true;
        });
    }

    public void reloadCustomDictionary() {
        synchronized (customRoot) {
            customRoot.children.clear();
            customRoot.isEndOfWord = false;
            loadCustomDictionary(customRoot);
        }
    }

    private void loadCustomDictionary(TrieNode root) {
        File customDictFile = new File(context.getFilesDir(), CUSTOM_DICTIONARY_FILE);
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
        for (char ch : word.toCharArray()) {
            current = current.children.computeIfAbsent(ch, c -> new TrieNode());
        }
        current.isEndOfWord = true;
    }

    public List<String> getSuggestions(String prefix) {
        List<String> suggestions = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) {
            return suggestions;
        }

        // Find suggestions from custom dictionary first
        TrieNode customPrefixNode = findPrefixNode(prefix, customRoot);
        if (customPrefixNode != null) {
            findAllWords(customPrefixNode, prefix, suggestions);
        }

        // Then from common words
        if (commonLoaded && suggestions.size() < MAX_SUGGESTIONS) {
            TrieNode commonPrefixNode = findPrefixNode(prefix, commonRoot);
            if (commonPrefixNode != null) {
                findAllWords(commonPrefixNode, prefix, suggestions);
            }
        }

        // Finally from the wordlist
        if (wordlistLoaded && suggestions.size() < MAX_SUGGESTIONS) {
            TrieNode wordlistPrefixNode = findPrefixNode(prefix, wordlistRoot);
            if (wordlistPrefixNode != null) {
                findAllWords(wordlistPrefixNode, prefix, suggestions);
            }
        }

        return suggestions;
    }

    private TrieNode findPrefixNode(String prefix, TrieNode root) {
        TrieNode current = root;
        for (char ch : prefix.toLowerCase().toCharArray()) {
            TrieNode node = current.children.get(ch);
            if (node == null) {
                return null;
            }
            current = node;
        }
        return current;
    }

    private void findAllWords(TrieNode node, String prefix, List<String> suggestions) {
        if (suggestions.size() >= MAX_SUGGESTIONS) {
            return;
        }
        if (node.isEndOfWord) {
            if (!suggestions.contains(prefix)) {
                suggestions.add(prefix);
            }
        }
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            findAllWords(entry.getValue(), prefix + entry.getKey(), suggestions);
            if (suggestions.size() >= MAX_SUGGESTIONS) {
                return;
            }
        }
    }

    public enum WordSource { CUSTOM, COMMON, WORDLIST, NONE }

    /**
     * Checks which dictionary a word belongs to, in order of priority.
     * This check is case-insensitive.
     * @param word The word to validate.
     * @return The {@link WordSource} of the word, or {@code WordSource.NONE} if not found.
     */
    public WordSource getWordSource(String word) {
        if (word == null || word.isEmpty()) {
            return WordSource.NONE;
        }

        // Check custom dictionary (highest priority)
        TrieNode customNode = findPrefixNode(word, customRoot);
        if (customNode != null && customNode.isEndOfWord) {
            return WordSource.CUSTOM;
        }

        // Check common dictionary
        if (commonLoaded) {
            TrieNode commonNode = findPrefixNode(word, commonRoot);
            if (commonNode != null && commonNode.isEndOfWord) {
                return WordSource.COMMON;
            }
        }

        // Check wordlist dictionary (lowest priority)
        if (wordlistLoaded) {
            TrieNode wordlistNode = findPrefixNode(word, wordlistRoot);
            if (wordlistNode != null && wordlistNode.isEndOfWord) {
                return WordSource.WORDLIST;
            }
        }

        return WordSource.NONE;
    }

    /**
     * Checks if a word exists in any of the loaded dictionaries.
     * This check is case-insensitive.
     * @param word The word to validate.
     * @return {@code true} if the word is found, {@code false} otherwise.
     */
    public boolean isValidWord(String word) {
        return getWordSource(word) != WordSource.NONE;
    }
}