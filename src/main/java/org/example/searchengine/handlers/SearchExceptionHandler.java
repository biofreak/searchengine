package org.example.searchengine.handlers;

import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.ws.rs.ForbiddenException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.example.searchengine.dto.searching.SearchResponse;

@RestControllerAdvice
public class SearchExceptionHandler {
    @ExceptionHandler(value = ForbiddenException.class)
    public ResponseEntity<SearchResponse> refreshTokenExceptionHandler(ForbiddenException ex, WebRequest webRequest) {
        return buildResponse(HttpStatus.FORBIDDEN, ex, webRequest);
    }

    @ExceptionHandler(value = BadRequestException.class)
    public ResponseEntity<SearchResponse> notValidHandler(BadRequestException ex, WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex, request);
    }

    private ResponseEntity<SearchResponse> buildResponse(HttpStatus httpStatus, Exception ex, WebRequest webRequest) {
        return ResponseEntity.status(httpStatus)
                .body(SearchResponse.builder()
                        .result(false)
                        .error(ex.getMessage())
                        .build());
    }
}
