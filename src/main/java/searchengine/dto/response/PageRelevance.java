package searchengine.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import searchengine.model.Page;

@Getter @Setter
@AllArgsConstructor
public class PageRelevance {

    private Page page;

    private double absoluteRelevance;

    private double relativeRelevance;

}
