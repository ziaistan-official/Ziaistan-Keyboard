package juloo.keyboard2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SentenceCorrector {

    private final SuggestionProvider suggestionProvider;

    public SentenceCorrector(SuggestionProvider suggestionProvider, KeyboardAwareSuggester keyboardAwareSuggester) {
        this.suggestionProvider = suggestionProvider;
    }

    public String segment(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        List<String> segmentedWords = new ArrayList<>();
        String result = segment(text, segmentedWords);
        if (result != null) {
            return result;
        }
        return text; // Return original text if segmentation fails
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

        return null; // No segmentation found
    }
}
