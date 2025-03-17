package searchengine.dto.search;

public record ResponseSearch(
        String siteUrl,

        String nameUrl,

        String uri,

        String title,

        String snippet,

        double relevance
) {

}
