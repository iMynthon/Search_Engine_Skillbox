package searchengine.services;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;

@Slf4j
@ToString
public class SiteCrawler extends RecursiveTask<List<Page>> {

    private static String HEAD_URL;

    private final String another_url;

    private final List<String> visitedUrls;

    private static final Pattern FILE_PATTERN = Pattern
            .compile(".*\\.(jpg|jpeg|png|gif|bmp|pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|tar|gz|7z|mp3|wav|mp4|mkv|avi|mov|sql)$", Pattern.CASE_INSENSITIVE);


    public SiteCrawler(String url) {
        this(url, new CopyOnWriteArrayList<>());
        HEAD_URL = url;
    }

    public SiteCrawler(String url, List<String> visitedUrls) {
        this.another_url = url;
        this.visitedUrls = visitedUrls;
    }

    @Override
    protected List<Page> compute() {

        Page currentPage = new Page(another_url.substring(HEAD_URL.length()));
        List<Page> pages = new CopyOnWriteArrayList<>();

        synchronized (visitedUrls) {
            if (visitedUrls.contains(another_url)) {
                return pages;
            }
        }
        visitedUrls.add(another_url);


        log.info("Indexing url: {}", another_url);

        List<SiteCrawler> crawler = new CopyOnWriteArrayList<>();

        try {

            Connection.Response response = Jsoup.connect(another_url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .timeout(5000)
                    .followRedirects(true)
                    .execute();

            currentPage.setCode(response.statusCode());
            currentPage.setContent(response.body());

            Document document = response.parse();
            Elements elements = document.select("a");

            pages.add(currentPage);

            for (Element element : elements) {
                String abshref = element.attr("abs:href");
                synchronized (elements) {
                    if (isValidLink(abshref.trim())) {
                        SiteCrawler siteCrawler = new SiteCrawler(abshref, visitedUrls);
                        siteCrawler.fork();

                        crawler.add(siteCrawler);
                    }
                }
            }

            for (SiteCrawler siteCrawler : crawler) {
                List<Page> resultPages = siteCrawler.join();
                pages.addAll(resultPages);
            }

        } catch (Exception e) {
            log.info("Invalid url");
        }
        return pages;
    }

    public boolean isValidLink(String urls) {
        synchronized (this) {
            return urls.startsWith(HEAD_URL) && !urls.contains("#") && !visitedUrls.contains(urls)
                    && !FILE_PATTERN.matcher(urls).matches();
        }
    }
}
