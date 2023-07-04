package searchengine;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.services.TextProcessor;

import java.io.IOException;

public class Main {
    public static TextProcessor textProcessor;
    public static final int OFFSET = 0;
    public static final int WORDS_AROUND = 4;

    static {
        try {
            textProcessor = new TextProcessor();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {

        String url = "https://www.playback.ru/catalog/18.html";
        Connection connection = Jsoup.connect(url);
        Document document = connection.get();
        String text = document.body().text();

        System.out.println(document);
        System.out.println(text);

        String query = "метро";

        String textLowerCase = text.toLowerCase();
        String preparedText = stripText(textLowerCase);

        String textLemmasForSnippet = getLemmasForSnippet(preparedText);
        String[] textLemmasSplit = textLemmasForSnippet.split("\\s+");
        String queryLemmas = getLemmasForSnippet(query);
        String[] queryLemmasSplit = queryLemmas.split("\\s+");
        String firstLemma = queryLemmasSplit[0];

        Integer index = getIndexOfWordInArray(textLemmasSplit, firstLemma);
        if (index == -1) {
            return;
        }

        StringBuilder snippet = new StringBuilder();
        int startIndex = Math.max((index - WORDS_AROUND), 0);
        if (index - WORDS_AROUND > 0) {
            snippet.append("... ");
        }
        int endIndex = Math.min((index + WORDS_AROUND), textLemmasSplit.length - 1);

        String[] textWords = text.split("\\s+");
        for (int i = startIndex; i <= endIndex; i++) {
            if (i == index || arrayContains(textLemmasSplit[i], queryLemmasSplit)) {
                snippet.append("<b>").append(textWords[i]).append("</b>").append(" ");
            } else {
                snippet.append(textWords[i]).append(" ");
            }
        }

        if (index + WORDS_AROUND < textLemmasSplit.length - 1) {
            snippet.append("...");
        }
        System.out.println(text);
        System.out.println("Prepared text:");
        System.out.println(preparedText);
        System.out.println("Result:");
        System.out.println(snippet);
    }

    private static Integer getIndexOfWordInArray(String[] textLemmasSplit, String firstLemma) {
        int index = -1;
        for (int i = 0; i < textLemmasSplit.length; i++) {
            if (textLemmasSplit[i].equals(firstLemma)) {
                index = i;
                break;
            }
        }

        return index;
    }

    private static String stripText(String textLowerCase) {
        return textLowerCase.replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String getLemmasForSnippet(String preparedText) {
        preparedText = preparedText.toLowerCase();

        StringBuilder lemmas = new StringBuilder();
        for (String word : preparedText.split("\\s+")) {

            if (word.matches("[^а-яa-z\\d]+")) {
                lemmas.append(word).append(" ");
                continue;
            }

            word = word.replaceAll("([^а-яa-z\\d])", "");

            lemmas.append(textProcessor.getLemma(word)).append(" ");
        }

        return lemmas.toString();
    }

    private static boolean arrayContains(String word, String[] array) {
        for (String s : array) {
            if (s.equals(word)) {
                return true;
            }
        }
        return false;
    }
}
