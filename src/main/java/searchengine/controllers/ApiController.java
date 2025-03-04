package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    @PostMapping("/indexPage")
    public Page addPage(@RequestParam Page page){
        return page;
    }

    @GetMapping
    public Site site(@RequestParam Site site){
        return site;
    }
}
