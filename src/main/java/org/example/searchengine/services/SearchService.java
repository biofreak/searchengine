package org.example.searchengine.services;

import org.example.searchengine.dto.searching.SearchResult;

import java.util.List;

public interface SearchService {
    List<SearchResult> startSearch(String query, String site);
}
