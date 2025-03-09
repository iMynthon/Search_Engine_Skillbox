package searchengine.services;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.ConnectionSetting;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.Indices;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndicesRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.until.CustomResponse.ResponseBoolean;
import searchengine.until.CustomResponse.ResponseError;
import searchengine.until.LemmaFinder;
import searchengine.until.SiteCrawler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static searchengine.model.Status.*;

@Service
@Slf4j
public class IndexingSiteService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sitesList;
    private final ConnectionSetting connectionSetting;
    private final IndicesRepository indicesRepository;
    private ForkJoinPool pool;

    private EntityManager entityManager;

    private final Object startLock = new Object();

    private final Object stopLock = new Object();

    public IndexingSiteService(SiteRepository siteRepository, PageRepository pageRepository,LemmaRepository lemmaRepository, IndicesRepository indicesRepository,
                               SitesList sitesList, ConnectionSetting connectionSetting) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sitesList = sitesList;
        this.connectionSetting = connectionSetting;
        this.lemmaRepository = lemmaRepository;
        this.indicesRepository = indicesRepository;
    }

    public ResponseEntity<ResponseBoolean> startIndexingSite() {

        if (pool != null && !pool.isShutdown()) {
            return new ResponseEntity<>(
                    new ResponseError(false, "Индексация уже запущена")
                    , HttpStatus.BAD_REQUEST);
        }

        pool = new ForkJoinPool();
      synchronized (startLock) {
          for (SiteConfig siteConfig : sitesList.getSites()) {
              if(siteRepository.existsByUrl(siteConfig.getUrl())){
                  log.info("Этот сайт уже проиндексирован: {}",siteConfig);
                  continue;
              }

              log.info("Индексация сайта: {}", siteConfig.getUrl());

              Site site = initSite(siteConfig);
              log.info(site.getUrl());
              siteRepository.saveAndFlush(site);

              try {
                  List<Page> pages = pool.invoke(new SiteCrawler(siteConfig.getUrl(), connectionSetting));
                  site.setPage(addSiteToPage(site, pages));

                  List<Lemma> listLemma = findLemmaToText(site,pages);
                  site.setLemma(listLemma);

                  site.setStatus(pool.isShutdown() ? FAILED : INDEXED);
                  pageRepository.saveAllAndFlush(pages);
                  lemmaRepository.saveAllAndFlush(listLemma);

              } catch (Exception e) {
                  log.error("Ошибка при индексация сайта: {}", siteConfig + " - " + e.getMessage());
                  site.setStatus(FAILED);
                  site.setLastError(e.getMessage());
                  siteRepository.save(site);
                  return new ResponseEntity<>(
                          new ResponseError(false, "Ошибка при индексация сайта: "
                                  + siteConfig.getUrl() + " - " + e.getMessage()),
                          HttpStatus.INTERNAL_SERVER_ERROR);
              }
          }
      }
        log.info("Список проиндексированных сайтов: {}", sitesList);
        return new ResponseEntity<>(
                new ResponseBoolean(true), HttpStatus.OK);
    }

    public ResponseEntity<ResponseBoolean> stopIndexing() {
        synchronized (stopLock) {
            try {
                if (!pool.isShutdown()) {
                    log.info("Остановка индексации");
                    pool.shutdownNow();
                    if(pool.awaitTermination(60,TimeUnit.SECONDS)){
                        pool.shutdownNow();
                    }
                    pool = null;
                    return new ResponseEntity<>(new ResponseBoolean(true), HttpStatus.OK);
                }
            } catch (Exception e) {
                log.error("Ошибка остановки потока: {}", e.getMessage());
            }
        }
        return new ResponseEntity<>(new ResponseError(false, "Индексация не запущена")
                , HttpStatus.BAD_REQUEST);

    }

    public ResponseEntity<ResponseBoolean> deleteSiteIndexing(Integer id) {
        if (!siteRepository.existsById(id)) {
            return new ResponseEntity<>(new ResponseBoolean(false), HttpStatus.NOT_FOUND);
        }
        siteRepository.deleteById(id);
        return new ResponseEntity<>(new ResponseBoolean(true), HttpStatus.OK);
    }

    public ResponseEntity<ResponseBoolean> indexPage(String url) {
        String cleanedUrl = url.substring(url.indexOf("h"),url.lastIndexOf('"'));

        Optional<SiteConfig> siteConfig = checkPageToSiteConfig(cleanedUrl);

        if (siteConfig.isEmpty()) {
            return new ResponseEntity<>(
                    new ResponseError(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"),
                    HttpStatus.NOT_FOUND);
        } else if(!checkIndexingPage(url,siteConfig.get())){
            return new ResponseEntity<>(
                    new ResponseError(false,"Данная страница уже проиндексирована"),HttpStatus.BAD_REQUEST);
        }

        Site site = siteRepository.findByUrl(siteConfig.get().getUrl());

        try {
            pool = new ForkJoinPool();
            List<Page> pages = pool.invoke(new SiteCrawler(cleanedUrl, connectionSetting));

            site.setPage(addSiteToPage(site, pages));
            site.setLemma(findLemmaToText(site,pages));

            pageRepository.saveAll(pages);

            return new ResponseEntity<>(new ResponseBoolean(true), HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Ошибка индексации страницы", e);

            site.setLastError(e.getMessage());
            siteRepository.save(site);

            return new ResponseEntity<>(
                    new ResponseError(false, "Ошибка индексации страницы: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


//    public static Site siteMapToEntity(SiteDTO siteDTO) {
//        Site site = new Site();
//        site.setId(siteDTO.getId());
//        site.setStatus(siteDTO.getStatus());
//        site.setStatusTime(siteDTO.getStatusTime());
//        site.setLastError(siteDTO.getLastError());
//        site.setUrl(siteDTO.getUrl());
//        site.setName(siteDTO.getName());
//        site.setPage(siteDTO.getPage()
//                .stream()
//                .map(IndexingSiteService::pageMapToEntity)
//                .toList());
//        return site;
//    }
//
//    public static SiteDTO siteMapToDTO(Site site) {
//        SiteDTO siteDTO = new SiteDTO();
//        siteDTO.setId(site.getId());
//        siteDTO.setStatus(site.getStatus());
//        siteDTO.setStatusTime(site.getStatusTime());
//        siteDTO.setLastError(site.getLastError());
//        siteDTO.setUrl(site.getUrl());
//        siteDTO.setName(site.getName());
//        siteDTO.setPage(site.getPage()
//                .stream()
//                .map(IndexingSiteService::pageMapToDTO)
//                .toList());
//        return siteDTO;
//    }

//    public static Page pageMapToEntity(PageDTO pageDTO) {
//        Page page = new Page();
//        page.setId(pageDTO.getId());
//        page.setPath(pageDTO.getPath());
//        page.setCode(pageDTO.getCode());
//        page.setContent(pageDTO.getContent());
//        return page;
//    }
//
//
//    public static PageDTO pageMapToDTO(Page page) {
//        PageDTO pageDTO = new PageDTO();
//        pageDTO.setId(page.getId());
//        pageDTO.setSite(page.getSite().getUrl());
//        pageDTO.setPath(page.getPath());
//        pageDTO.setCode(page.getCode());
//        pageDTO.setContent(page.getContent());
//        return pageDTO;
//    }

    private Optional<SiteConfig> checkPageToSiteConfig(String url) {
        for(SiteConfig siteConfig : sitesList.getSites()){
             if(url.startsWith(siteConfig.getUrl())){
                 return Optional.of(siteConfig);
             }
        }
        return Optional.empty();
    }

    private List<Lemma> findLemmaToText(Site site,List<Page> pages) {
        Map<Page,Map<String,Integer>> lemmasAndPage = new ConcurrentHashMap<>();
        pages.parallelStream().forEach(page -> {
            try {
                LemmaFinder lemmaFinder = LemmaFinder.getInstance();
                Map<String, Integer> currentLemmas = lemmaFinder.collectLemmas(page.getContent());
                lemmasAndPage.put(page,currentLemmas);
            } catch (IOException e) {
                log.error("Ошибка при лемматизации страницы: {}", page.getPath(), e);
            }
        });
        List<Indices> indicesList = new CopyOnWriteArrayList<>();
        List<Lemma> lemmaList = new ArrayList<>();
        lemmasAndPage.forEach((page, lemmas) -> lemmas.forEach((key, value) -> {

//            Lemma lemma = lemmaRepository.findByLemmaAndSite(key, site.getId())
//                    .orElseGet(() -> {
//                        Lemma newLemma = new Lemma();
//                        newLemma.setLemma(key);
//                        newLemma.setFrequency(0);
//                        newLemma.setSite(site);
//                        return newLemma;
//                    });

            Lemma lemma = new Lemma();
            lemma.setSite(site);
            lemma.setLemma(key);
            lemma.setFrequency(value);
            lemmaList.add(lemma);

            Indices indices = new Indices();
            indices.setPage(page);
            indices.setLemma(lemma);
            indices.setRank((float) value);
            indicesList.add(indices);
        }));

        indicesRepository.saveAll(indicesList);
        return lemmaList;
    }

    private boolean checkIndexingPage(String url,SiteConfig siteConfig){
        String path = url.substring(siteConfig.getUrl().length());
        return pageRepository.existsByPath(path);
    }

    private List<Page> addSiteToPage(Site site, List<Page> pages) {
        pages.forEach(p -> p.setSite(site));
        return pages;
    }

    private Site initSite(SiteConfig siteConfig) {
        Site site = new Site();
        site.setUrl(siteConfig.getUrl());
        site.setName(siteConfig.getName());
        site.setStatusTime(LocalDateTime.now());
        site.setLastError("");
        site.setStatus(INDEXING);
        return site;
    }

}
