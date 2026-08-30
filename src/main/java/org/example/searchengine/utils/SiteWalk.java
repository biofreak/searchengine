package org.example.searchengine.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.example.searchengine.model.IndexError;
import org.example.searchengine.model.Page;

import java.net.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveTask;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SiteWalk extends RecursiveTask<Stream<String>> {
    private final Set<String> REFS;

    private final Page PAGE;

    private final String BASE_ADDRESS;

    public SiteWalk(Set<String> urlList, Page pageEntity, String baseAddress) {
        REFS = urlList;
        PAGE = pageEntity;
        this.BASE_ADDRESS = baseAddress.replaceAll("/$", "");
    }

    private String stripSlash(String link) { return link.replaceAll("([^/])/$", "$1"); }

    private Stream<String> getReferences(Document html) {
        try {
            return html.select("a[href]").stream()
                    .map(link -> link.attr("abs:href"))
                    .map(this::stripSlash)
                    .filter(link -> link.startsWith(BASE_ADDRESS))
                    .map(link ->    link.contains("?") ? link.substring(0, link.indexOf("?")) : link)
                    .map(link -> link.contains("#") ? link.substring(0, link.indexOf("#")) : link)
                    .filter(Predicate.not(String::isEmpty))
                    .map(subPath -> subPath.replaceAll("^/|/$", ""))
                    .filter(link -> !REFS.contains(link))
                    .map(x -> x.replace("\uFEFF", ""))
                    .map(String::strip)
                    .map(x -> x.replaceAll(" ", "%20"))
                    .distinct();
        } catch (RuntimeException e) {
            return Stream.of();
        }
    }

    @Override
    protected Stream<String> compute() {
        try {
            String path = PAGE.getPath();
            String address = BASE_ADDRESS + (path.equals("/") ? "" : path);
            return getReferences(Jsoup.parse(PAGE.getContent(), BASE_ADDRESS))
                    .filter(link -> !link.equals(address));
        } catch (CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }
}