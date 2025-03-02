package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

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
                Site site = new Site();
                site.setStatus(INDEXED);
                site.setUrl(siteConfig.getUrl());
                site.setName(siteConfig.getName());
                site.setStatusTime(LocalDate.now());
                site.setLastError("");
                List<Page> pages = pool.invoke(new SiteCrawler(siteConfig.getUrl()));
                pages.remove(0);
                for(Page page : pages){

                    page.setSite(site);
                }
                site.setPage(pages);
                siteRepository.saveAndFlush(site);
            }
            pool.shutdown();

    }
}
