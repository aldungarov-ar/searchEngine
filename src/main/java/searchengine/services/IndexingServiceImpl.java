package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.*;
import searchengine.model.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private static final Logger LOGGER = LogManager.getLogger(IndexingServiceImpl.class);
    private static final int TERMINATION_AWAIT_TIME_HOURS = 24;
    public static volatile boolean inProgress;
    private final SitesList sites;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private TextProcessor textProcessor;
    private ForkJoinPool pool;

    @Override
    public RequestAnswer startIndexing() {

        if (inProgress) {
            return new RequestAnswer(false, "Indexing is already started");
        } else {
            inProgress = true;
        }

        for (Site site : sites.getSites()) {
            siteRepository.deleteByUrl(site.getUrl());
        }

        for (Site site : sites.getSites()) {
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            Site siteSaved = siteRepository.save(site);

            List<String> controlList = new ArrayList<>();
            controlList = Collections.synchronizedList(controlList);
            controlList.add(site.getUrl());

            PageExtractorAction parser = new PageExtractorAction(site.getUrl(), controlList, siteSaved,
                    pageRepository, siteRepository, lemmaRepository, indexRepository, textProcessor);
            pool = new ForkJoinPool();
            pool.execute(parser);
            pool.shutdown();

            awaitPoolTermination();

            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
        }

        System.out.println("Parsing complete");

        inProgress = false;
        return new RequestAnswer(true);
    }

    private void awaitPoolTermination() {
        try {
            if (!pool.awaitTermination(TERMINATION_AWAIT_TIME_HOURS, TimeUnit.HOURS)) {
                awaitPoolTermination();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public RequestAnswer stopIndexing() {
        if (!inProgress) {
            return new RequestAnswer(false, "Indexing is not started");
        } else {
            inProgress = false;
        }

        pool.shutdownNow();

        List<Site> siteList = siteRepository.findAll();
        for (Site site : siteList) {
            if (site.getStatus() == Status.INDEXING) {
                site.updateStatus(Status.FAILED);
                site.setLastError("Indexing stopped by user");
                siteRepository.save(site);
            }
        }

        return new RequestAnswer(true);
    }

    public RequestAnswer indexPage(String url) {
        url = URLDecoder.decode(url, StandardCharsets.UTF_8);
        url = url.split("=")[1];
        String rootUrl = getSiteURL(url);

        if (!sites.contains(rootUrl)) {
            return new RequestAnswer(false, "Данная страница находится за пределами сайтов," +
                    " указанных в конфигурационном файле");
        }

        Site site = getActualSite(rootUrl);
        Status statusBeforeIndexing = site.getStatus() == null ? Status.FAILED : site.getStatus();
        site.updateStatus(Status.INDEXING);
        siteRepository.save(site);

        String pagePath = "/" + url.split("/", 4)[3];
        List<Page> pageList = pageRepository.findByPath(pagePath);
        if (pageList.size() > 0) {
            updateLemmasFrequency(pageList.get(0));
            pageRepository.deleteByPath(pagePath);
        }

        ArrayList<String> controlList = new ArrayList<>();
        inProgress = true;
        PageExtractorAction parser = new PageExtractorAction(url, controlList, site,
                pageRepository, siteRepository, lemmaRepository, indexRepository, textProcessor);
        parser.compute();
        inProgress = false;
        parser.quietlyComplete();
        parser.join();

        site.updateStatus(statusBeforeIndexing);
        siteRepository.save(site);

        System.out.println("Page indexed");

        return new RequestAnswer(true);
    }

    private void updateLemmasFrequency(Page page) {
        List<Index> indexesOfPage = indexRepository.findByPageId(page.getId());
        indexesOfPage.forEach(index -> {
            Lemma indexLemma = index.getLemmaId();
            if (indexLemma.getFrequency() == 1) {
                lemmaRepository.delete(indexLemma);
            } else {
                indexLemma.setFrequency(indexLemma.getFrequency() - 1);
                lemmaRepository.save(indexLemma);
            }
        });

        lemmaRepository.flush();
    }

    private Site getActualSite(String rootUrl) {
        Site site = sites.getSiteByURL(rootUrl);
        List<Site> sitesFromDB = siteRepository.findAll();

        if (sitesFromDB.contains(site)) {
            for (Site s : sitesFromDB) {
                if (s.equals(site)) {
                    site = s;
                }
            }
        }

        return site;
    }

    private String getSiteURL(String url) {
        String[] split = url.split("/");
        if (split[0].startsWith("http") && split.length >= 2) {
            url = split[2];
        } else {
            url = split[0];
        }
        return url;
    }

}
