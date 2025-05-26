package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Hibernate;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.app.ConnectionSetting;
import searchengine.config.app.SiteConfig;
import searchengine.config.app.SitesList;
import searchengine.dto.response.*;
import searchengine.exception.IndexingSitesException;
import searchengine.exception.ResourcesNotFoundException;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.until.LemmaFinder;
import searchengine.until.SiteCrawler;
import searchengine.until.SnippetGenerator;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static searchengine.model.Status.*;

@Service
@Slf4j
@RequiredArgsConstructor
@CacheConfig(cacheManager = "redisCacheManager")
public class IndexingSiteService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sitesList;
    private final ConnectionSetting connectionSetting;
    private final IndexRepository indexRepository;
    private ForkJoinPool pool;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean isIndexingRunning = new AtomicBoolean(false);

    @Lazy
    @Autowired
    private IndexingSiteService selfProxy;

    @CacheEvict(value = "Search_Result", allEntries = true)
    public CompletableFuture<ResponseBoolean> startIndexingSite() {
        if (isIndexingRunning.get()) {
            log.info("Индексация уже запущена");
            return CompletableFuture.completedFuture(new ResponseError(new Exception("Индексация уже запущена")));
        }
        isIndexingRunning.set(true);
        log.info("Индексация запущена");
        pool = new ForkJoinPool();
        proxiesDelete();
        for (SiteConfig siteConfig : sitesList.getSites()) {
            pool.execute(()-> {
                log.info("Индексация сайта: {}", siteConfig.getUrl());
                Site site = initSite(siteConfig);
                siteRepository.save(site);
                List<Page> pages = new SiteCrawler(siteConfig.getUrl(),site.getUrl(), connectionSetting).compute();
                site.setPage(addSiteToPage(site, pages));
                Pair<List<Lemma>, List<Index>> lemmaAndIndex = findLemmaToText(site, pages);
                site.setLemma(lemmaAndIndex.getLeft());
                site.setStatus(pool.isShutdown() ? FAILED : INDEXED);
                site.setLastError(pool.isShutdown() ? "Индексация остановлена пользователем" : "");
                proxiesSave(site, pages, lemmaAndIndex);
                siteRepository.save(site);
                log.info("Сайт проиндексирован: {}", siteConfig);
            });

        }
        isIndexingRunning.set(false);
        return CompletableFuture.completedFuture(new ResponseBoolean(true));
    }

    @Async
    protected void deleteAllDataIndexingSites(){
        log.info("Очищение всех таблиц в БД");
        jdbcTemplate.update("DELETE FROM site_schema.index");
        jdbcTemplate.update("DELETE FROM site_schema.lemma");
        jdbcTemplate.update("DELETE FROM site_schema.page");
        jdbcTemplate.update("DELETE FROM site_schema.site");
        log.info("Очищение завершено");
    }

    public ResponseBoolean stopIndexing() {
        if (!pool.isShutdown()) {
            pool.shutdown();
            log.info("Индексация была остановлена пользователем");
            return new ResponseBoolean(true);
        }
        throw new IndexingSitesException("Индексация не запущена");
    }

    @CacheEvict(value = "Search_Result", allEntries = true)
    public ResponseBoolean deleteSiteIndexing(Integer id) {
        if (!siteRepository.existsById(id)) {
            return new ResponseError(new ResourcesNotFoundException(String.format("По вашему запросу ничего не найдено, " +
                    "по такому %d сайт не найден",id)));
        }
        siteRepository.deleteById(id);
        return new ResponseBoolean(true);
    }

    @CacheEvict(value = "Search_Result", allEntries = true)
    public ResponseBoolean indexPage(String url) {
        String urlToPage = URLDecoder.decode(url.substring(url.indexOf("h")), StandardCharsets.UTF_8);
        SiteConfig siteConfig = checkPageToSiteConfig(urlToPage).orElseThrow(() -> new ResourcesNotFoundException(String.format(
                "Данная страница %s находится за переделами конфигурационных файлов", urlToPage)));
        String path = urlToPage.substring(siteConfig.getUrl().length());
        if (checkIndexingPage(urlToPage, siteConfig)) {
            log.info("Такая страница {} уже есть в базе данных",urlToPage);
            pageRepository.deletePageByPath(path);
            log.info("Все данные которые были связаны со страницей: {} были удалены",urlToPage);
        }
        Site site = siteRepository.findByUrl(siteConfig.getUrl());
        try {
            Connection.Response response = Jsoup.connect(urlToPage)
                    .userAgent(connectionSetting.getCurrentUserAgent())
                    .referrer(connectionSetting.getCurrentReferrer())
                    .timeout(5000)
                    .execute();
            Page page = Page.builder()
                    .site(site)
                    .path(path)
                    .code(response.statusCode())
                    .content(response.body())
                    .build();
            findLemmaAndIndexToIndexPage(site,page);
        } catch (IOException io){
            io.printStackTrace();
        }

      return new ResponseBoolean(true);
    }

    @Cacheable(value = "Search_Result",keyGenerator = "searchKeyGenerator")
    public ResponseBoolean systemSearch(String query, String siteUrl, Integer offset, Integer limit) {
        if (query.isBlank()) {
            return new ResponseEmptySearchQuery(false, "Пустой поисковый запрос");
        }
        try {
            Site site = siteRepository.findByUrl(siteUrl);
            LemmaFinder lemmaFinder = LemmaFinder.getInstance();
            Set<String> uniqueLemma = lemmaFinder.getLemmaSet(query);
            List<Lemma> filterLemma = calculatingLemmasOnPages(uniqueLemma, site);
            if(filterLemma.isEmpty()){
                return new ResponseSearch(true,0,List.of());
            }
            List<Page> pages = indexRepository.findPagesByLemma(filterLemma.get(0).getId());

            for (Lemma lemma : filterLemma) {
                List<Page> pageWithLemma = indexRepository.findPagesByLemma(lemma.getId());

                pages = pages.stream()
                        .filter(pageWithLemma::contains)
                        .toList();
            }

            List<PageRelevance> resultRelevance = calculatedRelevance(filterLemma);
            resultRelevance.sort(Comparator.comparing(PageRelevance::absoluteRelevance).reversed());

            List<ResultSearchRequest> resultSearchRequestList = createdRequest(resultRelevance, query);

            int totalResultSearchCount = resultSearchRequestList.size();
            List<ResultSearchRequest> paginationResult = resultSearchRequestList.stream()
                    .skip(offset)
                    .limit(limit)
                    .toList();
            return new ResponseSearch(true, totalResultSearchCount, paginationResult);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void findLemmaAndIndexToIndexPage(Site site,Page page) throws IOException {
        List<Index> indexList = new ArrayList<>();
        List<Lemma> lemmaList = new ArrayList<>();
        LemmaFinder lemmaFinder = LemmaFinder.getInstance();
        Map<String, Integer> currentLemmas = lemmaFinder.collectLemmas(page.getContent());
        currentLemmas.forEach((key, value) -> {
            Lemma lemma = lemmaRepository.findByLemmaToSiteId(key,site);
            if(lemma == null){
                lemma = Lemma.builder()
                        .site(site)
                        .lemma(key)
                        .frequency(1)
                        .build();
            }
            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaList.add(lemma);
            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank((float) lemma.getFrequency());
            indexList.add(index);
        });
        allInsert(site,Collections.singletonList(page),Pair.of(lemmaList,indexList));
    }

    private Pair<List<Lemma>, List<Index>> findLemmaToText(Site site, List<Page> pages) {
        log.info("Начат поиск лемм сайта: {}",site.getName());
        Map<String, Lemma> allLemmas = new ConcurrentHashMap<>();
        List<Index> indexList = new ArrayList<>();
        pages.parallelStream().forEach(page -> {
            try {
                LemmaFinder lemmaFinder = LemmaFinder.getInstance();
                Map<String, Integer> currentLemmas = lemmaFinder.collectLemmas(page.getContent());
                currentLemmas.forEach((key, value) -> {
                    Lemma lemma = allLemmas.computeIfAbsent(key, text -> {
                        Lemma l = new Lemma();
                        l.setSite(site);
                        l.setLemma(text);
                        l.setFrequency(1);
                        return l;
                    });

                    lemma.setFrequency(lemma.getFrequency() + 1);

                    Index index = new Index();
                    index.setPage(page);
                    index.setLemma(lemma);
                    index.setRank((float) lemma.getFrequency());
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

    private Optional<SiteConfig> checkPageToSiteConfig(String url) {
        for (SiteConfig siteConfig : sitesList.getSites()) {
            if (url.startsWith(siteConfig.getUrl())) {
                return Optional.of(siteConfig);
            }
        }
        return Optional.empty();
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

    @Async
    @Transactional
    protected void allInsert(Site site, List<Page> pages,Pair<List<Lemma>, List<Index>> lemmaAndIndex){
            String string = pool.isShutdown() ? String.format("Сохранение сайта %s с остановленной индексацией",site.getName())
                    : String.format("Сохранение проиндексированного сайта %s", site.getName());
            log.info(string);
            log.info("Сохранение сайта: {}" ,site.getName());
            siteRepository.save(site);
            log.info("Сохранение страниц: {}" , site.getName());
            pageRepository.saveAll(pages);
            log.info("Сохранение лемм: {}" , site.getName());
            lemmaRepository.saveAll(lemmaAndIndex.getLeft());
            log.info("Сохранение индексов страниц и лемм: {}", site.getName());
            batchIndexInsert(lemmaAndIndex.getRight());
            string = pool.isShutdown() ? String.format("Сохранение сайта %s с остановленной индексацией завершено",site.getName())
                    : String.format("Сохранение проиндексированного сайта %s завершено", site.getName());
            log.info(string);
    }

    private void batchIndexInsert(List<Index> indexList) {
        String sql = "INSERT INTO index (page_id,lemma_id,rank) VALUES (?,?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Index index = indexList.get(i);
                Hibernate.initialize(index.getPage());
                Hibernate.initialize(index.getLemma());
                ps.setObject(1, index.getPage().getId());
                ps.setObject(2, index.getLemma().getId());
                ps.setFloat(3, index.getRank());
            }

            @Override
            public int getBatchSize() {
                return indexList.size();
            }
        });
    }

    private List<PageRelevance> calculatedRelevance(List<Lemma> filterLemma) {

        List<PageRelevance> resultRelevance = new ArrayList<>();

        List<Integer> lemmaIds = filterLemma.stream()
                .mapToInt(Lemma::getId)
                .boxed()
                .toList();

        List<Index> indexList = indexRepository.findByLemmaIdIn(lemmaIds);

        Map<Page, Double> pageToRelevance = new HashMap<>();

        for (Index index : indexList) {
            Page page = index.getPage();
            double rank = index.getRank();

            pageToRelevance.put(page, pageToRelevance.getOrDefault(page, 0.0) + rank);
        }

        double maxAbsoluteRelevance = pageToRelevance.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.1);

        for (Map.Entry<Page, Double> entry : pageToRelevance.entrySet()) {
            Page page = entry.getKey();
            double absoluteRelevance = entry.getValue();
            double relativeRelevance = absoluteRelevance / maxAbsoluteRelevance;

            resultRelevance.add(new PageRelevance(page, absoluteRelevance, relativeRelevance));
        }

        return resultRelevance;
    }

    private List<Lemma> calculatingLemmasOnPages(Set<String> lemmas, Site site) {
        long totalPages = pageRepository.count();
        double threshold = 0.8;

        Set<Lemma> filterLemma = new TreeSet<>(Comparator.comparing(Lemma::getFrequency));

        for (String lemma1 : lemmas) {
            List<Lemma> lemmaList = site == null ? lemmaRepository.findByLemma(lemma1)
                    : Collections.singletonList(lemmaRepository.findByLemmaToSiteId(lemma1, site));
            for (Lemma currentLemma : lemmaList) {
                if (currentLemma != null) {
                    long countPageToLemma = indexRepository.countPageToLemma(currentLemma.getId());
                    if ((double) countPageToLemma / totalPages <= threshold || lemmas.size() < 4) {
                        filterLemma.add(currentLemma);
                    }
                }
            }
        }
        return new ArrayList<>(filterLemma);
    }

    private List<ResultSearchRequest> createdRequest(List<PageRelevance> pageRelevance, String query) {

        return pageRelevance.stream()
                .map(page -> {

                        String url = page.page().getSite().getUrl();
                        String nameUrl = page.page().getSite().getName();
                        String uri = page.page().getPath();

                        Document document = Jsoup.parse(page.page().getContent());
                        String title = document.title();

                        String snippet = SnippetGenerator.generatedSnippet(query, page.page().getContent());

                        return new ResultSearchRequest(url, nameUrl, uri, title, snippet, page.relativeRelevance());
                }).toList();
    }

    protected void proxiesSave(Site site, List<Page> pages,Pair<List<Lemma>, List<Index>> lemmaAndIndex){
        selfProxy.allInsert(site, pages, lemmaAndIndex);
    }

    protected void proxiesDelete(){
        selfProxy.deleteAllDataIndexingSites();
    }

}
