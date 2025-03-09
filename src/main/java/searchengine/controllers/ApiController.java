package searchengine.controllers;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Site;
import searchengine.services.IndexingSiteService;
import searchengine.services.StatisticsService;
import searchengine.until.CustomResponse.ResponseBoolean;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    private final IndexingSiteService indexingSiteService;

    public ApiController(StatisticsService statisticsService, IndexingSiteService indexingSiteService) {
        this.statisticsService = statisticsService;
        this.indexingSiteService = indexingSiteService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }


    @GetMapping("/startIndexing")
    public ResponseEntity<ResponseBoolean> startIndexing(){
       return indexingSiteService.startIndexingSite();
    }


    @GetMapping("/stopIndexing")
    public ResponseEntity<ResponseBoolean> stopIndexing(){
       return indexingSiteService.stopIndexing();
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ResponseBoolean> delete(@PathVariable Integer id){
        return indexingSiteService.deleteSiteIndexing(id);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<ResponseBoolean> IndexPage(@RequestBody @NotBlank String url){
        return indexingSiteService.indexPage(url);
    }

    @GetMapping
    public Site site(@RequestParam Site site){
        return site;
    }
}
