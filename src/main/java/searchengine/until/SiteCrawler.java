package searchengine.until;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.ConnectionSetting;
import searchengine.model.Page;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;

@Slf4j
public class SiteCrawler extends RecursiveTask<List<Page>> {

    private final String HEAD_URL;

    private final String another_url;

    private final static Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    private final ConnectionSetting setting;

    private static final Pattern FILE_PATTERN = Pattern
            .compile(".*\\.(jpg|jpeg|png|gif|bmp|pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|tar|gz|7z|mp3|wav|mp4|mkv|avi|mov|sql|webp)$", Pattern.CASE_INSENSITIVE);


    public SiteCrawler(String site,String another_url, ConnectionSetting setting) {
        this.HEAD_URL = site;
        this.another_url = another_url;
        this.setting = setting;
    }

    @Override
    public List<Page> compute() {
        Page currentPage = new Page(another_url.substring(HEAD_URL.length()));

        List<Page> pages = new CopyOnWriteArrayList<>();

        if (visitedUrls.contains(another_url)) {
            return pages;
        }
        if(Thread.currentThread().isInterrupted() || getPool().isShutdown()){
            Thread.currentThread().interrupt();
            return pages;
        }
        visitedUrls.add(another_url);

        log.info("Индексация URL: {}", another_url);

        List<SiteCrawler> crawler = new CopyOnWriteArrayList<>();

        try {
            Connection.Response response = Jsoup.connect(another_url)
                    .userAgent(setting.getCurrentUserAgent())
                    .referrer(setting.getCurrentReferrer())
                    .timeout(5000)
                    .execute();

            currentPage.setCode(response.statusCode());
            currentPage.setContent(response.body());

            Document document = response.parse();
            Elements elements = document.select("a");

            pages.add(currentPage);

            for (Element element : elements) {
                if(Thread.currentThread().isInterrupted() || getPool().isShutdown()){
                    Thread.currentThread().interrupt();
                    return pages;
                }
                String abshref = element.attr("abs:href");
                if (isValidLink(abshref.trim())) {
                    SiteCrawler siteCrawler = new SiteCrawler(HEAD_URL,abshref,setting);
                    siteCrawler.fork();
                    crawler.add(siteCrawler);
                }
            }

            for (SiteCrawler siteCrawler : crawler) {
                if(Thread.currentThread().isInterrupted() || getPool().isShutdown()){
                    Thread.currentThread().interrupt();
                    return pages;
                }
                List<Page> resultPages = siteCrawler.join();
                pages.addAll(resultPages);
            }

        } catch (IOException e) {
            log.info("Недействительный URL: {}", another_url);
            currentPage.setCode(500);
            currentPage.setContent(e.getMessage().isEmpty() ? "Индексация остановлена пользователем" : e.getMessage());
            pages.add(currentPage);
            if(Thread.currentThread().isInterrupted() || getPool().isShutdown()){
                Thread.currentThread().interrupt();
                return pages;
            }

        }
        return pages;
    }

    public boolean isValidLink(String urls) {
        return urls.startsWith(HEAD_URL) && !urls.contains("#") && !visitedUrls.contains(urls)
                && !FILE_PATTERN.matcher(urls).matches();

    }
}
