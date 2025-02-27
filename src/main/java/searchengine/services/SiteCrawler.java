package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Site;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class SiteCrawler extends RecursiveTask<Site> {

    private static String first_url;

    private final String another_url;

    private final List<String> CHECK_URL;

    public SiteCrawler(String url) {
        this(url, new CopyOnWriteArrayList<>());
        first_url = url;
    }

    public SiteCrawler(String url, List<String> checkUrl) {
        this.another_url = url;
        this.CHECK_URL = checkUrl;
    }

    @Override
    protected Site compute() {

        Site currentSite = new Site();
        currentSite.setUrl(another_url);

        synchronized (CHECK_URL) {
            if (CHECK_URL.contains(another_url)) {
                return currentSite;
            }
            CHECK_URL.add(another_url);
        }

        log.info("Indexing url: {}", another_url);

        List<SiteCrawler> mappingTask = new CopyOnWriteArrayList<>();

        try {
            Document document = Jsoup.connect(another_url)
                    .get();

            Elements elements = document.select("a");

            for (Element element : elements) {
                String abshref = element.attr("abs:href");
                if (isValidLink(abshref.trim())) {
                    SiteCrawler mappingSite = new SiteCrawler(abshref,CHECK_URL);

                    mappingSite.fork();

                    mappingTask.add(mappingSite);

                    Thread.sleep(500);
                }
            }
            for (SiteCrawler mappingSites : mappingTask) {
                Site site = mappingSites.join();
                currentSite.setPage(site.getPage());
            }

        } catch (Exception e) {
            log.info("Invalid url");
        }

        return currentSite;
    }

    public boolean isValidLink(String url) {
        synchronized (CHECK_URL) {
            return url.startsWith(first_url) && !url.contains("#") && !CHECK_URL.contains(url);
        }
    }
}
