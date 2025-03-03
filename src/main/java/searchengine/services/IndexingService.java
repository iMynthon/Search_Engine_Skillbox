package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.exception.IndexingException;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static searchengine.model.Status.FAILED;
import static searchengine.model.Status.INDEXED;

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


    public void startIndexingSite(){
            for(SiteConfig siteConfig : sitesList.getSites()){
                log.info("Indexing site: {}",siteConfig);
                Site site = new Site();
                site.setStatus(INDEXED);
                site.setUrl(siteConfig.getUrl());
                site.setName(siteConfig.getName());
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("");
                List<Page> pages = new ArrayList<>();
                if(!pool.isShutdown()) {
                    pages = pool.invoke(new SiteCrawler(siteConfig.getUrl()));
                }
                else{
                    site.setStatus(FAILED);
                }
                for(Page page : pages){
                    page.setSite(site);
                }
                site.setPage(pages);
                siteRepository.saveAndFlush(site);
            }


    }

    public Map<String,? super Objects> stopIndexing(){
        if(pool.isShutdown()) {
            try {
                log.info("Индексация остановлена");
                pool.shutdown();
                if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {

                    pool.shutdownNow();
                    return Map.of("result", true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return new LinkedHashMap<String, Object>() {{
            put("result", false);
            put("error", "indexing don't start");
        }};

    }
}
