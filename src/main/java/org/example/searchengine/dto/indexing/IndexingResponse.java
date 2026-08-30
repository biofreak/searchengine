package org.example.searchengine.dto.indexing;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IndexingResponse {
    private Boolean result;
    private String error;
}
