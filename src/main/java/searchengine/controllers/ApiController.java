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

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
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

    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping("/delete/{id}")
    public ResponseBoolean delete(@PathVariable Integer id){
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
