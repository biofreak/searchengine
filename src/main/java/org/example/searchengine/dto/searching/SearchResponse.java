package org.example.searchengine.dto.searching;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchResponse {
    private Boolean result;
    private Integer count;
    private List<SearchResult> data;
    private String error;
}
