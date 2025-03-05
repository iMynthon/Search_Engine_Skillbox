package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.site.PageDTO;
import searchengine.dto.site.SiteDTO;
import searchengine.exception.IndexingException;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.until.ResponseFormat;
import searchengine.until.ResponseString;
import searchengine.until.SiteCrawler;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

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

            if (pool.isShutdown()) {
                return new ResponseEntity<>(
                        new ResponseString(false, "Indexing is already running")
                        , HttpStatus.BAD_REQUEST);
            }

            log.info("Indexing site: {}", siteConfig);

            Site site = initSite(siteConfig);
            siteRepository.save(site);

            try {
                List<Page> pages = pool.invoke(new SiteCrawler(siteConfig.getUrl()));

                site.setPage(addSiteToPage(site, pages));

                site.setStatus(pool.isShutdown() ? FAILED : INDEXED);

                pageRepository.saveAll(pages);
            } catch (Exception e) {
                log.error("Error during site indexing: {}", e.getMessage());
                site.setStatus(FAILED);
                site.setLastError(pool.isShutdown() ? "Indexing stop to User" : e.getMessage());
                siteRepository.save(site);
                return new ResponseEntity<>(
                        new ResponseString(false, "Error during site indexing: " + e.getMessage()),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        log.info("Indexing sites: {}", sitesList);
        return new ResponseEntity<>(
                new ResponseFormat(true), HttpStatus.OK);
    }

    public ResponseEntity<ResponseFormat> stopIndexing() {
        try {
            if (!pool.isShutdown()) {
                log.info("Индексация остановлена");
                pool.shutdownNow();
                if (pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                    return new ResponseEntity<>(new ResponseFormat(true), HttpStatus.OK);
                }
            }
        } catch (IndexingException | InterruptedException e) {
            log.info(("Error stop indexing"), e);
        }
        return new ResponseEntity<>(new ResponseString(false, "Indexing don't start")
                , HttpStatus.BAD_REQUEST);

    }

    public ResponseEntity<ResponseFormat> deleteSiteIndexing(Integer id) {
        if (!siteRepository.existsById(id)) {
            return new ResponseEntity<>(new ResponseFormat(false), HttpStatus.NOT_FOUND);
        }
        siteRepository.deleteById(id);
        return new ResponseEntity<>(new ResponseFormat(true), HttpStatus.OK);
    }

    public ResponseEntity<ResponseFormat> indexPage(String url) {

        for (SiteConfig siteConfig : sitesList.getSites()) {
            if (url.startsWith(siteConfig.getUrl())) {

                log.info("Indexing page: {}", url);

                Site site = siteRepository.findByUrl(siteConfig.getUrl());

                List<Page> pages = pool.invoke(new SiteCrawler(url));

                addSiteToPage(site, pages);

                pageRepository.saveAll(pages);

                return new ResponseEntity<>(new ResponseFormat(true), HttpStatus.CREATED);
            }
        }
        return new ResponseEntity<>(
                new ResponseString(false,
                        "Данная страница находится за пределами конфигурационный файлов"), HttpStatus.NOT_FOUND);
    }


    public static Site siteMapToEntity(SiteDTO siteDTO) {
        Site site = new Site();
        site.setId(siteDTO.getId());
        site.setStatus(siteDTO.getStatus());
        site.setStatusTime(siteDTO.getStatusTime());
        site.setLastError(siteDTO.getLastError());
        site.setUrl(siteDTO.getUrl());
        site.setName(siteDTO.getName());
        site.setPage(siteDTO.getPage()
                .stream()
                .map(IndexingService::pageMapToEntity)
                .toList());
        return site;
    }

    public static SiteDTO siteMapToDTO(Site site) {
        SiteDTO siteDTO = new SiteDTO();
        siteDTO.setId(site.getId());
        siteDTO.setStatus(site.getStatus());
        siteDTO.setStatusTime(site.getStatusTime());
        siteDTO.setLastError(site.getLastError());
        siteDTO.setUrl(site.getUrl());
        siteDTO.setName(site.getName());
        siteDTO.setPage(site.getPage()
                .stream()
                .map(IndexingService::pageMapToDTO)
                .toList());
        return siteDTO;
    }

    public static Page pageMapToEntity(PageDTO pageDTO) {
        Page page = new Page();
        page.setId(pageDTO.getId());
        page.setPath(pageDTO.getPath());
        page.setCode(pageDTO.getCode());
        page.setContent(pageDTO.getContent());
        return page;
    }


    public static PageDTO pageMapToDTO(Page page) {
        PageDTO pageDTO = new PageDTO();
        pageDTO.setId(page.getId());
        pageDTO.setSite(page.getSite().getUrl());
        pageDTO.setPath(page.getPath());
        pageDTO.setCode(page.getCode());
        pageDTO.setContent(page.getContent());
        return pageDTO;
    }

    private List<Page> addSiteToPage(Site site, List<Page> pages) {
        for (Page page : pages) {
            page.setSite(site);
        }
        return pages;
    }

    private Site initSite(SiteConfig siteConfig) {
        Site site = new Site();
        site.setUrl(siteConfig.getUrl());
        site.setName(siteConfig.getName());
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(INDEXING);
        return site;
    }

}
