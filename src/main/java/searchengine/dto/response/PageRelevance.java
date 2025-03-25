package searchengine.dto.response;

import searchengine.model.Page;

public record PageRelevance(

        Page page,

        Double absoluteRelevance,

        Double relativeRelevance
) {

}
