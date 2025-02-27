package searchengine.dto.site;

import lombok.Getter;
import lombok.Setter;
import searchengine.model.Page;
import searchengine.model.Status;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter
public class SiteDTO {

    private Integer id;

    private Status status;

    private LocalDate statusTime;

    private String lastError;

    private String url;

    private String name;

    private List<Page> page;

    public SiteDTO(LocalDate statusTime) {
        this.statusTime = LocalDate.now();
    }
}
