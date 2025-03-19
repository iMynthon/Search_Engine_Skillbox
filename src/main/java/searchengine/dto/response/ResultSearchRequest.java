package searchengine.dto.response;

public record ResultSearchRequest(
        String site,

        String siteName,

        String uri,

        String title,

        String snippet,

        double relevance
) {

}
