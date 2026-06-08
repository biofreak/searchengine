package org.example.searchengine.services;

import jakarta.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.example.searchengine.dto.searching.SearchResult;
import org.example.searchengine.model.Index;
import org.example.searchengine.model.Lemma;
import org.example.searchengine.model.Page;
import org.example.searchengine.model.Site;
import org.example.searchengine.repositories.LemmaRepository;
import org.example.searchengine.repositories.SiteRepository;
import org.example.searchengine.repositories.PageRepository;
import org.example.searchengine.repositories.IndexRepository;

import org.example.searchengine.utils.SplitToLemmas;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;

    private final Optional<SplitToLemmas> splitterRus = Optional.ofNullable(SplitToLemmas.getInstanceRus());
    private final Optional<SplitToLemmas> splitterEng = Optional.ofNullable(SplitToLemmas.getInstanceEng());

    private final double MENTION_COEFFICIENT = 0.7;
    private final String EMPTY_REQUEST_ERROR = "Задан пустой поисковый запрос";

    @Cacheable(value = "results", key = "{ #query, #site }")
    public List<SearchResult> startSearch(String query, String site) {
        if (!query.isEmpty()) {
            var siteList = site==null ? siteRepository.findAll() : List.of(siteRepository.findByUrl(site).orElseThrow());
            Map<String, List<Lemma>> lemmas = splitToLemmas(query).keySet().stream()
                    .map(lemma -> Map.entry(lemma, lemmaRepository.findBySiteInAndLemma(siteList, lemma)))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return getResults(lemmas, indexRepository.getPagesFromIndexIn(arrangeLemmas(siteList, lemmas)));
        }
        throw new BadRequestException(EMPTY_REQUEST_ERROR);
    }

    private String getSnippetFromContent(String htmlCode, Set<String> lemmaSet) {
        return Jsoup.parse(htmlCode).getAllElements().stream().map(Element::ownText)
                .filter(Predicate.not(String::isEmpty))
                .flatMap(string -> Arrays.stream(string.split("([.!?](\\s+|\\z)|\\z)")))
                .map(string -> {
                    List<String> boldList = Arrays.stream(string.replaceAll("[^а-яА-Яa-zA-Z']", " ")
                                    .split("(\\s+|$)"))
                            .filter(Predicate.not(String::isEmpty))
                            .filter(word -> word.matches("[а-яА-Яa-zA-Z]{2,}"))
                            .filter(word -> lemmaSet.stream().anyMatch(lemma ->
                                    getNormalForms(word).anyMatch(lemma::equals))).toList();

                    String bolded = string;
                    for (String word : boldList) {
                        bolded = bolded.replaceAll(word, "<b>" + word + "</b>");
                    }
                    return Map.entry(boldList.size(), bolded);
                }).filter(entry -> entry.getKey() > 0)
                .limit(3).map(Map.Entry::getValue).collect(Collectors.joining("<br />"));
    }

    private Set<String> arrangeLemmas(List<Site> siteList, Map<String, List<Lemma>> lemmas) {
        return lemmas.keySet().stream()
                .map(lemma -> Map.entry(lemma, lemmas.get(lemma).stream()
                        .mapToInt(x -> x == null ? 0 : x.getFrequency()).sum()))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByValue())
                .filter(entry -> {
                    int pageTotal = pageRepository.countAllBySiteIn(siteList);
                    return pageTotal > 0; // && (double) (entry.getValue() / pageTotal) > MENTION_COEFFICIENT;
                }).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private List<SearchResult> getResults(Map<String, List<Lemma>> lemmas, List<Page> pageList) {
        Map<Page, Double> rankMap = pageList.stream().map(pageEntity -> Map.entry(pageEntity,
                                        indexRepository.findByPageInAndLemmaIn(List.of(pageEntity),
                                                        lemmas.values().stream().flatMap(Collection::stream).toList())
                                                .stream().mapToDouble(Index::getRank).sum()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        double maxRank = rankMap.values().stream().mapToDouble(v -> v).max().orElse(0.0);

        return pageList.stream().sorted(Comparator
                        .comparingDouble(pageEntity -> maxRank > 0.0 ? rankMap.get(pageEntity) / maxRank : 0.0)
                        .reversed()).map(pageEntity -> {
                    SearchResult result = new SearchResult();
                    Site siteEntity = pageEntity.getSite();
                    String htmlCode = pageEntity.getContent();

                    result.setSite(siteEntity.getUrl());
                    result.setSiteName(siteEntity.getName());
                    result.setUri(pageEntity.getPath());
                    result.setTitle(Jsoup.parse(htmlCode).getElementsByTag("title").text());
                    result.setSnippet(getSnippetFromContent(htmlCode, lemmas.keySet()));
                    result.setRelevance(maxRank > 0.0 ? rankMap.get(pageEntity) / maxRank : 0.0);
                    return result;
                }).toList();
    }

    private Stream<String> getNormalForms(String word) {
        try {
            return splitterEng.map(x -> x.getNormalForms(word.toLowerCase()).stream()).orElseThrow();
        } catch (RuntimeException eng) {
            try {
                return splitterRus.map(x -> x.getNormalForms(word.toLowerCase()).stream()).orElseThrow();
            } catch (RuntimeException rus) {
                return Stream.of();
            }
        }
    }

    private Map<String, Long> splitToLemmas(String text) {
        try {
            return Stream.concat(splitterEng.map(x -> x.splitTextToLemmas(text).entrySet().stream()).orElseThrow(),
                            splitterRus.map(x -> x.splitTextToLemmas(text).entrySet().stream()).orElseThrow())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
