package searchengine.dto.site;

import lombok.Getter;
import lombok.Setter;
import searchengine.model.Page;
import searchengine.model.Status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class SiteDTO {

    private Integer id;

    private Status status;

    private LocalDateTime statusTime;

    private String lastError;

    private String url;

    private String name;

    private List<PageDTO> page = new ArrayList<>();

}
