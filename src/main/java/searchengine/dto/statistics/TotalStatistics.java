package searchengine.dto.statistics;

import lombok.Data;

@Data
public class TotalStatistics {
    private Integer sites;
    private Integer pages;
    private Integer lemmas;
    private Boolean indexing;

    public Integer getPages() {
        return pages == null ? 0 : pages;
    }

    public Integer getLemmas() {
        return lemmas == null ? 0 : lemmas;
    }
}
