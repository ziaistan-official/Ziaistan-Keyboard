package juloo.keyboard2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class LayoutBasedAutoCorrectionProvider {

    private final SuggestionProvider suggestionProvider;
    private Map<Character, List<Character>> adjacencyMap;
    private volatile boolean layoutReady = false;

    public LayoutBasedAutoCorrectionProvider(SuggestionProvider suggestionProvider) {
        this.suggestionProvider = suggestionProvider;
    }

    public void updateLayout(KeyboardData keyboardData) {
        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            layoutReady = false;
            if (keyboardData != null) {
                adjacencyMap = KeyboardLayoutAnalyzer.getAdjacencyMap(keyboardData);
            }
            layoutReady = true;
        });
    }

    private static class CorrectionCandidate {
        final String word;
        final SuggestionProvider.WordSource source;

        CorrectionCandidate(String word, SuggestionProvider.WordSource source) {
            this.word = word;
            this.source = source;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CorrectionCandidate that = (CorrectionCandidate) obj;
            return word.equals(that.word);
        }

        @Override
        public int hashCode() {
            return word.hashCode();
        }
    }


    public List<String> getCorrections(String word) {
        if (!layoutReady || suggestionProvider == null || word == null || word.isEmpty()) {
            return Collections.emptyList();
        }


        if (suggestionProvider.isValidWord(word)) {
            return Collections.emptyList();
        }

        return getSimilarWords(word);
    }


    public List<String> getSimilarWords(String word) {
        return getSimilarWords(word, Config.globalConfig().suggestion_search_priority);
    }

    public List<String> getSimilarWords(String word, String searchPriority) {
        if (!layoutReady || suggestionProvider == null || word == null || word.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> results = new ArrayList<>();
        String[] priorities = searchPriority.split(",");
        for (String p : priorities) {
            String tp = p.trim();
            List<String> typeResults = null;
            switch (tp) {
                case SuggestionProvider.SEARCH_SUBSTITUTION: typeResults = getSubstitutionCandidates(word); break;
                case SuggestionProvider.SEARCH_DELETION: typeResults = getDeletionCandidates(word); break;
                case SuggestionProvider.SEARCH_INSERTION: typeResults = getInsertionCandidates(word); break;
                case SuggestionProvider.SEARCH_TRANSPOSITION: typeResults = getTranspositionCandidates(word); break;
                case SuggestionProvider.SEARCH_DOUBLING: typeResults = getDoublingCandidates(word); break;
                case SuggestionProvider.SEARCH_SINGLING: typeResults = getSinglingCandidates(word); break;
            }
            if (typeResults != null) {
                for (String s : typeResults) {
                    if (!results.contains(s)) results.add(s);
                }
            }
        }
        return results;
    }

    public List<String> getSubstitutionCandidates(String word) {
        Set<CorrectionCandidate> candidates = new HashSet<>();
        if (adjacencyMap == null) return Collections.emptyList();
        for (int i = 0; i < word.length(); i++) {
            List<Character> neighbors = adjacencyMap.get(word.charAt(i));
            if (neighbors != null) {
                StringBuilder builder = new StringBuilder(word);
                for (char neighbor : neighbors) {
                    builder.setCharAt(i, neighbor);
                    String candidateWord = builder.toString();
                    if (suggestionProvider.isValidWord(candidateWord)) {
                        candidates.add(new CorrectionCandidate(candidateWord, suggestionProvider.getWordSource(candidateWord)));
                    }
                }
            }
        }
        return toWordList(candidates);
    }

    private List<String> toWordList(Set<CorrectionCandidate> candidates) {
        List<String> result = new ArrayList<>();
        for (CorrectionCandidate c : candidates) result.add(c.word);
        return result;
    }

    public List<String> getDeletionCandidates(String word) {
        Set<CorrectionCandidate> candidates = new HashSet<>();
        if (word.length() <= 1) return Collections.emptyList();
        for (int i = 0; i < word.length(); i++) {
            String candidateWord = word.substring(0, i) + word.substring(i + 1);
            if (suggestionProvider.isValidWord(candidateWord)) {
                candidates.add(new CorrectionCandidate(candidateWord, suggestionProvider.getWordSource(candidateWord)));
            }
        }
        return toWordList(candidates);
    }

    public List<String> getInsertionCandidates(String word) {
        Set<CorrectionCandidate> candidates = new HashSet<>();
        if (adjacencyMap == null || adjacencyMap.isEmpty()) return Collections.emptyList();

        for (int i = 0; i <= word.length(); i++) {
            Set<Character> charsToInsert = new HashSet<>();
            if (i > 0) {
                List<Character> leftNeighbors = adjacencyMap.get(word.charAt(i - 1));
                if (leftNeighbors != null) charsToInsert.addAll(leftNeighbors);
            }
            if (i < word.length()) {
                List<Character> rightNeighbors = adjacencyMap.get(word.charAt(i));
                if (rightNeighbors != null) charsToInsert.addAll(rightNeighbors);
            }
            for (char c : charsToInsert) {
                String candidateWord = new StringBuilder(word).insert(i, c).toString();
                if (suggestionProvider.isValidWord(candidateWord)) {
                    candidates.add(new CorrectionCandidate(candidateWord, suggestionProvider.getWordSource(candidateWord)));
                }
            }
        }
        return toWordList(candidates);
    }

    public List<String> getTranspositionCandidates(String word) {
        Set<CorrectionCandidate> candidates = new HashSet<>();
        if (word.length() < 2) return Collections.emptyList();
        for (int i = 0; i < word.length() - 1; i++) {
            char[] chars = word.toCharArray();
            char temp = chars[i];
            chars[i] = chars[i + 1];
            chars[i + 1] = temp;
            String candidateWord = new String(chars);
            if (suggestionProvider.isValidWord(candidateWord)) {
                candidates.add(new CorrectionCandidate(candidateWord, suggestionProvider.getWordSource(candidateWord)));
            }
        }
        return toWordList(candidates);
    }

    public List<String> getDoublingCandidates(String word) {
        Set<CorrectionCandidate> candidates = new HashSet<>();
        for (int i = 0; i < word.length(); i++) {
            String doubledWord = word.substring(0, i + 1) + word.charAt(i) + word.substring(i + 1);
            if (suggestionProvider.isValidWord(doubledWord)) {
                candidates.add(new CorrectionCandidate(doubledWord, suggestionProvider.getWordSource(doubledWord)));
            }
        }
        return toWordList(candidates);
    }

    public List<String> getSinglingCandidates(String word) {
        Set<CorrectionCandidate> candidates = new HashSet<>();
        if (word.matches(".*(.)\\1.*")) {
            StringBuilder reducedBuilder = new StringBuilder();
            if (word.length() > 0) {
                reducedBuilder.append(word.charAt(0));
                for (int i = 1; i < word.length(); i++) {
                    if (word.charAt(i) != word.charAt(i - 1)) {
                        reducedBuilder.append(word.charAt(i));
                    }
                }
                String reducedWord = reducedBuilder.toString();
                if (suggestionProvider.isValidWord(reducedWord)) {
                    candidates.add(new CorrectionCandidate(reducedWord, suggestionProvider.getWordSource(reducedWord)));
                }
            }
        }
        return toWordList(candidates);
    }

    private void getSubstitutionCandidates(String word, Set<CorrectionCandidate> candidates) {
        for (String s : getSubstitutionCandidates(word)) candidates.add(new CorrectionCandidate(s, suggestionProvider.getWordSource(s)));
    }

    private void getDeletionCandidates(String word, Set<CorrectionCandidate> candidates) {
        for (String s : getDeletionCandidates(word)) candidates.add(new CorrectionCandidate(s, suggestionProvider.getWordSource(s)));
    }

    private void getInsertionCandidates(String word, Set<CorrectionCandidate> candidates) {
        for (String s : getInsertionCandidates(word)) candidates.add(new CorrectionCandidate(s, suggestionProvider.getWordSource(s)));
    }

    private void getTranspositionCandidates(String word, Set<CorrectionCandidate> candidates) {
        for (String s : getTranspositionCandidates(word)) candidates.add(new CorrectionCandidate(s, suggestionProvider.getWordSource(s)));
    }

    private void getReversalCandidates(String word, Set<CorrectionCandidate> candidates) {
        if (word.length() < 2) return;
        String reversedWord = new StringBuilder(word).reverse().toString();
        if (suggestionProvider.isValidWord(reversedWord)) {
            candidates.add(new CorrectionCandidate(reversedWord, suggestionProvider.getWordSource(reversedWord)));
        }
    }

    private void getDoublingSinglingCandidates(String word, Set<CorrectionCandidate> candidates) {
        for (String s : getDoublingCandidates(word)) candidates.add(new CorrectionCandidate(s, suggestionProvider.getWordSource(s)));
        for (String s : getSinglingCandidates(word)) candidates.add(new CorrectionCandidate(s, suggestionProvider.getWordSource(s)));
    }
}