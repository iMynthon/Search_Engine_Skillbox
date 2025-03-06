package searchengine.until;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.ConnectionSetting;
import searchengine.model.Page;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;

@Slf4j
public class SiteCrawler extends RecursiveTask<List<Page>> {

    private static String HEAD_URL;

    private final String another_url;

    private final Set<String> visitedUrls;

    private final ConnectionSetting setting;

    private final Random random = new Random();

    private static final Pattern FILE_PATTERN = Pattern
            .compile(".*\\.(jpg|jpeg|png|gif|bmp|pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|tar|gz|7z|mp3|wav|mp4|mkv|avi|mov|sql)$", Pattern.CASE_INSENSITIVE);


    public SiteCrawler(String url, ConnectionSetting setting) {
        this(url, ConcurrentHashMap.newKeySet(), setting);
        HEAD_URL = url;

    }

    public SiteCrawler(String url, Set<String> visitedUrls, ConnectionSetting connectionSetting) {
        this.another_url = url;
        this.visitedUrls = visitedUrls;
        this.setting = connectionSetting;
    }

    @Override
    protected List<Page> compute() {

        Page currentPage = new Page(another_url.substring(HEAD_URL.length()));

        List<Page> pages = new CopyOnWriteArrayList<>();

        if (visitedUrls.contains(another_url)) {
            return pages;
        }
        visitedUrls.add(another_url);

        log.info("Indexing url: {}", another_url);

        List<SiteCrawler> crawler = new CopyOnWriteArrayList<>();

        try {

            Connection.Response response = Jsoup.connect(another_url)
                    .userAgent(getRandomSetting().getUserAgent())
                    .referrer(getRandomSetting().getReferrer())
                    .timeout(5000)
                    .execute();

            currentPage.setCode(response.statusCode());
            currentPage.setContent(response.body());

            Document document = response.parse();
            Elements elements = document.select("a");

            pages.add(currentPage);

            for (Element element : elements) {
                String abshref = element.attr("abs:href");
                if (isValidLink(abshref.trim())) {
                    SiteCrawler siteCrawler = new SiteCrawler(abshref, visitedUrls, setting);
                    siteCrawler.fork();

                    crawler.add(siteCrawler);
                }
            }

            for (SiteCrawler siteCrawler : crawler) {
                List<Page> resultPages = siteCrawler.join();
                pages.addAll(resultPages);
            }

        } catch (Exception e) {
            log.info("Invalid url: {}", another_url);
            currentPage.setCode(500);
            currentPage.setContent(e.getMessage());
            pages.add(currentPage);
        }
        return pages;
    }

    public boolean isValidLink(String urls) {
        return urls.startsWith(HEAD_URL) && !urls.contains("#") && !visitedUrls.contains(urls)
                && !FILE_PATTERN.matcher(urls).matches();

    }

    public ConnectionSetting.Setting getRandomSetting(){
        List<ConnectionSetting.Setting> settings = setting.getSettings();
        return settings.get(random.nextInt(settings.size()));
    }
}
