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
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;

@Slf4j
public class SiteCrawler extends RecursiveTask<List<Page>> {

    private static String HEAD_URL;

    private String another_url;

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

    public SiteCrawler(String prefixUrl,String another_url,ConnectionSetting setting){
        HEAD_URL = startWithPrefixUrl(prefixUrl);
        this.another_url = another_url;
        this.setting = setting;
        this.visitedUrls = ConcurrentHashMap.newKeySet();
    }

    @Override
    public List<Page> compute() {

        Page currentPage = new Page(another_url.substring(HEAD_URL.length()));

        List<Page> pages = new CopyOnWriteArrayList<>();

        if (visitedUrls.contains(another_url)) {
            return pages;
        }
        visitedUrls.add(another_url);

        log.info("Индексация URL: {}", another_url);

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

                if(Thread.interrupted()){
                    Thread.currentThread().interrupt();
                    return pages;
                }

                String abshref = element.attr("abs:href");
                if (isValidLink(abshref.trim())) {
                    SiteCrawler siteCrawler = new SiteCrawler(abshref, visitedUrls, setting);
                    siteCrawler.fork();

                    crawler.add(siteCrawler);
                }
            }

            for (SiteCrawler siteCrawler : crawler) {
                if(Thread.interrupted()){
                    Thread.currentThread().interrupt();
                    return pages;
                }
                List<Page> resultPages = siteCrawler.join();
                pages.addAll(resultPages);
            }

        } catch (IOException e) {
            log.info("Недействительный URL: {}", another_url);
            currentPage.setCode(500);
            currentPage.setContent(e.getMessage().isEmpty() ? "Индексация остановлена пользователей" : e.getMessage());
            pages.add(currentPage);
            if(Thread.interrupted()){
                Thread.currentThread().interrupt();
            }
        }
        return pages;
    }

    public boolean isValidLink(String urls) {
        if(Thread.interrupted()){
            Thread.currentThread().interrupt();
            return false;
        }
        return urls.startsWith(HEAD_URL) && !urls.contains("#") && !visitedUrls.contains(urls)
                && !FILE_PATTERN.matcher(urls).matches();

    }

    public String startWithPrefixUrl(String url){
        return HEAD_URL.substring(0,url.length());
    }

    public ConnectionSetting.Setting getRandomSetting(){
        List<ConnectionSetting.Setting> settings = setting.getSettings();
        return settings.get(random.nextInt(settings.size()));
    }
}
