package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.IndexRepository;
import searchengine.dto.LemmaRepository;
import searchengine.model.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    public static final int HIGH_FREQUENCY = 100;
    public static final int WORDS_AROUND = 5;

    private int offset;
    private int limit;

    @Autowired
    private TextProcessor textProcessor;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;

    @Override
    public RequestAnswer search(String query, int offset, int limit) {
        query = query.toLowerCase();
        ArrayList<String> queryLemmas = new ArrayList<>(
                textProcessor.countLemmas(query).keySet());

        ArrayList<Lemma> lemmas = new ArrayList<>();
        for (String lemma : queryLemmas) {
            List<Lemma> lemmaList = lemmaRepository.findByLemma(lemma);
            if (lemmaList.isEmpty()) {
                continue;
            }
            lemmas.add(lemmaList.get(0));
        }

        if (lemmas.isEmpty()) {
            return new RequestAnswer(false, "Lemmas with frequency lower than " +
                    HIGH_FREQUENCY + " not found.");
        }

        Iterator<Lemma> iterator = lemmas.iterator();
        while (iterator.hasNext()) {
            Lemma lemma = iterator.next();
            if (lemma.getFrequency() > HIGH_FREQUENCY) {
                iterator.remove();
            }
        }

        // TODO add check if lemma from query is not exist in DB

        lemmas.sort(Comparator.comparingInt(Lemma::getFrequency)
                .reversed()); // TODO possibly there is a way to leave only lemmas Id's

        List<Index> indexesForRarestLemma = indexRepository.findByLemmaId(lemmas.get(0));
        if (indexesForRarestLemma.isEmpty()) {
            return null; // TODO create correct answer for case
        }
        ArrayList<Page> pages = new ArrayList<>();
        for (Index index : indexesForRarestLemma) {
            Page page = index.getPageId();
            pages.add(page);
        }

        Iterator<Page> pagesIterator = pages.iterator();
        for (Lemma lemma : lemmas) {
            List<Index> indexes = indexRepository.findByLemmaId(lemma);
            if (indexes.isEmpty()) {
                continue;
            }
            ArrayList<Page> pagesWithLemma = new ArrayList<>();
            for (Index index : indexes) {
                pagesWithLemma.add(index.getPageId());
            }
            pages.retainAll(pagesWithLemma);
        }

        if (pages.isEmpty()) {
            return null;
        }

        HashMap<Page, Double[]> relevanceTable = new HashMap<>();
        //This is the "head" of relevance table
        String[] queueWords = new String[lemmas.size()];
        for (int i = 0; i < lemmas.size(); i++) {
            queueWords[i] = lemmas.get(i).getLemma();
        }

        double maxAbsoluteRelevance = 0.0;
        for (Page page : pages) {
            // For now, last array element is relative relevance, and last-1 element is absolute relevance
            relevanceTable.put(page, new Double[queueWords.length + 2]);

            for (int i = 0; i < queueWords.length + 2; i++) {
                relevanceTable.get(page)[i] = 0.0;
            }

            Double[] row = relevanceTable.get(page);
            String content = page.getContent();
            String[] words = content.split("\\s+");

            for (int i = 0; i < queueWords.length; i++) {
                String queueWord = queueWords[i];
                Double relevance = calculateRelevance(queueWord, words);

                 row[i] = relevance;
            }

            double absoluteRelevance = 0.0;
            for (int i = 0; i < row.length - 2; i++) {
                absoluteRelevance += row[i];
            }
            row[row.length - 1] = absoluteRelevance;

            maxAbsoluteRelevance = Math.max(maxAbsoluteRelevance, absoluteRelevance);
        }

        for (Map.Entry<Page, Double[]> entry : relevanceTable.entrySet()) {
            Double[] row = entry.getValue();
            Double absoluteRelevance = row[row.length - 2];
            row[row.length - 1] = absoluteRelevance / maxAbsoluteRelevance;
        }

        PageByRelativeRelevanceComparator pageByRelativeRelevanceComparator
                = new PageByRelativeRelevanceComparator(relevanceTable);
        TreeMap<Page, Double[]> mapOfPagesSortedByRelativeRelevance
                = new TreeMap<>(pageByRelativeRelevanceComparator);

        ArrayList<SearchResult> searchResults = new ArrayList<>();
        SearchRequestAnswer searchRequestAnswer = new SearchRequestAnswer(
                mapOfPagesSortedByRelativeRelevance.size(), searchResults);

        int index = 0;
        for(Map.Entry<Page, Double[]> entry : mapOfPagesSortedByRelativeRelevance.entrySet()) {
            if (index <= offset) {
                index++;
            } else if (index >= limit ||
                    index >= mapOfPagesSortedByRelativeRelevance.entrySet().size()) {
                break;
            } else {
                Page page = entry.getKey();
                String site = page.getSiteId().getUrl();
                String siteName = page.getSiteId().getName();
                String uri = page.getPath();
                String title = getTitle(page);
                String snippet = createSnippet(page.getContent(), query);
                double relevance = entry.getValue()[entry.getValue().length - 1];
                SearchResult searchResult = new SearchResult(site, siteName, uri, title, snippet, relevance);

                searchRequestAnswer.addSearchResult(searchResult);
            }
        }


        return searchRequestAnswer;
    }

    private String getTitle(Page page) {
        String pageContent = page.getContent();
        int titleTextStartIndex = pageContent.indexOf("<title>");
        int titleTextEndIndex = pageContent.indexOf("</title>");

        if (titleTextStartIndex == -1 || titleTextEndIndex == -1) {
            return "";
        }

        titleTextStartIndex += 7;

        return pageContent.substring(titleTextStartIndex, titleTextEndIndex);
    }

    /**
     * Class is needed to represent complex logic of comparing pages by its pre-calculated relevance
     */
    static class PageByRelativeRelevanceComparator implements Comparator<Page> {
        Map<Page, Double[]> base;

        public PageByRelativeRelevanceComparator(Map<Page, Double[]> base) {
            this.base = base;
        }

        public int compare(Page a, Page b) {
            Double[] aRelevanceValues = base.get(a);
            Double[] bRelevanceValues = base.get(b);

            // In each array of relevance values last value is relative relevance
            Double aRelativeRelevance = aRelevanceValues[aRelevanceValues.length - 1];
            Double bRelativeRelevance = bRelevanceValues[bRelevanceValues.length - 1];


            if (aRelativeRelevance >= bRelativeRelevance) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    private Double calculateRelevance(String queueWord, String[] text) {
        double entriesCount = 0.0;
        for (String word : text) {
            word = word.toLowerCase();
            word = word.replaceAll("[^а-яa-z0-9]", "");
            word = textProcessor.getLemma(word);
            if (word.equals(queueWord)) {
                    entriesCount++;
            }
        }

        if (entriesCount == 0.0) {
            return 0.0;
        }
        return entriesCount / text.length;
    }


    /**
     * Gets snippet from given text (pageContent) based on first word in a query
     *
     * @param pageContent text will be used for creating query
     * @param query string value of query
     *
     * @return snippet contains (WORD_AROUND * 2) number of words if possible and surrounded by "...",
     * each word of query surrounded by <b> tag
     */
    private String createSnippet(String pageContent, String query) {
        String content = pageContent.toLowerCase();
        String[] contentWords = content.split("\\s+");

        String[] contentWordsLemmas = new String[contentWords.length];
        for (int i = 0; i < contentWords.length; i++) {
            String lemma = textProcessor.getLemma(contentWords[i]).equals("") ? contentWords[i] :
                    textProcessor.getLemma(contentWords[i]);
            contentWordsLemmas[i] = lemma;
        }

        String[] queryLemmas = query.split("\\s+");
        for (String word : queryLemmas) {
            word = textProcessor.getLemma(word);
        }
        String queryFirstWordLemma = queryLemmas[0];

        int rarestWordIndex = -1;
        for (int i = 0; i < contentWords.length; i++) {
            if (contentWordsLemmas[i].equals(queryFirstWordLemma)) {
                rarestWordIndex = i;
            }
        }

        ArrayList<String> queryLemmasList = new ArrayList<>(List.of(queryLemmas));
        StringBuilder snippet = new StringBuilder();
        int startIndex = Math.max((rarestWordIndex - WORDS_AROUND), 0);
        if (startIndex > 0) {
            snippet.append("...");
        }
        int endIndex = Math.min((rarestWordIndex + WORDS_AROUND), contentWords.length);

        for (int i = startIndex; i < endIndex; i++) {
            if (queryLemmasList.contains(contentWordsLemmas[startIndex + i])) {
                snippet.append("<b>");
            }
            snippet.append(contentWords[startIndex + i]);
            snippet.append(" ");
            if (queryLemmasList.contains(contentWordsLemmas[startIndex + i])) {
                snippet.append("</b>");
            }
        }

        if (endIndex == contentWords.length) {
            snippet.append("...");
        }


        return snippet.toString();
    }

}
