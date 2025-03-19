package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.ConnectionSetting;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.response.*;
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
import searchengine.until.LemmaFinder;
import searchengine.until.SiteCrawler;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final JdbcTemplate jdbcTemplate;
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
                    batchIndexInsert(lemmaAndIndex.getRight());

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

    @Transactional
    public ResponseBoolean deleteSiteIndexing(Integer id) {
        if (!siteRepository.existsById(id)) {
            return new ResponseError(new ResourcesNotFoundException("По вашему запросу ничего не найдено, " +
                    "данный сайт не был проиндексирован"));
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

                siteRepository.save(site);

                pageRepository.saveAll(pages);
                lemmaRepository.saveAll(lemmaAndIndex.getLeft());
                indexRepository.saveAll(lemmaAndIndex.getRight());

            } catch (Exception e) {
                log.error(e.getMessage());
                site.setLastError(e.getMessage());
                siteRepository.save(site);
            }
        });

        return CompletableFuture.completedFuture(new ResponseBoolean(true));
    }

    public ResponseBoolean systemSearch(String query, String siteUrl, Integer offset, Integer limit) {
        if (query.isBlank()) {
            return new ResponseEmptySearchQuery(false, "Пустой поисковый запрос");
        }
        try {
            Site site = siteRepository.findByUrl(siteUrl);
            LemmaFinder lemmaFinder = LemmaFinder.getInstance();
            Set<String> uniqueLemma = lemmaFinder.getLemmaSet(query);
            List<Lemma> filterLemma = calculatingLemmasOnPages(uniqueLemma, site);

            List<Page> pages = indexRepository.findPagesByLemma(filterLemma.get(0).getId()).orElseThrow(
                    () -> new ResourcesNotFoundException("Страниц с вашим поисковым запросом не найдено"));

            for (Lemma lemma : filterLemma) {
                Optional<List<Page>> pageWithLemma = indexRepository.findPagesByLemma(lemma.getId());

                pages = pages.stream()
                        .filter(pageWithLemma.get()::contains)
                        .toList();
            }

            List<PageRelevance> resultRelevance = calculatedRelevance(filterLemma);
            resultRelevance.sort(Comparator.comparing(PageRelevance::getAbsoluteRelevance).reversed());

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

    public List<ResultSearchRequest> createdRequest(List<PageRelevance> pageRelevance, String query) {
        return pageRelevance.stream()
                .map(page -> {
                    String url = page.getPage().getSite().getUrl();
                    String nameUrl = page.getPage().getSite().getName();
                    String uri = page.getPage().getPath();

                    Document document = Jsoup.parse(page.getPage().getContent());
                    String title = document.title();

                    String snippet = generatedSnippet(query, page.getPage().getContent());

                    return new ResultSearchRequest(url, nameUrl, uri, title, snippet, page.getRelativeRelevance());
                }).toList();
    }


    public String generatedSnippet(String query, String content) {

        String text = Jsoup.parse(content).text();

        String[] words = query.split("\\s+");

        int index = findFirstOccurrenceIndex(text, words);

        if (index == -1) {
            return text.length() > 100 ? text.substring(0, 100) + " " : text;
        }

        int snippetLength = query.length() + 100;
        int start = Math.max(0, index - 50);
        int end = Math.min(text.length(), start + snippetLength);

        return highlightWords(text.substring(start, end), words);
    }

    private int findFirstOccurrenceIndex(String text, String[] words) {

        String regex = String.join("|", words);
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        return matcher.find() ? matcher.start() : -1;
    }

    private String highlightWords(String text, String[] words) {

        String regex = "(" + String.join("|", words) + ")";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, "<b>" + matcher.group() + "</b>");
        }
        matcher.appendTail(result);

        return result.toString();
    }


    public List<PageRelevance> calculatedRelevance(List<Lemma> filterLemma) {

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

    public List<Lemma> calculatingLemmasOnPages(Set<String> lemmas, Site site) {
        long totalPages = pageRepository.count();
        double threshold = 0.6;

        Set<Lemma> filterLemma = new TreeSet<>(Comparator.comparing(Lemma::getFrequency));

        for (String lemma1 : lemmas) {
            List<Lemma> lemmaList = site == null ? lemmaRepository.findByLemma(lemma1)
                    : Collections.singletonList(lemmaRepository.findByLemmaToSiteId(lemma1, site));
            for (Lemma currentLemma : lemmaList) {
                if (currentLemma != null) {
                    long countPageToLemma = indexRepository.countPageToLemma(currentLemma.getId());
                    if ((double) countPageToLemma / totalPages <= threshold) {
                        filterLemma.add(currentLemma);
                    }
                }
            }
        }
        return new ArrayList<>(filterLemma);
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

    private Site initSite(SiteConfig siteConfig) {
        Site site = new Site();
        site.setUrl(siteConfig.getUrl());
        site.setName(siteConfig.getName());
        site.setStatusTime(LocalDateTime.now());
        site.setLastError("");
        site.setStatus(INDEXING);
        return site;
    }

    private void batchIndexInsert(List<Index> indexList) {
        String sql = "INSERT INTO index (page_id,lemma_id,rank) VALUES (?,?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Index index = indexList.get(i);
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

}
