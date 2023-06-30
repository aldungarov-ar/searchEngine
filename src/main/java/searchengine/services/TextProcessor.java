package searchengine.services;

import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class TextProcessor {
    private static final String[] FUNCTIONAL_PROPERTIES = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private final RussianLuceneMorphology luceneMorphology;

    public TextProcessor() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    /**
     * Counts Lemmas in given RAW text
     *
     * @param text raw text of web page
     * @return Map of lemmas and count of its appearance on given page
     */
    public Map<String, Integer> countLemmas(String text) {
        String[] words = prepareText(text);
        Map<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {

            String lemma = getLemma(word);
            lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
        }

        return lemmas;
    }

    /**
     * Method will clean the text leaving only words and numbers LowerCase
     *
     * @param text text to clean
     * @return array of words and numbers LowerCase without punctuation
     */
    private String[] prepareText(String text) {

        return text.toLowerCase(Locale.ROOT)
                .replaceAll("<[^>]*>", "")
                .replaceAll("([^а-яa-z\\d])", " ")
                .replaceAll("ё", "е")
                .replaceAll("\\s+", " ")
                .trim()
                .split("\\s+");
    }

    public boolean wordIsFunctional(String word) {
        if (word.length() == 0) {
            return false;
        } else if (wordIsInLatin(word) || wordIsNumeric(word)) {
            return false;
        }

        List<String> morphInfo = luceneMorphology.getMorphInfo(word);

        for (String property : FUNCTIONAL_PROPERTIES) {
            if (morphInfo.get(0).contains(property)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Method will return lemma for given word, or empty string in case exception
     *
     * @param word word to make lemma
     * @return lemma for given word
     */
    public String getLemma(String word) {
        word = word.toLowerCase(Locale.ROOT);

        if (wordIsInLatin(word) || wordIsNumeric(word)) {
            return word;
        } else if (word.length() == 0 || wordIsFunctional(word)) {
            return "";
        }

        try {
            List<String> morphInfo = luceneMorphology.getMorphInfo(word);
            return morphInfo.get(0).
                    split("\\|")[0];
        } catch (WrongCharaterException exception) {
            return "";
        }
    }

    private boolean wordIsInLatin(String word) {
        if (word.length() == 0) {
            return false;
        }

        return word.matches("[a-z]+");
    }

    private boolean wordIsNumeric(String word) {
        return word.matches("\\d+");
    }

    public String removePunctuation(String query) {
        return query.replaceAll("[^а-яa-z0-9]", " ").trim();
    }
}
