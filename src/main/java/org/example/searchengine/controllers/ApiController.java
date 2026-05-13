package org.example.searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.searchengine.dto.indexing.IndexingResponse;
import org.example.searchengine.dto.searching.SearchResponse;
import org.example.searchengine.dto.searching.SearchResult;
import org.example.searchengine.dto.statistics.StatisticsResponse;
import org.example.searchengine.services.IndexingService;
import org.example.searchengine.services.SearchService;
import org.example.searchengine.services.StatisticsService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;


    public ApiController(StatisticsService statisticsService,
                         IndexingService indexingService,
                         SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    @ResponseBody
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    @ResponseBody
    public ResponseEntity<IndexingResponse> startIndexing() {
        return ResponseEntity.ok(indexingService.fullIndex());
    }

    @GetMapping("/stopIndexing")
    @ResponseBody
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndex());
    }

    @PostMapping("/indexPage")
    @ResponseBody
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam(name = "url") String link) {
        return ResponseEntity.ok(indexingService.addIndex(link));
    }

    @GetMapping("/search")
    @ResponseBody
    public SearchResponse search(@RequestParam(name = "query") String query,
                                        @RequestParam(name = "site", required = false) String site,
                                        @RequestParam(name = "offset", defaultValue = "0") Integer offset,
                                        @RequestParam(name = "limit", defaultValue = "20") Integer limit) {
        List<SearchResult> data = searchService.startSearch(query, site);
        return SearchResponse.builder().result(true).count(data.size())
                .data(data.stream().skip(offset).limit(limit).toList()).build();
    }
}
