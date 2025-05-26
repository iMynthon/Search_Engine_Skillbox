package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.app.SiteConfig;
import searchengine.config.app.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl {

    private final SitesList sites;

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;

    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteConfig> sitesList = sites.getSites();
        for(SiteConfig siteConfig : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(siteConfig.getName());
            item.setUrl(siteConfig.getUrl());
            Site site = siteRepository.findByUrl(siteConfig.getUrl());
            int pages = site == null ? 0 : pageRepository.countPagesToSite(site.getId());
            int lemmas = site == null ? 0 : lemmaRepository.countLemmaToSite(site.getId());
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(site == null ? "Сайт не проиндексирован" : site.getStatus().toString());
            item.setError(site == null ? "" : site.getLastError());
            item.setStatusTime(site == null ? 0 : site.getStatusTime().getLong(ChronoField.MILLI_OF_SECOND));
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
