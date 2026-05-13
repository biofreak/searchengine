package org.example.searchengine.services;

import org.example.searchengine.dto.indexing.IndexingResponse;

public interface IndexingService {
    IndexingResponse fullIndex();
    IndexingResponse stopIndex();
    IndexingResponse addIndex(String link);
}
