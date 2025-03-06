package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.ConnectionSetting;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.site.PageDTO;
import searchengine.dto.site.SiteDTO;
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
    private final ConnectionSetting connectionSetting;
    private ForkJoinPool pool;

    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository, SitesList sitesList, ConnectionSetting connectionSetting) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sitesList = sitesList;
        this.connectionSetting = connectionSetting;
    }

    public ResponseEntity<ResponseFormat> startIndexingSite() {

        if (pool != null && !pool.isShutdown()) {
            return new ResponseEntity<>(
                    new ResponseString(false, "Indexing is already running")
                    , HttpStatus.BAD_REQUEST);
        }

        pool = new ForkJoinPool();

        for (SiteConfig siteConfig : sitesList.getSites()) {
            log.info("Indexing site: {}", siteConfig.getUrl());

            Site site = initSite(siteConfig);
            log.info(site.getUrl());
            siteRepository.save(site);

            try {
                List<Page> pages = pool.invoke(new SiteCrawler(siteConfig.getUrl(), connectionSetting));
                site.setPage(addSiteToPage(site, pages));
                site.setStatus(INDEXED);
                pageRepository.saveAll(pages);

            } catch (Exception e) {
                log.error("Error during site indexing: {}", e.getMessage());
                site.setStatus(FAILED);
                site.setLastError(e.getMessage());
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
            if (pool != null && !pool.isShutdown()) {
                pool.shutdownNow();
                if (pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                    return new ResponseEntity<>(new ResponseFormat(true), HttpStatus.OK);
                }
            }
        } catch (InterruptedException e) {
            log.info(("Error stop indexing"), e);
            Thread.currentThread().interrupt();
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
        Optional<SiteConfig> siteConfig = findSiteConfig(url);

        if (siteConfig.isEmpty()) {
            return new ResponseEntity<>(
                    new ResponseString(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"),
                    HttpStatus.NOT_FOUND);
        }

        Site site = siteRepository.findByUrl(siteConfig.get().getUrl());

        try {

            List<Page> pages = pool.invoke(new SiteCrawler(url, connectionSetting));

            addSiteToPage(site, pages);
            pageRepository.saveAll(pages);

            return new ResponseEntity<>(new ResponseFormat(true), HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Ошибка индексации страницы", e);

            site.setLastError(e.getMessage());
            siteRepository.save(site);

            return new ResponseEntity<>(
                    new ResponseString(false, "Error during page indexing: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
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

    private Optional<SiteConfig> findSiteConfig(String url) {
        return sitesList.getSites().stream()
                .filter(siteConfig -> url.startsWith(siteConfig.getUrl()))
                .findFirst();
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
        site.setLastError("");
        site.setStatus(INDEXING);
        return site;
    }

}
