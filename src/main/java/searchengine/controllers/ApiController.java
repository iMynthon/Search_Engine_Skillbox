package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }


    @GetMapping("/startIndexing")
    public Boolean startIndexing(){
        return true;
    }

    @GetMapping("/stopIndexing")
    public Boolean stopIndexing(){
        return true;
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
