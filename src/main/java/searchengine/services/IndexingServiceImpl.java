package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.PageRepository;
import searchengine.dto.RequestAnswer;
import searchengine.dto.SiteRepository;
import searchengine.model.Site;
import searchengine.model.Status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{

    private static final int TERMINATION_AWAIT_TIME_HOURS = 24;
    public static volatile boolean inProgress;
    private final SitesList sites;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    private ForkJoinPool pool;

    @Override
    public RequestAnswer startIndexing() {

        if (inProgress) {
            return new RequestAnswer(false, "Indexing is already started");
        } else {
            inProgress = true;
        }

        for (Site site : sites.getSites()) {
            siteRepository.delete(site);
        }

        for (Site site : sites.getSites()) {
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            Site siteSaved = siteRepository.save(site);

            List<String> controlList = new ArrayList<>();
            controlList = Collections.synchronizedList(controlList);
            controlList.add(site.getUrl());

            PageExtractorAction parser = new PageExtractorAction(site.getUrl(), controlList, siteSaved,
                    pageRepository, siteRepository);
            pool = new ForkJoinPool();
            pool.execute(parser);
            pool.shutdown();

            awaitPoolTermination();

            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
        }

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
                site.setStatus(Status.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("Indexing stopped by user");
                siteRepository.save(site);
            }
        }

        return new RequestAnswer(true);
    }

}
