package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.IndexRepository;
import searchengine.dto.LemmaRepository;
import searchengine.model.*;

import java.util.*;

@Service
public class SearchServiceImpl implements SearchService {

    public static final int HIGH_FREQUENCY = 100;
    public static final int WORDS_AROUND = 5;

    @Autowired
    private final TextProcessor textProcessor;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;

    public SearchServiceImpl(TextProcessor textProcessor,
                             LemmaRepository lemmaRepository,
                             IndexRepository indexRepository) {
        this.textProcessor = textProcessor;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    public RequestAnswer search(String query, int offset, int limit) {
        List<String> queryWords = splitQueryForLemmas(query);

        // ---------Checks if query is empty or no such lemmas was found in DB---------
        if (queryWords.isEmpty()) {
            return new RequestAnswer(false, "No results found");
        }

        List<Lemma> queryLemmas = new ArrayList<>();
        for (String queryWord : queryWords) {
            queryLemmas.addAll(lemmaRepository.findByLemma(queryWord));
            if (queryLemmas.isEmpty()) {
                return new RequestAnswer(false, "No results found");
            }
        }
        // -----------------------------------------------------------------------------


        //----------Removes high frequency lemmas and sorts from rare to common---------

        Iterator<Lemma> iterator = queryLemmas.iterator();
        while (iterator.hasNext()) {
            Lemma lemma = iterator.next();
            if (lemma.getFrequency() > HIGH_FREQUENCY && queryLemmas.size() > 1) {
                iterator.remove();
            }
        }

        queryLemmas.sort(Comparator.comparing(Lemma::getFrequency).reversed());
        // ----------------------------------------------------------------------------

        Set<Page> pages = new HashSet<>();
        indexRepository.findByLemmaId(queryLemmas.get(0).getId()).forEach(index -> pages.add(index.getPageId()));

        //---------Removes pages that don't contain all query lemmas---------
        for (int i = 1; i < queryLemmas.size(); i++) {
            Iterator<Page> pageIterator = pages.iterator();
            while (pageIterator.hasNext()) {
                Page page = pageIterator.next();
                if (indexRepository.findByLemmaIdAndPageId(queryLemmas.get(i).getId(), page.getId()).isEmpty()) {
                    pageIterator.remove();
                }
            }
        }
        // -----------------------------------------------------------------

        if (pages.isEmpty()) {
            return new RequestAnswer(false, "No results found");
        }


        //---------Calculates absolute relevance of each page---------
        HashMap<Page, Double[]> pagesRelevance = new HashMap<>();
        double maxAbsoluteRelevance = 0.0;

        for (Page page : pages) {
            Double[] relevanceValues = new Double[queryLemmas.size() + 2];
            relevanceValues[0] = 0.0;
            double absoluteRelevance = 0.0;
            for (int i = 0; i < queryLemmas.size(); i++) {
                List<Index> indexes = indexRepository.findByLemmaIdAndPageId(queryLemmas.get(i).getId(), page.getId());
                relevanceValues[i] = (double) indexes.get(0).getRank();
                absoluteRelevance += relevanceValues[i];
            }

            relevanceValues[queryLemmas.size()] = absoluteRelevance;
            pagesRelevance.put(page, relevanceValues);

            if (absoluteRelevance > maxAbsoluteRelevance) {
                maxAbsoluteRelevance = absoluteRelevance;
            }
        }
        //----------------------------------------------------


        //---------Calculates relative relevance of each page---------
        for (Map.Entry<Page, Double[]> entry : pagesRelevance.entrySet()) {
            Double[] relevanceValues = entry.getValue();
            relevanceValues[queryLemmas.size() + 1] = relevanceValues[queryLemmas.size()] / maxAbsoluteRelevance;
        }
        //------------------------------------------------------------

        //---------Sorts pages by relative relevance---------
        List<Page> sortedPages = new ArrayList<>(pagesRelevance.keySet());
        sortedPages.sort(new PageByRelativeRelevanceComparator(pagesRelevance));
        //----------------------------------------------------

        //---------Creates Search result for each page---------
        ArrayList<SearchResult> searchResults = new ArrayList<>();
        for(int i = offset; i < limit; i++) {
            Page page = sortedPages.get(i);
            SearchResult searchResult = new SearchResult(page.getSiteId().getUrl(),
                    page.getSiteId().getName(),
                    page.getPath(),
                    getTitle(page),
                    createSnippet(page.getContent(), query),
                    pagesRelevance.get(page)[queryLemmas.size() + 1]);
            searchResults.add(searchResult);
        }
        //----------------------------------------------------


        return new SearchRequestAnswer(pages.size(), searchResults);
    }

    private List<String> splitQueryForLemmas(String query) {
        query = query.toLowerCase();
        query = textProcessor.removePunctuation(query);
        String[] split = query.split(" ");
        List<String> queryWords = new ArrayList<>(Arrays.asList(split));
        queryWords.removeIf(String::isEmpty);
        queryWords.forEach(textProcessor::getLemma);

        return queryWords;
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


            return bRelativeRelevance.compareTo(aRelativeRelevance);
        }
    }


    /**
     * Gets snippet from given text (pageContent) based on first word in a query
     *
     * @param pageContent text will be used for creating query
     * @param query       string value of query
     * @return snippet contains (WORDS_AROUND * 2) number of words if possible and surrounded by "...",
     * each word of query surrounded by <b> tag
     */
    private String createSnippet(String pageContent, String query) {
        String pageText = textProcessor.removePunctuation(pageContent);
        String pageTextInLemmas = String.join(" ", textProcessor.countLemmas(pageText).keySet());
        String queryLemma = textProcessor.getLemma(query.split(" ")[0]);

        int queryLemmaIndex = pageTextInLemmas.indexOf(queryLemma);

        if (queryLemmaIndex != -1) {
            int snippetStartIndex = queryLemmaIndex - WORDS_AROUND * 2;
            int snippetEndIndex = queryLemmaIndex + WORDS_AROUND * 2;

            if (snippetStartIndex < 0) {
                snippetStartIndex = 0;
            }

            if (snippetEndIndex > pageText.length()) {
                snippetEndIndex = pageText.length();
            }

            String snippet = pageText.substring(snippetStartIndex, snippetEndIndex);
            snippet = snippet.replaceAll(queryLemma, "<b>" + queryLemma + "</b>");
            snippet = "..." + snippet + "...";

            return snippet;
        } else {
            return "";
        }
    }

}
