package searchengine.controllers;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Site;
import searchengine.services.IndexingSiteService;
import searchengine.dto.response.ResponseBoolean;
import searchengine.services.StatisticsServiceImpl;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsServiceImpl statisticsService;

    private final IndexingSiteService indexingSiteService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/startIndexing")
    public CompletableFuture<ResponseBoolean> startIndexing(){
       return indexingSiteService.startIndexingSite();
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/stopIndexing")
    public CompletableFuture<ResponseBoolean> stopIndexing(){
       return indexingSiteService.stopIndexing();
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ResponseBoolean> delete(@PathVariable Integer id){
        return indexingSiteService.deleteSiteIndexing(id);
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/indexPage")
    public CompletableFuture<ResponseBoolean> IndexPage(@RequestBody @NotBlank String url){
        return indexingSiteService.indexPage(url);
    }

    @GetMapping
    public Site site(@RequestParam Site site){
        return site;
    }
}
