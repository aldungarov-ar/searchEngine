package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.LemmaRepository;
import searchengine.dto.PageRepository;
import searchengine.dto.SiteRepository;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        //----------Fill in the total statistics----------------
        TotalStatistics total = new TotalStatistics();
        int totalSites = siteRepository.sitesTotal();
        total.setSites(totalSites);
        int totalPages = pageRepository.pagesTotal();
        total.setPages(totalPages);
        int totalLemmas = lemmaRepository.lemmasTotal();
        total.setLemmas(totalLemmas);

        total.setIndexing(IndexingServiceImpl.inProgress);

        //----------Fill in the detailed statistics-------------
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = siteRepository.findAll();
        for (Site site : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            int siteId = site.getId();
            int pages = pageRepository.countBySiteId(siteId);
            item.setPages(pages);
            int lemmas = lemmaRepository.countBySiteId(siteId);
            item.setLemmas(lemmas);
            item.setStatus(site.getStatus().toString());
            item.setError(site.getLastError());
            item.setStatusTime(ZonedDateTime.of(site.getStatusTime(), ZoneId.systemDefault())
                    .toInstant().toEpochMilli());
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
