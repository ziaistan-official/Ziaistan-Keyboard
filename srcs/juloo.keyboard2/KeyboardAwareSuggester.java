package juloo.keyboard2;

import android.content.Context;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;

public class KeyboardAwareSuggester {

    private final Map<Character, List<Character>> surroundings;
    private final SuggestionProvider suggestionProvider;

    public KeyboardAwareSuggester(Context context, SuggestionProvider suggestionProvider) {
        this.surroundings = parseSurroundings(context);
        this.suggestionProvider = suggestionProvider;
    }

    private Map<Character, List<Character>> parseSurroundings(Context context) {
        Map<Character, List<Character>> surroundingsMap = new HashMap<>();
        try {
            XmlPullParser parser = context.getResources().getXml(R.xml.surroundings);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "char".equals(parser.getName())) {
                    char value = parser.getAttributeValue(null, "value").charAt(0);
                    String neighborsStr = parser.getAttributeValue(null, "neighbors");
                    List<Character> neighbors = new ArrayList<>();
                    neighbors.add(value); // A character is its own neighbor
                    for (char c : neighborsStr.toCharArray()) {
                        neighbors.add(c);
                    }
                    surroundingsMap.put(value, neighbors);
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }
        return surroundingsMap;
    }

    public List<String> suggest(String token) {
        if (token == null || token.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedToken = token.toLowerCase();
        final List<String> customSuggestions = Collections.synchronizedList(new ArrayList<>());
        final List<String> commonSuggestions = Collections.synchronizedList(new ArrayList<>());
        final List<String> wordlistSuggestions = Collections.synchronizedList(new ArrayList<>());

        final CountDownLatch latch = new CountDownLatch(6); // 2 searches x 3 dictionaries

        // Full Substitution Search
        runInParallel(latch, () -> fullSubstitutionSearch(new StringBuilder(), suggestionProvider.customRoot, getAlternates(normalizedToken), 0, customSuggestions));
        runInParallel(latch, () -> {
            if (suggestionProvider.commonLoaded) {
                fullSubstitutionSearch(new StringBuilder(), suggestionProvider.commonRoot, getAlternates(normalizedToken), 0, commonSuggestions);
            }
        });
        runInParallel(latch, () -> fullSubstitutionSearch(new StringBuilder(), suggestionProvider.wordlistRoot, getAlternates(normalizedToken), 0, wordlistSuggestions));

        // Edit Distance 1 Search
        runInParallel(latch, () -> editDistanceSearch(new StringBuilder(), suggestionProvider.customRoot, normalizedToken, 0, 1, customSuggestions));
        runInParallel(latch, () -> {
            if (suggestionProvider.commonLoaded) {
                editDistanceSearch(new StringBuilder(), suggestionProvider.commonRoot, normalizedToken, 0, 1, commonSuggestions);
            }
        });
        runInParallel(latch, () -> editDistanceSearch(new StringBuilder(), suggestionProvider.wordlistRoot, normalizedToken, 0, 1, wordlistSuggestions));

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

    private List<List<Character>> getAlternates(String token) {
        List<List<Character>> alternates = new ArrayList<>();
        for (char c : token.toCharArray()) {
            List<Character> neighbors = surroundings.get(c);
            if (neighbors == null) {
                neighbors = Collections.singletonList(c);
            }
            alternates.add(neighbors);
        }
        return alternates;
    }

    private void fullSubstitutionSearch(StringBuilder currentWord, SuggestionProvider.TrieNode node,
                                        List<List<Character>> alternates, int position, List<String> suggestions) {
        if (position == alternates.size()) {
            if (node.isEndOfWord) {
                if (!suggestions.contains(currentWord.toString())) {
                    suggestions.add(currentWord.toString());
                }
            }
            return;
        }

        for (char c : alternates.get(position)) {
            SuggestionProvider.TrieNode child = node.children.get(c);
            if (child != null) {
                currentWord.append(c);
                fullSubstitutionSearch(currentWord, child, alternates, position + 1, suggestions);
                currentWord.deleteCharAt(currentWord.length() - 1);
            }
        }
    }

    private void editDistanceSearch(StringBuilder currentWord, SuggestionProvider.TrieNode node,
                                    String token, int tokenIndex, int edits, List<String> suggestions) {
        if (node.isEndOfWord && tokenIndex == token.length()) {
            if (!suggestions.contains(currentWord.toString())) {
                suggestions.add(currentWord.toString());
            }
        }

        if (tokenIndex > token.length() || edits < 0) {
            return;
        }

        for (Map.Entry<Character, SuggestionProvider.TrieNode> entry : node.children.entrySet()) {
            char c = entry.getKey();
            SuggestionProvider.TrieNode child = entry.getValue();
            currentWord.append(c);

            // Match
            if (tokenIndex < token.length() && c == token.charAt(tokenIndex)) {
                editDistanceSearch(currentWord, child, token, tokenIndex + 1, edits, suggestions);
            } else if (edits > 0) {
                // Substitution
                editDistanceSearch(currentWord, child, token, tokenIndex + 1, edits - 1, suggestions);
                // Deletion
                editDistanceSearch(currentWord, child, token, tokenIndex, edits - 1, suggestions);
                // Insertion
                editDistanceSearch(currentWord, node, token, tokenIndex + 1, edits - 1, suggestions);
                // Transposition
                if (tokenIndex < token.length() - 1 && c == token.charAt(tokenIndex + 1) && entry.getValue().children.containsKey(token.charAt(tokenIndex))) {
                    editDistanceSearch(currentWord, child.children.get(token.charAt(tokenIndex)), token, tokenIndex + 2, edits - 1, suggestions);
                }
            }
            currentWord.deleteCharAt(currentWord.length() - 1);
        }
    }
}
