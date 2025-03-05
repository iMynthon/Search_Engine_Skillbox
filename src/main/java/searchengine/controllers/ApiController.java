package searchengine.controllers;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.site.SiteDTO;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;
import searchengine.until.ResponseFormat;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    private final IndexingService indexingService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }


    @GetMapping("/startIndexing")
    public ResponseEntity<ResponseFormat> startIndexing(){
       return indexingService.startIndexingSite();
    }


    @GetMapping("/stopIndexing")
    public ResponseEntity<ResponseFormat> stopIndexing(){
       return indexingService.stopIndexing();
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ResponseFormat> delete(@PathVariable Integer id){
        return indexingService.deleteSiteIndexing(id);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<ResponseFormat> IndexPage(@RequestBody @NotBlank String url){
        return indexingService.indexPage(url);
    }

    @GetMapping
    public Site site(@RequestParam Site site){
        return site;
    }
}
