package searchengine.services;

import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class TextProcessor {
    private static final String[] FUNCTIONAL_PROPERTIES = new String[] {"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
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

            String info = luceneMorphology.getMorphInfo(word).get(0);
            word = info.split("\\|")[0];

            if (!wordIsFunctional(word)) {
                if (lemmas.containsKey(word)) {
                    lemmas.put(word, lemmas.get(word) + 1);
                } else {
                    lemmas.put(word, 1);
                }
            }
        }

        return lemmas;
    }

    /**
     * Method will clean the text leaving only russian words LowerCase
     *
     * @param text text to clean
     * @return array of russian words LowerCase
     */
    private String[] prepareText(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
    }

    public boolean wordIsFunctional(String word) {
        List<String> morphInfo = luceneMorphology.getMorphInfo(word);

        for(String property : FUNCTIONAL_PROPERTIES) {
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
        if (wordIsInLatin(word) || word.length() == 0) {
            return word;
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

        Character.UnicodeScript script = Character.UnicodeScript.of(word.charAt(0));
        return script.equals(Character.UnicodeScript.LATIN);
    }
}
