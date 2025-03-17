package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;
import searchengine.model.Page;

@Getter @Setter
public class PageRelevance {

    private Page page;

    private double absoluteRelevance;

    private double relativeRelevance;

    public PageRelevance(Page page, double absoluteRelevance) {
        this.page = page;
        this.absoluteRelevance = absoluteRelevance;
    }
}
