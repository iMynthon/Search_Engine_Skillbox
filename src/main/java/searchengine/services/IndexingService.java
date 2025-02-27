package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.dto.site.SiteDTO;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;

@Service
public class IndexingService {

   private final SiteRepository siteRepository;

   private final PageRepository pageRepository;

   private final List<String> checkUrl = new CopyOnWriteArrayList<>();

    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }


    public void startIndexingSite(SiteDTO siteDTO){

    }

    public void deleteAll(){
        siteRepository.deleteAll();
        pageRepository.deleteAll();
    }

    public void createSite(){
        Site site = new Site();
        site.setStatus(Status.INDEXED);
    }

}
