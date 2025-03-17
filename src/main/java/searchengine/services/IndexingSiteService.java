package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.ConnectionSetting;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.exception.IndexingSitesException;
import searchengine.exception.ResourcesNotFoundException;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.dto.response.ResponseBoolean;
import searchengine.dto.response.ResponseError;
import searchengine.until.LemmaFinder;
import searchengine.until.SiteCrawler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
    private final JdbcTemplate jdbcTemplate;
    private ForkJoinPool pool;

    private final AtomicBoolean isIndexingRunning = new AtomicBoolean(false);

    @Transactional
    public CompletableFuture<ResponseBoolean> startIndexingSite() {
        if (isIndexingRunning.get()) {
            log.info("Индексация уже запущена");
            return CompletableFuture.completedFuture(new ResponseError(new Exception("Индексация уже запущена")));
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

                    Pair<List<Lemma>, List<Index>> lemmaAndIndex = findLemmaToText(site, pages);
                    site.setLemma(lemmaAndIndex.getLeft());

                    site.setStatus(pool.isShutdown() ? FAILED : INDEXED);

                    siteRepository.save(site);
                    pageRepository.saveAll(pages);
                    lemmaRepository.saveAll(lemmaAndIndex.getLeft());
                    indexRepository.saveAll(lemmaAndIndex.getRight());

                } catch (Exception e) {
                    log.error("Ошибка при индексация сайта: {}", siteConfig + " - " + e.getMessage());
                    site.setStatus(FAILED);
                    site.setLastError(pool.isShutdown() ? "Индексация остановлена пользователем" : e.getMessage());
                    siteRepository.save(site);
                }
                log.info("Сайт проиндексирован: {}", siteConfig);
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

    public ResponseBoolean deleteSiteIndexing(Integer id) {
        if (!siteRepository.existsById(id)) {
            return new ResponseError(new ResourcesNotFoundException("Запрашиваемый сайт не был проиндексирован, ошибка запроса"));
        }
        siteRepository.deleteById(id);
        return new ResponseBoolean(true);
    }

    @Transactional
    public CompletableFuture<ResponseBoolean> indexPage(String url) {
        String urlToPage = URLDecoder.decode(url.substring(url.indexOf("h")), StandardCharsets.UTF_8);

        SiteConfig siteConfig = checkPageToSiteConfig(urlToPage).orElseThrow(() -> new ResourcesNotFoundException(String.format(
                "Данная страница %s находится за переделами конфигурационных файлов", urlToPage)));

        if (checkIndexingPage(urlToPage, siteConfig)) {
            log.info("Такая страница уже есть в базе данных");
            pageRepository.deletePageByPath(urlToPage.substring(siteConfig.getUrl().length()));
            log.info("Все связанные данные с этой страницы были удалены");
        }

        Site site = siteRepository.findByUrl(siteConfig.getUrl());

        pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors()
                , ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null, true);

        log.info("Индексация страницы: {}", urlToPage);

        pool.execute(() -> {
            try {
                List<Page> pages = new SiteCrawler(siteConfig.getUrl(), urlToPage, connectionSetting).compute();

                site.setPage(addSiteToPage(site, pages));

                Pair<List<Lemma>, List<Index>> lemmaAndIndex = findLemmaToText(site, pages);

                site.setLemma(lemmaAndIndex.getLeft());

                pageRepository.saveAll(pages);
                lemmaRepository.saveAll(lemmaAndIndex.getLeft());
                indexRepository.saveAll(lemmaAndIndex.getRight());
                siteRepository.save(site);

            } catch (Exception e) {
                log.error(e.getMessage());
                site.setLastError(e.getMessage());
                siteRepository.save(site);
            }
        });

        return CompletableFuture.completedFuture(new ResponseBoolean(true));
    }

    public ResponseBoolean systemSearch(String text) {
        try {
            LemmaFinder lemmaFinder = LemmaFinder.getInstance();
            Set<String> uniqueLemma = lemmaFinder.getLemmaSet(text);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private Optional<SiteConfig> checkPageToSiteConfig(String url) {
        for (SiteConfig siteConfig : sitesList.getSites()) {
            if (url.startsWith(siteConfig.getUrl())) {
                return Optional.of(siteConfig);
            }
        }
        return Optional.empty();
    }

    private Pair<List<Lemma>, List<Index>> findLemmaToText(Site site, List<Page> pages) {
        Map<String, Lemma> allLemmas = new ConcurrentHashMap<>();
        List<Index> indexList = new ArrayList<>();

        pages.parallelStream().forEach(page -> {
            try {
                LemmaFinder lemmaFinder = LemmaFinder.getInstance();
                Map<String, Integer> currentLemmas = lemmaFinder.collectLemmas(page.getContent());
                currentLemmas.forEach((lemmaText, count) -> {
                    Lemma lemma = allLemmas.computeIfAbsent(lemmaText, text -> {
                        Lemma l = new Lemma();
                        l.setSite(site);
                        l.setLemma(text);
                        l.setFrequency(0);
                        return l;
                    });

                    lemma.setFrequency(lemma.getFrequency() + count);

                    Index index = new Index();
                    index.setPage(page);
                    index.setLemma(lemma);
                    index.setRank((float) count);
                    indexList.add(index);
                });
            } catch (IOException e) {
                log.error("Ошибка при лемматизации страницы: {}", page.getPath(), e);
            }
        });
        return Pair.of(new ArrayList<>(allLemmas.values()), indexList);
    }

    private boolean checkIndexingPage(String url, SiteConfig siteConfig) {
        String path = url.substring(siteConfig.getUrl().length());
        return pageRepository.existsByPath(path);
    }

    private List<Page> addSiteToPage(Site site, List<Page> pages) {
        pages.forEach(p -> p.setSite(site));
        return pages;
    }

//    private void batchLemmaInsert(List<Lemma> lemmaList){
//        String sql = "INSERT INTO lemma (site_id,lemma,frequency) VALUES (?,?,?)";
//        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
//            @Override
//            public void setValues(PreparedStatement ps, int i) throws SQLException {
//                Lemma lemma = lemmaList.get(i);
//                ps.setInt(1,lemma.getSite().getId());
//                ps.setString(2,lemma.getLemma());
//                ps.setInt(3,lemma.getFrequency());
//            }
//
//            @Override
//            public int getBatchSize() {
//                return lemmaList.size();
//            }
//        });
//    }
//
//    private void batchIndexInsert(List<Index> indexList){
//        String sql = "INSERT INTO index (page_id,lemma_id,rank) VALUES (?,?,?)";
//        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
//            @Override
//            public void setValues(PreparedStatement ps, int i) throws SQLException {
//                Index index = indexList.get(i);
//                ps.setObject(1,index.getPage());
//                ps.setObject(2,index.getLemma());
//                ps.setFloat(3,index.getRank());
//            }
//
//            @Override
//            public int getBatchSize() {
//                return indexList.size();
//            }
//        });
//    }

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
