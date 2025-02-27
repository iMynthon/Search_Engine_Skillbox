package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDate;
import java.util.concurrent.ForkJoinPool;

import static searchengine.model.Status.INDEXED;

@Service
public class IndexingService {

   private final SiteRepository siteRepository;

   private final PageRepository pageRepository;

   private final SitesList sitesList;

    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository, SitesList sitesList) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sitesList = sitesList;
    }


    public void startIndexingSite(){
            sitesList.getSites().forEach(s -> {
                ForkJoinPool pool = new ForkJoinPool();
                Site site = new Site();
                site.setStatus(INDEXED);
                site.setStatusTime(LocalDate.now());
                site.setLastError("");
                site.setUrl(s.getUrl());
                site.setName(s.getName());
                Site siteOfPage = pool.invoke(new SiteCrawler(s.getUrl()));

                pool.shutdown();

                site.setPage(siteOfPage.getPage());

                siteRepository.save(site);

                siteOfPage.getPage().forEach(p -> {
                    Page page = new Page();
                    page.setSite(p.getSite());
                    page.setCode(200);
                    page.setPath(p.getPath());
                    page.setContent(p.getContent());
                });
            });
    }

    public void deleteAll(){
        siteRepository.deleteAll();
        pageRepository.deleteAll();
    }

    public void createSite(){

    }

}
