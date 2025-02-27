package searchengine.dto.site;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import searchengine.model.Site;

@Getter @Setter
@NoArgsConstructor
public class PageDTO {

    private Integer id;

    private Site site;

    private String path;

    private Integer code;

    private String content;
}
