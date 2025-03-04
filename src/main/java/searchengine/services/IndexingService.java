package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.exception.IndexingException;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.until.ResponseFormat;
import searchengine.until.SiteCrawler;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static searchengine.model.Status.*;

@Service
@Slf4j
public class IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sitesList;
    private final ForkJoinPool pool = new ForkJoinPool();

    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository, SitesList sitesList) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sitesList = sitesList;
    }

    public ResponseEntity<ResponseFormat> startIndexingSite() {
        for (SiteConfig siteConfig : sitesList.getSites()) {

            if(pool.isShutdown()){
                return new ResponseEntity<>(
                        new ResponseFormat("error", "ForkJoinPool is shutdown")
                        ,HttpStatus.INTERNAL_SERVER_ERROR);
            }

            log.info("Indexing site: {}", siteConfig);
            Site site = initSite(siteConfig);
            siteRepository.save(site);
            List<Page> pages = new ArrayList<>();

            if(!pool.isShutdown()) {
                pages = pool.invoke(new SiteCrawler(siteConfig.getUrl()));
            }

            site.setPage(addSiteToPage(site,pages));

            site.setStatus(INDEXED);

            pageRepository.saveAll(pages);
        }
        return new ResponseEntity<>(new ResponseFormat("result",true), HttpStatus.OK);
    }

    public ResponseEntity<ResponseFormat> stopIndexing()  {
        try {
            if (!pool.isShutdown()) {
                log.info("Индексация остановлена");
                pool.shutdownNow();
                if (pool.awaitTermination(5, TimeUnit.SECONDS)) {
                   pool.shutdownNow();
                    return new ResponseEntity<>(new ResponseFormat("result", true), HttpStatus.OK);
                }
            }
        } catch (IndexingException | InterruptedException e){
            log.info(("Error stop indexing"),e);
        }
        return new ResponseEntity<>(new ResponseFormat("error","Indexing don't start")
                ,HttpStatus.BAD_REQUEST);

    }

    private List<Page> addSiteToPage(Site site, List<Page> pages){
        for(Page page : pages){
            page.setSite(site);
        }
        return pages;
    }

    private Site initSite(SiteConfig siteConfig){
        Site site = new Site();
        site.setUrl(siteConfig.getUrl());
        site.setName(siteConfig.getName());
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(INDEXING);
        return site;
    }
}
