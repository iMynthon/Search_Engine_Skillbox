package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.ConnectionSetting;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.exception.IndexingSitesException;
import searchengine.exception.ResourcesNotFoundException;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.dto.CustomResponse.ResponseBoolean;
import searchengine.dto.CustomResponse.ResponseError;
import searchengine.until.LemmaFinder;
import searchengine.until.SiteCrawler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static searchengine.model.Status.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndexingSiteService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sitesList;
    private final ConnectionSetting connectionSetting;
    private final IndexRepository indexRepository;

    private ForkJoinPool pool;

    private final AtomicBoolean isIndexingRunning = new AtomicBoolean(false);

    @Transactional
    public CompletableFuture<ResponseBoolean> startIndexingSite() {
        if (isIndexingRunning.get()) {
            log.info("Индексация уже запущена");
            return CompletableFuture.completedFuture(new ResponseError(new Exception()));
        }

        isIndexingRunning.set(true);
        log.info("Индексация запущена");


        pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()
                , ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null, true);

        pool.execute(() -> {
            for (SiteConfig siteConfig : sitesList.getSites()) {

                if (siteRepository.existsByUrl(siteConfig.getUrl())) {
                    log.info("Этот сайт уже проиндексирован: {}", siteConfig);
                    continue;
                } else if (pool.isShutdown()) {
                    log.info("Индексация остановлена");
                    break;
                }

                log.info("Индексация сайта: {}", siteConfig.getUrl());

                Site site = initSite(siteConfig);

                siteRepository.save(site);
                try {
                    List<Page> pages = new SiteCrawler(siteConfig.getUrl(), connectionSetting).compute();
                    site.setPage(addSiteToPage(site, pages));

                    List<Lemma> listLemma = findLemmaToText(site, pages);
                    site.setLemma(listLemma);

                    site.setStatus(pool.isShutdown() ? FAILED : INDEXED);
                    pageRepository.saveAll(pages);
                    lemmaRepository.saveAll(listLemma);

                } catch (Exception e) {
                    log.error("Ошибка при индексация сайта: {}", siteConfig + " - " + e.getMessage());
                    site.setStatus(FAILED);
                    site.setLastError(pool.isShutdown() ? "" : e.getMessage());
                    siteRepository.save(site);
                }
                log.info("Сайт про индексирован: {}", siteConfig);
            }
        });
        isIndexingRunning.set(false);
        return CompletableFuture.completedFuture(new ResponseBoolean(true));

    }

    public CompletableFuture<ResponseBoolean> stopIndexing() {
        if (!pool.isShutdown()) {
            pool.shutdownNow();
            return CompletableFuture.completedFuture(new ResponseBoolean(true));
        }
        throw new IndexingSitesException("Индексация не запущена");

    }

    public ResponseEntity<ResponseBoolean> deleteSiteIndexing(Integer id) {
        if (!siteRepository.existsById(id)) {
            return new ResponseEntity<>(new ResponseBoolean(false), HttpStatus.NOT_FOUND);
        }
        siteRepository.deleteById(id);
        return new ResponseEntity<>(new ResponseBoolean(true), HttpStatus.OK);
    }

    @Transactional
    public CompletableFuture<ResponseBoolean> indexPage(String url) {
        String cleanedUrl = url.substring(url.indexOf("h"), url.lastIndexOf('"'));

        SiteConfig siteConfig = checkPageToSiteConfig(cleanedUrl).orElseThrow(() -> new ResourcesNotFoundException(
                "Данная страница находится за переделами конфигурационных файлов"));
        if (checkIndexingPage(url, siteConfig)) {
            return CompletableFuture.completedFuture(new ResponseError(
                    new IndexingSitesException("Данная страница уже проиндексирована")));
        }

        Site site = siteRepository.findByUrl(siteConfig.getUrl());

        pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()
                , ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null, true);

        pool.execute(() -> {
            try {
                List<Page> pages = new SiteCrawler(siteConfig.getUrl(), cleanedUrl, connectionSetting).compute();

                site.setPage(addSiteToPage(site, pages));
                site.setLemma(findLemmaToText(site, pages));

                pageRepository.saveAll(pages);

            } catch (Exception e) {
                log.error(e.getMessage());
                site.setLastError(e.getMessage());
                siteRepository.save(site);
            }
        });

        return CompletableFuture.completedFuture(new ResponseBoolean(true));
    }

    private Optional<SiteConfig> checkPageToSiteConfig(String url) {
        for (SiteConfig siteConfig : sitesList.getSites()) {
            if (url.startsWith(siteConfig.getUrl())) {
                return Optional.of(siteConfig);
            }
        }
        return Optional.empty();
    }

    private List<Lemma> findLemmaToText(Site site, List<Page> pages) {
        Map<String, Integer> allLemmas = new ConcurrentHashMap<>();
        pages.parallelStream().forEach(page -> {
            try {
                LemmaFinder lemmaFinder = LemmaFinder.getInstance();
                Map<String, Integer> currentLemmas = lemmaFinder.collectLemmas(page.getContent());
                currentLemmas.forEach((lemma, count) -> {
                    allLemmas.merge(lemma, count, (oldValue, newValue) -> newValue);
                });
            } catch (IOException e) {
                log.error("Ошибка при лемматизации страницы: {}", page.getPath(), e);
            }
        });

        return allLemmas.entrySet()
                .parallelStream()
                .map(entry -> {
                    Lemma lemma = new Lemma();
                    lemma.setSite(site);
                    lemma.setLemma(entry.getKey());
                    lemma.setFrequency(entry.getValue());
                    return lemma;
                })
                .toList();
    }

    private boolean checkIndexingPage(String url, SiteConfig siteConfig) {
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
