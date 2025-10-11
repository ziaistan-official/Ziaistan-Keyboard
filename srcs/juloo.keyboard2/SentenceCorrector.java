package juloo.keyboard2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SentenceCorrector {

    private final SuggestionProvider suggestionProvider;
    private final KeyboardAwareSuggester keyboardAwareSuggester;

    public SentenceCorrector(SuggestionProvider suggestionProvider, KeyboardAwareSuggester keyboardAwareSuggester) {
        this.suggestionProvider = suggestionProvider;
        this.keyboardAwareSuggester = keyboardAwareSuggester;
    }

    public String segment(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return segment(text, new ArrayList<>());
    }

    private String segment(String text, List<String> segmentedWords) {
        if (text.isEmpty()) {
            return String.join(" ", segmentedWords);
        }

        for (int i = text.length(); i > 0; i--) {
            String prefix = text.substring(0, i);
            if (suggestionProvider.isValidWord(prefix)) {
                segmentedWords.add(prefix);
                String result = segment(text.substring(i), segmentedWords);
                if (result != null) {
                    return result;
                }
                segmentedWords.remove(segmentedWords.size() - 1); // backtrack
            }
        }

        // If no valid word is found, try to correct the prefix
        for (int i = text.length(); i > 0; i--) {
            String prefix = text.substring(0, i);
            List<String> suggestions = keyboardAwareSuggester.suggest(prefix);
            if (!suggestions.isEmpty()) {
                segmentedWords.add(suggestions.get(0));
                String result = segment(text.substring(i), segmentedWords);
                if (result != null) {
                    return result;
                }
                segmentedWords.remove(segmentedWords.size() - 1); // backtrack
            }
        }

        return null; // No segmentation found
    }
}
